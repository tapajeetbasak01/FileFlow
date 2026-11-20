package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.model.ConversionOptions
import com.example.model.ConversionOutput
import com.example.model.PdfPageSizeOption
import com.example.model.SelectedFileItem
import com.example.model.SplitMode
import com.example.model.ToolId
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.multipdf.Splitter
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

class PdfConversionEngine(
    private val context: Context,
    private val imageEngine: ImageConversionEngine
) {

    @Synchronized
    private fun ensurePdfBoxInit() {
        if (!pdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            pdfBoxInitialized = true
        }
    }

    suspend fun convert(
        toolId: ToolId,
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (progress: Float, index: Int, message: String) -> Unit
    ): List<ConversionOutput> {
        ensurePdfBoxInit()
        return when (toolId) {
            ToolId.MERGE_PDF -> mergePdfs(files, outputDir, onProgress)
            ToolId.SPLIT_PDF -> splitPdf(files.first(), options, outputDir, onProgress)
            ToolId.EXTRACT_PDF_PAGES -> extractPages(files.first(), options, outputDir, onProgress)
            ToolId.PDF_TO_IMAGES -> pdfToImages(files.first(), options, outputDir, onProgress)
            ToolId.IMAGES_TO_PDF -> imagesToPdf(files, options, outputDir, onProgress)
            ToolId.PDF_TO_TXT -> pdfToText(files, outputDir, onProgress)
            ToolId.COMPRESS_PDF -> compressPdfs(files, options, outputDir, onProgress)
            else -> throw IllegalArgumentException("Unsupported PDF tool: $toolId")
        }
    }

    private suspend fun mergePdfs(
        files: List<SelectedFileItem>,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        if (files.size < 2) {
            throw IllegalArgumentException("Select at least 2 PDF files to merge.")
        }
        val tempFiles = mutableListOf<File>()
        try {
            val merger = PDFMergerUtility()
            var totalSourceSize = 0L
            files.forEachIndexed { idx, item ->
                coroutineContext.ensureActive()
                onProgress(
                    (idx.toFloat() / (files.size + 1)) * 0.7f,
                    idx + 1,
                    "Staging ${item.displayName}…"
                )
                val tmp = FileHelper.copyUriToTempFile(context, item.uri, item.displayName)
                tempFiles += tmp
                merger.addSource(tmp)
                totalSourceSize += item.sizeBytes
            }

            onProgress(0.8f, files.size, "Merging ${files.size} PDFs…")
            val firstBase = FileHelper.sanitizeBaseName(files.first().displayName)
            val outFile = FileHelper.uniqueOutputFile(outputDir, "${firstBase}_merged", "pdf")
            merger.destinationFileName = outFile.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())

            onProgress(1f, files.size, "Merge complete")
            return listOf(
                ConversionOutput(
                    outputFilePath = outFile.absolutePath,
                    fileName = outFile.name,
                    mimeType = "application/pdf",
                    sizeBytes = outFile.length(),
                    sourceSizeBytes = totalSourceSize,
                    extraNote = "Merged ${files.size} PDF files"
                )
            )
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    private suspend fun splitPdf(
        item: SelectedFileItem,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val tempPdf = FileHelper.copyUriToTempFile(context, item.uri, item.displayName)
        val outputs = mutableListOf<ConversionOutput>()
        try {
            onProgress(0.15f, 1, "Loading ${item.displayName}…")
            PDDocument.load(tempPdf).use { doc ->
                val totalPages = doc.numberOfPages
                if (totalPages < 2) {
                    throw IllegalArgumentException("PDF has only $totalPages page; need at least 2 pages to split.")
                }

                val splitAt = when (options.splitMode) {
                    SplitMode.EVERY_PAGE -> 1
                    SplitMode.CHUNKS -> options.splitChunkSize.coerceIn(1, totalPages)
                }

                val splitter = Splitter().apply { setSplitAtPage(splitAt) }
                val parts = splitter.split(doc)
                val base = FileHelper.sanitizeBaseName(item.displayName)

                parts.forEachIndexed { idx, partDoc ->
                    coroutineContext.ensureActive()
                    partDoc.use { pd ->
                        val outFile = FileHelper.uniqueOutputFile(
                            outputDir,
                            "${base}_part_${idx + 1}",
                            "pdf"
                        )
                        pd.save(outFile)
                        outputs += ConversionOutput(
                            outputFilePath = outFile.absolutePath,
                            fileName = outFile.name,
                            mimeType = "application/pdf",
                            sizeBytes = outFile.length(),
                            sourceSizeBytes = item.sizeBytes,
                            extraNote = "Part ${idx + 1} of ${parts.size} (${pd.numberOfPages} pg)"
                        )
                    }
                    onProgress(
                        0.2f + 0.8f * ((idx + 1).toFloat() / parts.size.toFloat()),
                        1,
                        "Saved part ${idx + 1} of ${parts.size}"
                    )
                }
            }
            return outputs
        } finally {
            tempPdf.delete()
        }
    }

    private suspend fun extractPages(
        item: SelectedFileItem,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val tempPdf = FileHelper.copyUriToTempFile(context, item.uri, item.displayName)
        try {
            onProgress(0.2f, 1, "Reading pages from ${item.displayName}…")
            PDDocument.load(tempPdf).use { sourceDoc ->
                val totalPages = sourceDoc.numberOfPages
                val pagesToExtract = parsePageRangeExpression(options.pdfPageRange, totalPages)
                if (pagesToExtract.isEmpty()) {
                    throw IllegalArgumentException(
                        "Invalid page range \"${options.pdfPageRange}\". Document has $totalPages pages (valid: 1-$totalPages)."
                    )
                }

                val base = FileHelper.sanitizeBaseName(item.displayName)
                val outFile = FileHelper.uniqueOutputFile(outputDir, "${base}_extracted", "pdf")

                PDDocument().use { destDoc ->
                    pagesToExtract.forEachIndexed { idx, oneBasedPage ->
                        coroutineContext.ensureActive()
                        destDoc.importPage(sourceDoc.getPage(oneBasedPage - 1))
                        onProgress(
                            0.25f + 0.65f * ((idx + 1).toFloat() / pagesToExtract.size.toFloat()),
                            1,
                            "Extracting page $oneBasedPage…"
                        )
                    }
                    destDoc.save(outFile)
                }

                onProgress(1f, 1, "Extraction complete")
                return listOf(
                    ConversionOutput(
                        outputFilePath = outFile.absolutePath,
                        fileName = outFile.name,
                        mimeType = "application/pdf",
                        sizeBytes = outFile.length(),
                        sourceSizeBytes = item.sizeBytes,
                        extraNote = "Extracted ${pagesToExtract.size} of $totalPages pages"
                    )
                )
            }
        } finally {
            tempPdf.delete()
        }
    }

    private suspend fun pdfToImages(
        item: SelectedFileItem,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val tempPdf = FileHelper.copyUriToTempFile(context, item.uri, item.displayName)
        val outputs = mutableListOf<ConversionOutput>()
        val scale = (options.pdfDpi.coerceIn(72, 300)) / 72f
        val useJpg = options.imageOutputFormat.equals("JPG", ignoreCase = true)
        val ext = if (useJpg) "jpg" else "png"
        val mime = if (useJpg) "image/jpeg" else "image/png"
        val format = if (useJpg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG
        val base = FileHelper.sanitizeBaseName(item.displayName)

        try {
            ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = renderer.pageCount
                    if (count == 0) throw IllegalArgumentException("PDF has 0 pages.")

                    for (i in 0 until count) {
                        coroutineContext.ensureActive()
                        onProgress(
                            i.toFloat() / count.toFloat(),
                            1,
                            "Rendering page ${i + 1} of $count…"
                        )
                        renderer.openPage(i).use { page ->
                            val w = (page.width * scale).roundToInt().coerceIn(100, 3600)
                            val h = (page.height * scale).roundToInt().coerceIn(100, 4800)
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            try {
                                Canvas(bitmap).drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val outFile = FileHelper.uniqueOutputFile(
                                    outputDir,
                                    "${base}_page_${i + 1}",
                                    ext
                                )
                                FileOutputStream(outFile).use { fos ->
                                    bitmap.compress(format, options.quality.coerceIn(50, 100), fos)
                                }
                                outputs += ConversionOutput(
                                    outputFilePath = outFile.absolutePath,
                                    fileName = outFile.name,
                                    mimeType = mime,
                                    sizeBytes = outFile.length(),
                                    sourceSizeBytes = item.sizeBytes,
                                    extraNote = "Page ${i + 1} (${w}×${h})"
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            onProgress(1f, 1, "Rendered ${outputs.size} pages")
            return outputs
        } finally {
            tempPdf.delete()
        }
    }

    private suspend fun imagesToPdf(
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val pdfDocument = PdfDocument()
        var totalSourceBytes = 0L
        val firstBase = FileHelper.sanitizeBaseName(files.first().displayName)
        val outFile = FileHelper.uniqueOutputFile(outputDir, "${firstBase}_pack", "pdf")

        try {
            files.forEachIndexed { idx, item ->
                coroutineContext.ensureActive()
                totalSourceBytes += item.sizeBytes
                onProgress(
                    idx.toFloat() / files.size.toFloat(),
                    idx + 1,
                    "Adding image ${idx + 1} of ${files.size}…"
                )
                val bitmap = imageEngine.decodeOrientedBitmap(item.uri, item.displayName, maxDimension = 2400)
                try {
                    val (pageW, pageH, margin) = when (options.pdfPageSize) {
                        PdfPageSizeOption.A4 -> Triple(595, 842, 28f)
                        PdfPageSizeOption.LETTER -> Triple(612, 792, 28f)
                        PdfPageSizeOption.FIT_IMAGE -> Triple(bitmap.width, bitmap.height, 0f)
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    if (options.pdfPageSize == PdfPageSizeOption.FIT_IMAGE) {
                        canvas.drawBitmap(bitmap, 0f, 0f, null)
                    } else {
                        val availW = pageW - margin * 2f
                        val availH = pageH - margin * 2f
                        val scale = minOf(availW / bitmap.width.toFloat(), availH / bitmap.height.toFloat())
                        val drawW = bitmap.width * scale
                        val drawH = bitmap.height * scale
                        val left = (pageW - drawW) / 2f
                        val top = (pageH - drawH) / 2f
                        val destRect = RectF(left, top, left + drawW, top + drawH)
                        canvas.drawBitmap(bitmap, null, destRect, null)
                    }
                    pdfDocument.finishPage(page)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            }

            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            onProgress(1f, files.size, "PDF created")
            return listOf(
                ConversionOutput(
                    outputFilePath = outFile.absolutePath,
                    fileName = outFile.name,
                    mimeType = "application/pdf",
                    sizeBytes = outFile.length(),
                    sourceSizeBytes = totalSourceBytes,
                    extraNote = "${files.size} image(s) • ${options.pdfPageSize.label}"
                )
            )
        } finally {
            pdfDocument.close()
        }
    }

    private suspend fun pdfToText(
        files: List<SelectedFileItem>,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val outputs = mutableListOf<ConversionOutput>()
        files.forEachIndexed { idx, item ->
            coroutineContext.ensureActive()
            onProgress(idx.toFloat() / files.size.toFloat(), idx + 1, "Extracting text from ${item.displayName}…")
            val tempPdf = FileHelper.copyUriToTempFile(context, item.uri, item.displayName)
            try {
                val extractedText = PDDocument.load(tempPdf).use { doc ->
                    val stripper = PDFTextStripper()
                    stripper.getText(doc)
                }
                val outFile = FileHelper.uniqueOutputFile(
                    outputDir,
                    FileHelper.sanitizeBaseName(item.displayName),
                    "txt"
                )
                outFile.writeText(
                    if (extractedText.isBlank()) {
                        "[No selectable text found in ${item.displayName}. This PDF may contain scanned images.]"
                    } else {
                        extractedText
                    }
                )
                outputs += ConversionOutput(
                    outputFilePath = outFile.absolutePath,
                    fileName = outFile.name,
                    mimeType = "text/plain",
                    sizeBytes = outFile.length(),
                    sourceSizeBytes = item.sizeBytes,
                    extraNote = "${extractedText.lines().size} lines extracted"
                )
            } finally {
                tempPdf.delete()
            }
        }
        return outputs
    }

    private suspend fun compressPdfs(
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        outputDir: File,
        onProgress: suspend (Float, Int, String) -> Unit
    ): List<ConversionOutput> {
        val outputs = mutableListOf<ConversionOutput>()
        val targetDpi = options.pdfDpi.coerceIn(72, 160)
        val scale = targetDpi / 72f
        val jpegQuality = options.quality.coerceIn(30, 85)

        files.forEachIndexed { fileIdx, item ->
            coroutineContext.ensureActive()
            val tempPdf = FileHelper.copyUriToTempFile(context, item.uri, item.displayName)
            val pdfDoc = PdfDocument()
            try {
                ParcelFileDescriptor.open(tempPdf, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        val pageCount = renderer.pageCount
                        for (p in 0 until pageCount) {
                            coroutineContext.ensureActive()
                            val overall = (fileIdx + (p.toFloat() / pageCount.coerceAtLeast(1))) / files.size.toFloat()
                            onProgress(overall, fileIdx + 1, "Optimizing ${item.displayName} (page ${p + 1}/$pageCount)…")
                            renderer.openPage(p).use { page ->
                                val w = (page.width * scale).roundToInt().coerceIn(100, 2200)
                                val h = (page.height * scale).roundToInt().coerceIn(100, 3000)
                                val rawBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                var compressedBitmap: Bitmap? = null
                                try {
                                    Canvas(rawBitmap).drawColor(Color.WHITE)
                                    page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                    val baos = ByteArrayOutputStream()
                                    rawBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
                                    val bytes = baos.toByteArray()
                                    compressedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                                    val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, p + 1).create()
                                    val outPage = pdfDoc.startPage(pageInfo)
                                    val dest = RectF(0f, 0f, page.width.toFloat(), page.height.toFloat())
                                    outPage.canvas.drawBitmap(compressedBitmap ?: rawBitmap, null, dest, null)
                                    pdfDoc.finishPage(outPage)
                                } finally {
                                    rawBitmap.recycle()
                                    compressedBitmap?.recycle()
                                }
                            }
                        }
                    }
                }
                val outFile = FileHelper.uniqueOutputFile(
                    outputDir,
                    "${FileHelper.sanitizeBaseName(item.displayName)}_compressed",
                    "pdf"
                )
                FileOutputStream(outFile).use { fos -> pdfDoc.writeTo(fos) }
                outputs += ConversionOutput(
                    outputFilePath = outFile.absolutePath,
                    fileName = outFile.name,
                    mimeType = "application/pdf",
                    sizeBytes = outFile.length(),
                    sourceSizeBytes = item.sizeBytes,
                    extraNote = "${targetDpi} DPI • Q$jpegQuality"
                )
            } finally {
                pdfDoc.close()
                tempPdf.delete()
            }
        }
        return outputs
    }

    companion object {
        @Volatile
        private var pdfBoxInitialized = false

        fun parsePageRangeExpression(expr: String, maxPage: Int): List<Int> {
            if (maxPage <= 0) return emptyList()
            val cleaned = expr.trim()
            if (cleaned.isEmpty()) return emptyList()
            val result = linkedSetOf<Int>()
            cleaned.split(',').forEach { token ->
                val part = token.trim()
                if (part.isEmpty()) return@forEach
                if ('-' in part) {
                    val bounds = part.split('-', limit = 2)
                    val start = bounds[0].trim().toIntOrNull()
                    val end = bounds[1].trim().toIntOrNull()
                    if (start != null && end != null && start >= 1 && end >= start) {
                        for (p in start..minOf(end, maxPage)) {
                            result += p
                        }
                    }
                } else {
                    val page = part.toIntOrNull()
                    if (page != null && page in 1..maxPage) {
                        result += page
                    }
                }
            }
            return result.toList()
        }
    }
}
