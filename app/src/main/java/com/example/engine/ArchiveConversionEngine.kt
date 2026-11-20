package com.example.engine

import android.content.Context
import com.example.model.ConversionOutput
import com.example.model.SelectedFileItem
import com.example.model.ToolId
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

class ArchiveConversionEngine(private val context: Context) {

    suspend fun convert(
        toolId: ToolId,
        files: List<SelectedFileItem>,
        outputDir: File,
        onProgress: suspend (progress: Float, index: Int, message: String) -> Unit
    ): List<ConversionOutput> {
        return when (toolId) {
            ToolId.CREATE_ZIP -> listOf(createZip(files, outputDir, onProgress))
            ToolId.EXTRACT_ZIP -> extractZip(files.first(), outputDir, onProgress)
            ToolId.FILE_CHECKSUM -> computeChecksums(files, outputDir, onProgress)
            else -> throw IllegalArgumentException("Unsupported archive tool: $toolId")
        }
    }

    suspend fun createZipFromFiles(
        files: List<File>,
        baseName: String,
        outputDir: File
    ): ConversionOutput {
        val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, "zip")
        var totalSource = 0L
        ZipOutputStream(FileOutputStream(outFile).buffered()).use { zos ->
            val usedNames = mutableSetOf<String>()
            files.filter { it.exists() }.forEach { file ->
                totalSource += file.length()
                var entryName = file.name
                var counter = 1
                while (!usedNames.add(entryName)) {
                    val stem = entryName.substringBeforeLast('.', entryName)
                    val ext = entryName.substringAfterLast('.', "")
                    entryName = if (ext.isNotEmpty()) "${stem}_$counter.$ext" else "${stem}_$counter"
                    counter++
                }
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(zos, bufferSize = 32 * 1024) }
                zos.closeEntry()
            }
        }
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "application/zip",
            sizeBytes = outFile.length(),
            sourceSizeBytes = totalSource,
            extraNote = "${files.size} file(s) archived"
        )
    }

    private suspend fun createZip(
        files: List<SelectedFileItem>,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): ConversionOutput {
        val firstBase = FileHelper.sanitizeBaseName(files.first().displayName)
        val outFile = FileHelper.uniqueOutputFile(outputDir, "${firstBase}_archive", "zip")
        var totalSourceBytes = 0L

        ZipOutputStream(FileOutputStream(outFile).buffered()).use { zos ->
            val usedNames = mutableSetOf<String>()
            files.forEachIndexed { idx, item ->
                coroutineContext.ensureActive()
                onProgress(idx.toFloat() / files.size.toFloat(), idx + 1, "Compressing ${item.displayName}…")
                totalSourceBytes += item.sizeBytes

                var entryName = item.displayName.replace("/", "_").ifBlank { "file_$idx" }
                var counter = 1
                while (!usedNames.add(entryName)) {
                    val stem = item.displayName.substringBeforeLast('.', item.displayName)
                    val ext = item.displayName.substringAfterLast('.', "")
                    entryName = if (ext.isNotEmpty()) "${stem}_$counter.$ext" else "${stem}_$counter"
                    counter++
                }

                zos.putNextEntry(ZipEntry(entryName))
                val input = context.contentResolver.openInputStream(item.uri)
                    ?: throw IllegalStateException("Cannot read ${item.displayName}")
                input.use { it.copyTo(zos, bufferSize = 32 * 1024) }
                zos.closeEntry()
            }
        }

        onProgress(1f, files.size, "ZIP archive created")
        return ConversionOutput(
            outputFilePath = outFile.absolutePath,
            fileName = outFile.name,
            mimeType = "application/zip",
            sizeBytes = outFile.length(),
            sourceSizeBytes = totalSourceBytes,
            extraNote = "${files.size} file(s) zipped"
        )
    }

    private suspend fun extractZip(
        zipItem: SelectedFileItem,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val outputs = mutableListOf<ConversionOutput>()
        val canonicalOutRoot = outputDir.canonicalFile
        val input = context.contentResolver.openInputStream(zipItem.uri)
            ?: throw IllegalStateException("Cannot open ${zipItem.displayName}")

        onProgress(0.2f, 1, "Inspecting ${zipItem.displayName}…")
        input.use { rawStream ->
            ZipInputStream(rawStream.buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                var index = 0
                while (entry != null) {
                    coroutineContext.ensureActive()
                    if (!entry.isDirectory) {
                        val safeFileName = File(entry.name).name.ifBlank { "extracted_$index" }
                        val stem = safeFileName.substringBeforeLast('.', safeFileName)
                        val ext = safeFileName.substringAfterLast('.', "bin")
                        val targetFile = FileHelper.uniqueOutputFile(canonicalOutRoot, stem, ext)

                        // Zip-Slip guard
                        if (!targetFile.canonicalPath.startsWith(canonicalOutRoot.path + File.separator)) {
                            throw SecurityException("Blocked unsafe ZIP path: ${entry.name}")
                        }

                        FileOutputStream(targetFile).use { fos ->
                            zis.copyTo(fos, bufferSize = 32 * 1024)
                        }
                        val mime = FileHelper.guessMimeTypeFromExtension(ext)
                        outputs += ConversionOutput(
                            outputFilePath = targetFile.absolutePath,
                            fileName = targetFile.name,
                            mimeType = mime,
                            sizeBytes = targetFile.length(),
                            sourceSizeBytes = zipItem.sizeBytes,
                            extraNote = "Extracted from ${zipItem.displayName}"
                        )
                        index++
                        onProgress((0.2f + (index % 10) * 0.07f).coerceAtMost(0.95f), 1, "Extracted $safeFileName")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (outputs.isEmpty()) {
            throw IllegalArgumentException("ZIP archive contained no extractable files.")
        }
        onProgress(1f, 1, "Extracted ${outputs.size} file(s)")
        return outputs
    }

    private suspend fun computeChecksums(
        files: List<SelectedFileItem>,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val report = StringBuilder()
        var totalSize = 0L
        report.appendLine("FileFlow Cryptographic Checksum Report")
        report.appendLine("======================================")
        report.appendLine()

        files.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / files.size.toFloat(), idx + 1, "Hashing ${item.displayName}…")
            totalSize += item.sizeBytes

            val sha256 = MessageDigest.getInstance("SHA-256")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val md5 = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(32 * 1024)
            var bytesReadTotal = 0L

            val input = context.contentResolver.openInputStream(item.uri)
                ?: throw IllegalStateException("Cannot read ${item.displayName}")
            input.use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    sha256.update(buffer, 0, read)
                    sha1.update(buffer, 0, read)
                    md5.update(buffer, 0, read)
                    bytesReadTotal += read
                }
            }

            report.appendLine("File: ${item.displayName}")
            report.appendLine("Size: ${FileHelper.formatFileSize(bytesReadTotal)} ($bytesReadTotal bytes)")
            report.appendLine("SHA-256: ${sha256.digest().toHex()}")
            report.appendLine("SHA-1:   ${sha1.digest().toHex()}")
            report.appendLine("MD5:     ${md5.digest().toHex()}")
            report.appendLine()
        }

        val firstBase = FileHelper.sanitizeBaseName(files.first().displayName)
        val outFile = FileHelper.uniqueOutputFile(outputDir, "${firstBase}_checksums", "txt")
        outFile.writeText(report.toString())
        onProgress(1f, files.size, "Checksums computed")

        return listOf(
            ConversionOutput(
                outputFilePath = outFile.absolutePath,
                fileName = outFile.name,
                mimeType = "text/plain",
                sizeBytes = outFile.length(),
                sourceSizeBytes = totalSize,
                extraNote = "SHA-256, SHA-1, MD5 (${files.size} file(s))"
            )
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it) }
}
