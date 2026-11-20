package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.FileFlowContainer
import com.example.engine.DownloadsSaveResult
import com.example.engine.FileHelper
import com.example.model.ConversionOptions
import com.example.model.ConversionOutput
import com.example.model.ConversionState
import com.example.model.ConversionTool
import com.example.model.SelectedFileItem
import com.example.model.ToolId
import com.example.model.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ConverterViewModel(private val container: FileFlowContainer) : ViewModel() {

    private val _tool = MutableStateFlow(ToolRegistry.getTool(ToolId.PNG_TO_JPG))
    val tool: StateFlow<ConversionTool> = _tool.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<SelectedFileItem>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFileItem>> = _selectedFiles.asStateFlow()

    private val _options = MutableStateFlow(ConversionOptions())
    val options: StateFlow<ConversionOptions> = _options.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionState>(ConversionState.Idle)
    val conversionState: StateFlow<ConversionState> = _conversionState.asStateFlow()

    private val _events = MutableSharedFlow<UiMessageEvent>()
    val events: SharedFlow<UiMessageEvent> = _events.asSharedFlow()

    private var activeJob: Job? = null

    fun initForTool(toolId: ToolId, preloadedFiles: List<SelectedFileItem> = emptyList()) {
        val resolvedTool = ToolRegistry.getTool(toolId)
        if (_tool.value.toolId != resolvedTool.toolId) {
            activeJob?.cancel()
            _tool.value = resolvedTool
            _selectedFiles.value = preloadedFiles
            _conversionState.value = ConversionState.Idle
            _options.value = defaultOptionsForTool(resolvedTool)
        } else if (preloadedFiles.isNotEmpty()) {
            _selectedFiles.value = preloadedFiles
            _conversionState.value = ConversionState.Idle
        }
    }

    private fun defaultOptionsForTool(tool: ConversionTool): ConversionOptions {
        return when (tool.toolId) {
            ToolId.PNG_TO_JPG, ToolId.PNG_TO_WEBP, ToolId.JPG_TO_WEBP -> ConversionOptions(quality = 88)
            ToolId.IMAGE_COMPRESS -> ConversionOptions(quality = 65)
            ToolId.IMAGE_RESIZE -> ConversionOptions(quality = 88, scalePercent = 50, imageOutputFormat = "JPG")
            ToolId.PDF_TO_IMAGES -> ConversionOptions(pdfDpi = 180, imageOutputFormat = "PNG", quality = 92)
            ToolId.COMPRESS_PDF -> ConversionOptions(pdfDpi = 120, quality = 60)
            ToolId.AUDIO_COMPRESS -> ConversionOptions(audioBitrateKbps = 96)
            ToolId.WAV_TO_MP3 -> ConversionOptions(audioBitrateKbps = 192)
            ToolId.EXTRACT_AUDIO -> ConversionOptions(extractedAudioFormat = "M4A", audioBitrateKbps = 192)
            else -> ConversionOptions()
        }
    }

    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = uris.mapNotNull { uri ->
                runCatching { FileHelper.resolveSelectedFile(container.appContext, uri) }.getOrNull()
            }
            if (resolved.isEmpty()) {
                _events.emit(UiMessageEvent.Toast("Unable to read selected file(s)."))
                return@launch
            }
            _selectedFiles.update { current ->
                if (_tool.value.supportsBatch) {
                    val existingUris = current.map { it.uri }.toSet()
                    current + resolved.filter { it.uri !in existingUris }
                } else {
                    listOf(resolved.first())
                }
            }
            if (_conversionState.value !is ConversionState.Running) {
                _conversionState.value = ConversionState.Idle
            }
        }
    }

    fun removeFileAt(index: Int) {
        _selectedFiles.update { list ->
            list.filterIndexed { i, _ -> i != index }
        }
    }

    fun moveFile(fromIndex: Int, toIndex: Int) {
        _selectedFiles.update { list ->
            if (fromIndex !in list.indices || toIndex !in list.indices) return@update list
            val mutable = list.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            mutable
        }
    }

    fun clearFiles() {
        _selectedFiles.value = emptyList()
        _conversionState.value = ConversionState.Idle
    }

    fun updateOptions(transform: (ConversionOptions) -> ConversionOptions) {
        _options.update(transform)
    }

    fun startConversion() {
        val currentTool = _tool.value
        val files = _selectedFiles.value
        if (files.isEmpty()) {
            _conversionState.value = ConversionState.Error("Select at least one input file to convert.")
            return
        }
        if (currentTool.requiresMultipleInputs && files.size < 2) {
            _conversionState.value = ConversionState.Error("Please add at least 2 files for ${currentTool.title}.")
            return
        }

        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _conversionState.value = ConversionState.Running(
                progress = 0.02f,
                currentIndex = 1,
                totalFiles = files.size,
                statusMessage = "Preparing ${files.size} file(s)…"
            )

            try {
                val (outputs, durationMs) = container.orchestrator.runConversion(
                    tool = currentTool,
                    files = files,
                    options = _options.value
                ) { progress, currentIdx, message ->
                    _conversionState.value = ConversionState.Running(
                        progress = progress.coerceIn(0f, 1f),
                        currentIndex = currentIdx.coerceIn(1, files.size),
                        totalFiles = files.size,
                        statusMessage = message
                    )
                }
                _conversionState.value = ConversionState.Success(
                    outputs = outputs,
                    durationMs = durationMs
                )
            } catch (_: CancellationException) {
                _conversionState.value = ConversionState.Idle
                _events.emit(UiMessageEvent.Toast("Conversion cancelled"))
            } catch (e: Exception) {
                _conversionState.value = ConversionState.Error(
                    message = e.localizedMessage ?: "Conversion failed.",
                    details = e::class.java.simpleName
                )
            }
        }
    }

    fun cancelConversion() {
        activeJob?.cancel()
        activeJob = null
        _conversionState.value = ConversionState.Idle
    }

    fun resetToIdle() {
        _conversionState.value = ConversionState.Idle
    }

    fun saveOutputToDownloads(output: ConversionOutput) {
        viewModelScope.launch {
            val file = File(output.outputFilePath)
            when (val res = FileHelper.saveToDownloads(container.appContext, file, output.mimeType)) {
                is DownloadsSaveResult.SavedToMediaStore ->
                    _events.emit(UiMessageEvent.Toast("Saved ${res.displayName} to Downloads/FileFlow"))
                is DownloadsSaveResult.RequiresSafDocument ->
                    _events.emit(UiMessageEvent.RequestSafSave(res.sourceFile, res.mimeType))
                is DownloadsSaveResult.Failure ->
                    _events.emit(UiMessageEvent.Toast(res.message))
            }
        }
    }

    fun saveAllOutputsToDownloads(outputs: List<ConversionOutput>) {
        if (outputs.size == 1) {
            saveOutputToDownloads(outputs.first())
            return
        }
        viewModelScope.launch {
            var savedCount = 0
            var safPending: DownloadsSaveResult.RequiresSafDocument? = null
            for (out in outputs) {
                val file = File(out.outputFilePath)
                when (val res = FileHelper.saveToDownloads(container.appContext, file, out.mimeType)) {
                    is DownloadsSaveResult.SavedToMediaStore -> savedCount++
                    is DownloadsSaveResult.RequiresSafDocument -> {
                        safPending = res
                        break
                    }
                    is DownloadsSaveResult.Failure -> Unit
                }
            }
            if (safPending != null) {
                val zipOut = runCatching {
                    container.orchestrator.bundleOutputsAsZip(
                        outputs,
                        FileHelper.sanitizeBaseName(_tool.value.title)
                    )
                }.getOrNull()
                if (zipOut != null) {
                    _events.emit(UiMessageEvent.RequestSafSave(File(zipOut.outputFilePath), zipOut.mimeType))
                } else {
                    _events.emit(UiMessageEvent.RequestSafSave(safPending.sourceFile, safPending.mimeType))
                }
            } else {
                _events.emit(UiMessageEvent.Toast("Saved $savedCount file(s) to Downloads/FileFlow"))
            }
        }
    }

    fun bundleAndSaveZip(outputs: List<ConversionOutput>) {
        viewModelScope.launch {
            runCatching {
                val zipOut = container.orchestrator.bundleOutputsAsZip(
                    outputs,
                    FileHelper.sanitizeBaseName(_tool.value.shortTitle)
                )
                saveOutputToDownloads(zipOut)
            }.onFailure { err ->
                _events.emit(UiMessageEvent.Toast(err.localizedMessage ?: "Failed creating ZIP bundle."))
            }
        }
    }

    fun shareOutput(output: ConversionOutput) {
        val ok = FileHelper.shareFile(container.appContext, File(output.outputFilePath), output.mimeType)
        if (!ok) {
            viewModelScope.launch { _events.emit(UiMessageEvent.Toast("Unable to share file.")) }
        }
    }

    fun shareAllOutputs(outputs: List<ConversionOutput>) {
        val pairs = outputs.map { File(it.outputFilePath) to it.mimeType }
        val ok = FileHelper.shareMultipleFiles(container.appContext, pairs)
        if (!ok) {
            viewModelScope.launch { _events.emit(UiMessageEvent.Toast("Unable to share files.")) }
        }
    }

    fun openOutput(output: ConversionOutput) {
        val ok = FileHelper.openFile(container.appContext, File(output.outputFilePath), output.mimeType)
        if (!ok) {
            viewModelScope.launch {
                _events.emit(UiMessageEvent.Toast("No app installed to open ${output.fileName}."))
            }
        }
    }

    fun completeSafSave(sourceFile: File, targetUri: Uri) {
        viewModelScope.launch {
            runCatching {
                FileHelper.writeFileToUri(container.appContext, sourceFile, targetUri)
            }.onSuccess {
                _events.emit(UiMessageEvent.Toast("Saved ${sourceFile.name}"))
            }.onFailure { err ->
                _events.emit(UiMessageEvent.Toast(err.localizedMessage ?: "Failed saving file."))
            }
        }
    }

    class Factory(private val container: FileFlowContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ConverterViewModel(container) as T
        }
    }
}
