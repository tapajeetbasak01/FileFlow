package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.example.model.ConversionOptions
import com.example.model.ConversionOutput
import com.example.model.SelectedFileItem
import com.example.model.ToolId
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

class ImageConversionEngine(private val context: Context) {

    suspend fun convert(
        toolId: ToolId,
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (progress: Float, index: Int, message: String) -> Unit
    ): List<ConversionOutput> {
        val outputs = mutableListOf<ConversionOutput>()

        files.forEachIndexed { index, item ->
            coroutineContext.ensureActive()
            onProgress(
                index.toFloat() / files.size.toFloat(),
                index + 1,
                "Processing ${item.displayName}…"
            )

            val decoded = decodeOrientedBitmap(item.uri, item.displayName)
            try {
                val targetBitmap = applyResizeIfNeeded(
                    bitmap = decoded,
                    scalePercent = if (toolId == ToolId.IMAGE_RESIZE) options.scalePercent else 100
                )

                val (targetExt, compressFormat, mimeType) = resolveTargetFormat(toolId, item.extension, options)
                val quality = when (toolId) {
                    ToolId.JPG_TO_PNG, ToolId.WEBP_TO_PNG -> 100
                    else -> options.quality.coerceIn(10, 100)
                }

                val finalBitmap = if (compressFormat == Bitmap.CompressFormat.JPEG) {
                    flattenAlphaToWhite(targetBitmap)
                } else {
                    targetBitmap
                }

                val suffix = when (toolId) {
                    ToolId.IMAGE_COMPRESS -> "_compressed"
                    ToolId.IMAGE_RESIZE -> "_${options.scalePercent}pct"
                    else -> ""
                }
                val baseName = FileHelper.sanitizeBaseName(item.displayName) + suffix
                val outFile = FileHelper.uniqueOutputFile(outputDir, baseName, targetExt)

                FileOutputStream(outFile).use { fos ->
                    val ok = finalBitmap.compress(compressFormat, quality, fos)
                    if (!ok) {
                        throw IllegalStateException("Failed to encode ${item.displayName} to $targetExt.")
                    }
                }

                if (finalBitmap !== targetBitmap) finalBitmap.recycle()
                if (targetBitmap !== decoded) targetBitmap.recycle()

                outputs += ConversionOutput(
                    outputFilePath = outFile.absolutePath,
                    fileName = outFile.name,
                    mimeType = mimeType,
                    sizeBytes = outFile.length(),
                    sourceSizeBytes = item.sizeBytes,
                    extraNote = "${decoded.width}×${decoded.height} → ${targetExt.uppercase()} (Q$quality)"
                )
            } finally {
                if (!decoded.isRecycled) decoded.recycle()
            }

            onProgress(
                (index + 1).toFloat() / files.size.toFloat(),
                index + 1,
                "Completed ${item.displayName}"
            )
        }

        return outputs
    }

    private fun resolveTargetFormat(
        toolId: ToolId,
        sourceExt: String,
        options: ConversionOptions
    ): Triple<String, Bitmap.CompressFormat, String> {
        val webpLossy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }

        return when (toolId) {
            ToolId.PNG_TO_JPG -> Triple("jpg", Bitmap.CompressFormat.JPEG, "image/jpeg")
            ToolId.JPG_TO_PNG, ToolId.WEBP_TO_PNG -> Triple("png", Bitmap.CompressFormat.PNG, "image/png")
            ToolId.PNG_TO_WEBP, ToolId.JPG_TO_WEBP -> Triple("webp", webpLossy, "image/webp")
            ToolId.IMAGE_COMPRESS -> when (sourceExt.lowercase()) {
                "webp" -> Triple("webp", webpLossy, "image/webp")
                "png" -> Triple("jpg", Bitmap.CompressFormat.JPEG, "image/jpeg")
                else -> Triple("jpg", Bitmap.CompressFormat.JPEG, "image/jpeg")
            }
            ToolId.IMAGE_RESIZE, ToolId.IMAGE_ANY_TO_PNG_JPG -> when (options.imageOutputFormat.uppercase()) {
                "JPG", "JPEG" -> Triple("jpg", Bitmap.CompressFormat.JPEG, "image/jpeg")
                "WEBP" -> Triple("webp", webpLossy, "image/webp")
                else -> Triple("png", Bitmap.CompressFormat.PNG, "image/png")
            }
            else -> Triple("png", Bitmap.CompressFormat.PNG, "image/png")
        }
    }

    fun decodeOrientedBitmap(uri: Uri, displayName: String, maxDimension: Int = 4096): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                    val w = info.size.width
                    val h = info.size.height
                    if (w > maxDimension || h > maxDimension) {
                        val scale = maxDimension.toFloat() / maxOf(w, h).toFloat()
                        decoder.setTargetSize(
                            (w * scale).roundToInt().coerceAtLeast(1),
                            (h * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                }
            } catch (_: Exception) {
                // Fallback to BitmapFactory below
            }
        }

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
            throw IllegalArgumentException("Could not decode image: $displayName")
        }

        var inSampleSize = 1
        while ((boundsOpts.outWidth / inSampleSize) > maxDimension ||
            (boundsOpts.outHeight / inSampleSize) > maxDimension
        ) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val rawBitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: throw IllegalArgumentException("Unsupported or corrupted image: $displayName")

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        return if (!matrix.isIdentity) {
            val rotated = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
            if (rotated !== rawBitmap) rawBitmap.recycle()
            rotated
        } else {
            rawBitmap
        }
    }

    private fun applyResizeIfNeeded(bitmap: Bitmap, scalePercent: Int): Bitmap {
        val clamped = scalePercent.coerceIn(10, 100)
        if (clamped == 100) return bitmap
        val newW = (bitmap.width * clamped / 100f).roundToInt().coerceAtLeast(1)
        val newH = (bitmap.height * clamped / 100f).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }

    private fun flattenAlphaToWhite(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(source, 0f, 0f, null)
        return result
    }
}
