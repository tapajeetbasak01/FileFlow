package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ConversionRecordEntity
import com.example.data.FileFlowContainer
import com.example.engine.DownloadsSaveResult
import com.example.engine.FileHelper
import com.example.model.ConversionTool
import com.example.model.SelectedFileItem
import com.example.model.ThemeMode
import com.example.model.ToolRegistry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface UiMessageEvent {
    data class Toast(val message: String) : UiMessageEvent
    data class RequestSafSave(val sourceFile: File, val mimeType: String) : UiMessageEvent
}

data class QuickPickSuggestionState(
    val files: List<SelectedFileItem> = emptyList(),
    val suggestedTools: List<ConversionTool> = emptyList()
)

class MainViewModel(private val container: FileFlowContainer) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = container.themePreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val recentHistory: StateFlow<List<ConversionRecordEntity>> =
        container.historyRepository.recentHistory(limit = 5)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allHistory: StateFlow<List<ConversionRecordEntity>> =
        container.historyRepository.allHistory
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _quickPickState = MutableStateFlow<QuickPickSuggestionState?>(null)
    val quickPickState: StateFlow<QuickPickSuggestionState?> = _quickPickState.asStateFlow()

    private val _preloadedFilesForTool = MutableStateFlow<List<SelectedFileItem>>(emptyList())
    val preloadedFilesForTool: StateFlow<List<SelectedFileItem>> = _preloadedFilesForTool.asStateFlow()

    private val _events = MutableSharedFlow<UiMessageEvent>()
    val events: SharedFlow<UiMessageEvent> = _events.asSharedFlow()

    fun cycleThemeMode() {
        val next = when (themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        viewModelScope.launch {
            container.themePreferences.setThemeMode(next)
        }
    }

    fun onHomeFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val resolved = uris.mapNotNull { uri ->
                runCatching { FileHelper.resolveSelectedFile(container.appContext, uri) }.getOrNull()
            }
            if (resolved.isEmpty()) {
                _events.emit(UiMessageEvent.Toast("Could not read selected file(s)."))
                return@launch
            }
            val first = resolved.first()
            val suggestions = ToolRegistry.suggestToolsForFile(first.displayName, first.mimeType)
            _quickPickState.value = QuickPickSuggestionState(
                files = resolved,
                suggestedTools = suggestions
            )
        }
    }

    fun dismissQuickPick() {
        _quickPickState.value = null
    }

    fun consumePreloadedFiles(): List<SelectedFileItem> {
        val current = _preloadedFilesForTool.value
        _preloadedFilesForTool.value = emptyList()
        return current
    }

    fun stageFilesForTool(files: List<SelectedFileItem>) {
        _preloadedFilesForTool.value = files
        _quickPickState.value = null
    }

    fun deleteHistoryRecord(record: ConversionRecordEntity) {
        viewModelScope.launch {
            container.historyRepository.deleteRecord(record)
            _events.emit(UiMessageEvent.Toast("Removed ${record.outputFileName} from history"))
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            container.historyRepository.clearAll(FileHelper.getConvertedOutputDir(container.appContext))
            _events.emit(UiMessageEvent.Toast("Conversion history cleared"))
        }
    }

    fun saveRecordToDownloads(record: ConversionRecordEntity) {
        viewModelScope.launch {
            val file = File(record.outputFilePath)
            when (val res = FileHelper.saveToDownloads(container.appContext, file, record.outputMimeType)) {
                is DownloadsSaveResult.SavedToMediaStore ->
                    _events.emit(UiMessageEvent.Toast("Saved ${res.displayName} to Downloads/FileFlow"))
                is DownloadsSaveResult.RequiresSafDocument ->
                    _events.emit(UiMessageEvent.RequestSafSave(res.sourceFile, res.mimeType))
                is DownloadsSaveResult.Failure ->
                    _events.emit(UiMessageEvent.Toast(res.message))
            }
        }
    }

    fun shareRecord(record: ConversionRecordEntity) {
        val file = File(record.outputFilePath)
        val ok = FileHelper.shareFile(container.appContext, file, record.outputMimeType)
        if (!ok) {
            viewModelScope.launch {
                _events.emit(UiMessageEvent.Toast("File is no longer available on device."))
            }
        }
    }

    fun openRecord(record: ConversionRecordEntity) {
        val file = File(record.outputFilePath)
        val ok = FileHelper.openFile(container.appContext, file, record.outputMimeType)
        if (!ok) {
            viewModelScope.launch {
                _events.emit(UiMessageEvent.Toast("No compatible viewer app found for ${record.outputFormat}."))
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
                _events.emit(UiMessageEvent.Toast(err.localizedMessage ?: "Failed to save file."))
            }
        }
    }

    class Factory(private val container: FileFlowContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(container) as T
        }
    }
}
