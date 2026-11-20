package com.example.engine

import android.content.Context
import com.example.data.ConversionRecordEntity
import com.example.data.HistoryRepository
import com.example.model.ConversionCategory
import com.example.model.ConversionOptions
import com.example.model.ConversionOutput
import com.example.model.ConversionTool
import com.example.model.SelectedFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ConversionOrchestrator(
    private val context: Context,
    private val historyRepository: HistoryRepository
) {
    private val imageEngine = ImageConversionEngine(context)
    private val pdfEngine = PdfConversionEngine(context, imageEngine)
    private val documentEngine = DocumentConversionEngine(context)
    private val audioEngine = AudioConversionEngine(context)
    private val videoEngine = VideoConversionEngine(context, audioEngine)
    private val archiveEngine = ArchiveConversionEngine(context)

    suspend fun runConversion(
        tool: ConversionTool,
        files: List<SelectedFileItem>,
        options: ConversionOptions,
        onProgress: suspend (progress: Float, currentIndex: Int, message: String) -> Unit
    ): Pair<List<ConversionOutput>, Long> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) {
            throw IllegalArgumentException("Please select at least one input file.")
        }
        val outputDir = FileHelper.getConvertedOutputDir(context)
        val startMs = System.currentTimeMillis()

        val outputs = when (tool.category) {
            ConversionCategory.IMAGE -> imageEngine.convert(tool.toolId, files, options, outputDir, onProgress)
            ConversionCategory.PDF -> pdfEngine.convert(tool.toolId, files, options, outputDir, onProgress)
            ConversionCategory.DOCUMENT -> documentEngine.convert(tool.toolId, files, options, outputDir, onProgress)
            ConversionCategory.AUDIO -> audioEngine.convert(tool.toolId, files, options, outputDir, onProgress)
            ConversionCategory.VIDEO -> videoEngine.convert(tool.toolId, files, options, outputDir, onProgress)
            ConversionCategory.ARCHIVE -> archiveEngine.convert(tool.toolId, files, outputDir, onProgress)
        }

        val elapsedMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        val records = outputs.mapIndexed { idx, output ->
            val src = files.getOrNull(idx) ?: files.first()
            ConversionRecordEntity(
                toolId = tool.toolId.id,
                toolTitle = tool.title,
                categoryId = tool.category.id,
                sourceFileName = if (files.size > 1 && outputs.size == 1) {
                    "${files.first().displayName} + ${files.size - 1} more"
                } else {
                    src.displayName
                },
                sourceFormat = src.extension.uppercase().ifBlank { tool.inputLabel },
                sourceSizeBytes = output.sourceSizeBytes,
                outputFileName = output.fileName,
                outputFilePath = output.outputFilePath,
                outputFormat = output.fileName.substringAfterLast('.', tool.outputLabel).uppercase(),
                outputMimeType = output.mimeType,
                outputSizeBytes = output.sizeBytes,
                durationMs = elapsedMs
            )
        }
        historyRepository.recordConversions(records)

        outputs to elapsedMs
    }

    suspend fun bundleOutputsAsZip(
        outputs: List<ConversionOutput>,
        baseName: String
    ): ConversionOutput = withContext(Dispatchers.IO) {
        val outputDir = FileHelper.getConvertedOutputDir(context)
        val files = outputs.map { File(it.outputFilePath) }
        archiveEngine.createZipFromFiles(files, "${baseName}_bundle", outputDir)
    }
}
