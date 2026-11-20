package com.example.engine

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.model.SelectedFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import kotlin.math.abs

sealed interface DownloadsSaveResult {
    data class SavedToMediaStore(val uri: Uri, val displayName: String) : DownloadsSaveResult
    data class RequiresSafDocument(val sourceFile: File, val mimeType: String) : DownloadsSaveResult
    data class Failure(val message: String) : DownloadsSaveResult
}

object FileHelper {

    fun getConvertedOutputDir(context: Context): File =
        File(context.filesDir, "converted").apply { if (!exists()) mkdirs() }

    fun getTempWorkDir(context: Context): File =
        File(context.cacheDir, "temp").apply { if (!exists()) mkdirs() }

    fun uniqueOutputFile(dir: File, rawBaseName: String, extension: String): File {
        val cleanBase = rawBaseName
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
            .ifBlank { "fileflow_output" }
        val ext = extension.trimStart('.').lowercase()
        var candidate = File(dir, "$cleanBase.$ext")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(dir, "${cleanBase}_$counter.$ext")
            counter++
        }
        return candidate
    }

    fun sanitizeBaseName(fileName: String): String {
        val base = fileName.substringBeforeLast('.', fileName)
        return base.replace(Regex("[^a-zA-Z0-9._-]"), "_").trim('_').ifBlank { "file" }
    }

    suspend fun resolveSelectedFile(context: Context, uri: Uri): SelectedFileItem =
        withContext(Dispatchers.IO) {
            val cr = context.contentResolver
            var displayName = "document_${System.currentTimeMillis()}"
            var sizeBytes = 0L

            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) {
                            cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                        }
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            sizeBytes = cursor.getLong(sizeIdx)
                        }
                    }
                }

            val detectedMime = cr.getType(uri) ?: run {
                val ext = displayName.substringAfterLast('.', "").lowercase()
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }

            val extension = displayName.substringAfterLast('.', "").lowercase().ifBlank {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(detectedMime)?.lowercase().orEmpty()
            }

            if (sizeBytes <= 0L) {
                runCatching {
                    cr.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        if (afd.length > 0) sizeBytes = afd.length
                    }
                }
            }

            val pageCount = if (extension == "pdf" || detectedMime == "application/pdf") {
                runCatching {
                    cr.openFileDescriptor(uri, "r")?.use { pfd ->
                        PdfRenderer(pfd).use { it.pageCount }
                    }
                }.getOrNull()
            } else {
                null
            }

            SelectedFileItem(
                uri = uri,
                displayName = displayName,
                sizeBytes = sizeBytes,
                mimeType = detectedMime,
                extension = extension,
                pageCount = pageCount
            )
        }

    fun copyUriToTempFile(context: Context, uri: Uri, preferredName: String): File {
        val tempDir = getTempWorkDir(context)
        val ext = preferredName.substringAfterLast('.', "tmp").lowercase()
        val tempFile = File.createTempFile("ff_in_", ".$ext", tempDir)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open input stream for $preferredName")
        input.use { inStream ->
            FileOutputStream(tempFile).use { outStream ->
                inStream.copyTo(outStream, bufferSize = 32 * 1024)
            }
        }
        return tempFile
    }

    fun writeFileToUri(context: Context, sourceFile: File, targetUri: Uri) {
        val out = context.contentResolver.openOutputStream(targetUri)
            ?: throw IllegalStateException("Unable to open destination output stream.")
        FileInputStream(sourceFile).use { input ->
            out.use { output ->
                input.copyTo(output, bufferSize = 32 * 1024)
            }
        }
    }

    suspend fun saveToDownloads(
        context: Context,
        sourceFile: File,
        mimeType: String
    ): DownloadsSaveResult = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) {
            return@withContext DownloadsSaveResult.Failure("Converted file no longer exists.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/FileFlow")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext DownloadsSaveResult.Failure("Could not create Downloads entry.")

                resolver.openOutputStream(itemUri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out, bufferSize = 32 * 1024)
                    }
                } ?: return@withContext DownloadsSaveResult.Failure("Failed writing to Downloads.")

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)

                DownloadsSaveResult.SavedToMediaStore(itemUri, sourceFile.name)
            } catch (e: Exception) {
                DownloadsSaveResult.Failure(e.localizedMessage ?: "Failed saving to Downloads.")
            }
        } else {
            DownloadsSaveResult.RequiresSafDocument(sourceFile, mimeType)
        }
    }

    fun getFileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareFile(context: Context, file: File, mimeType: String): Boolean {
        if (!file.exists()) return false
        return try {
            val uri = getFileProviderUri(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share ${file.name}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun shareMultipleFiles(context: Context, filesWithMimes: List<Pair<File, String>>): Boolean {
        val valid = filesWithMimes.filter { it.first.exists() }
        if (valid.isEmpty()) return false
        if (valid.size == 1) return shareFile(context, valid.first().first, valid.first().second)

        return try {
            val uris = ArrayList(valid.map { getFileProviderUri(context, it.first) })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, "Share ${valid.size} converted files").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun openFile(context: Context, file: File, mimeType: String): Boolean {
        if (!file.exists()) return false
        return try {
            val uri = getFileProviderUri(context, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unitIdx = 0
        while (value >= 1024.0 && unitIdx < units.lastIndex) {
            value /= 1024.0
            unitIdx++
        }
        val df = DecimalFormat(if (unitIdx == 0) "#,##0" else "#,##0.0")
        return "${df.format(value)} ${units[unitIdx]}"
    }

    fun formatSavingsLabel(sourceBytes: Long, outputBytes: Long): String {
        if (sourceBytes <= 0L || outputBytes <= 0L) return ""
        val delta = sourceBytes - outputBytes
        val pct = (abs(delta).toDouble() / sourceBytes.toDouble()) * 100.0
        val pctRounded = pct.toInt()
        return when {
            delta > 0 && pctRounded > 0 -> "-$pctRounded% smaller"
            delta < 0 && pctRounded > 0 -> "+$pctRounded% larger"
            else -> "Same size"
        }
    }

    fun guessMimeTypeFromExtension(ext: String): String {
        return when (ext.lowercase().trimStart('.')) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "md" -> "text/markdown"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "zip" -> "application/zip"
            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                ?: "application/octet-stream"
        }
    }
}
