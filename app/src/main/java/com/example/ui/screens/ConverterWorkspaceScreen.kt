package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.engine.FileHelper
import com.example.model.ConversionCategory
import com.example.model.ConversionOptions
import com.example.model.ConversionOutput
import com.example.model.ConversionState
import com.example.model.ConversionTool
import com.example.model.PdfPageSizeOption
import com.example.model.PickerKind
import com.example.model.SelectedFileItem
import com.example.model.SplitMode
import com.example.model.ToolId
import com.example.model.VideoQualityPreset
import com.example.ui.components.ActionChipButton
import com.example.ui.components.FormatFlowPill
import com.example.ui.components.categoryIcon
import com.example.ui.theme.SuccessGreen
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterWorkspaceScreen(
    tool: ConversionTool,
    selectedFiles: List<SelectedFileItem>,
    options: ConversionOptions,
    conversionState: ConversionState,
    onBack: () -> Unit,
    onAddUris: (List<android.net.Uri>) -> Unit,
    onRemoveFileAt: (Int) -> Unit,
    onMoveFile: (Int, Int) -> Unit,
    onClearFiles: () -> Unit,
    onUpdateOptions: ((ConversionOptions) -> ConversionOptions) -> Unit,
    onStartConversion: () -> Unit,
    onCancelConversion: () -> Unit,
    onResetToIdle: () -> Unit,
    onSaveOutput: (ConversionOutput) -> Unit,
    onSaveAllOutputs: (List<ConversionOutput>) -> Unit,
    onBundleZip: (List<ConversionOutput>) -> Unit,
    onShareOutput: (ConversionOutput) -> Unit,
    onShareAllOutputs: (List<ConversionOutput>) -> Unit,
    onOpenOutput: (ConversionOutput) -> Unit,
    modifier: Modifier = Modifier
) {
    // Zero-permission Photo Picker for images/videos + SAF for documents/audio/archives
    val singlePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { onAddUris(listOf(it)) } }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris -> if (uris.isNotEmpty()) onAddUris(uris) }

    val singleSafPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onAddUris(listOf(it)) } }

    val multiSafPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) onAddUris(uris) }

    val launchFilePicker: () -> Unit = {
        when (tool.pickerKind) {
            PickerKind.PHOTO_PICKER_IMAGE -> {
                val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                if (tool.supportsBatch) multiPhotoPicker.launch(req) else singlePhotoPicker.launch(req)
            }
            PickerKind.PHOTO_PICKER_VIDEO -> {
                val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                if (tool.supportsBatch) multiPhotoPicker.launch(req) else singlePhotoPicker.launch(req)
            }
            PickerKind.SAF_DOCUMENT -> {
                val mimes = tool.acceptedMimeTypes.toTypedArray()
                if (tool.supportsBatch) multiSafPicker.launch(mimes) else singleSafPicker.launch(mimes)
            }
        }
    }

    val isRunning = conversionState is ConversionState.Running
    val accent = tool.category.accentColor

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tool.category.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("workspace_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            actions = {
                FormatFlowPill(
                    inputLabel = tool.inputLabel,
                    outputLabel = tool.outputLabel,
                    accent = accent,
                    modifier = Modifier.padding(end = 12.dp)
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tool Intro Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = accent.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = categoryIcon(tool.category),
                                    contentDescription = null,
                                    tint = accent
                                )
                            }
                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Input File Queue Card
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = if (tool.supportsBatch) "Input Queue (${selectedFiles.size})" else "Input File",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (tool.requiresMultipleInputs) {
                                            "Add 2 or more files (reorder supported)"
                                        } else if (tool.supportsBatch) {
                                            "Supports single or batch multi-file selection"
                                        } else {
                                            "Select a file via Android picker"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (selectedFiles.isNotEmpty() && !isRunning) {
                                    TextButton(onClick = onClearFiles) {
                                        Text("Clear")
                                    }
                                }
                            }

                            if (selectedFiles.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    selectedFiles.forEachIndexed { idx, file ->
                                        SelectedFileRow(
                                            file = file,
                                            index = idx,
                                            total = selectedFiles.size,
                                            allowReorder = tool.toolId == ToolId.MERGE_PDF || tool.toolId == ToolId.IMAGES_TO_PDF,
                                            enabled = !isRunning,
                                            onMoveUp = { onMoveFile(idx, idx - 1) },
                                            onMoveDown = { onMoveFile(idx, idx + 1) },
                                            onRemove = { onRemoveFileAt(idx) }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = launchFilePicker,
                                    enabled = !isRunning,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("workspace_pick_files_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            selectedFiles.isEmpty() -> "Select ${tool.inputLabel}"
                                            tool.supportsBatch -> "Add More Files"
                                            else -> "Replace File"
                                        }
                                    )
                                }

                                // Fallback SAF document button when Photo Picker is active (for Drive/Files app images/videos)
                                if (tool.pickerKind != PickerKind.SAF_DOCUMENT) {
                                    OutlinedButton(
                                        onClick = {
                                            val mimes = tool.acceptedMimeTypes.toTypedArray()
                                            if (tool.supportsBatch) multiSafPicker.launch(mimes)
                                            else singleSafPicker.launch(mimes)
                                        },
                                        enabled = !isRunning,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("workspace_browse_saf_button")
                                    ) {
                                        Text("Files App")
                                    }
                                }
                            }
                        }
                    }
                }

                // Tool-Specific Configuration Panel
                item {
                    ToolOptionsSection(
                        tool = tool,
                        options = options,
                        selectedFiles = selectedFiles,
                        enabled = !isRunning,
                        onUpdateOptions = onUpdateOptions
                    )
                }

                // Live Progress Card OR Primary Convert CTA
                item {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = conversionState is ConversionState.Running,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        val running = conversionState as? ConversionState.Running
                        if (running != null) {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Converting (${running.currentIndex}/${running.totalFiles})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${(running.progress * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    LinearProgressIndicator(
                                        progress = { running.progress.coerceIn(0.02f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(CircleShape)
                                    )

                                    Text(
                                        text = running.statusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    OutlinedButton(
                                        onClick = onCancelConversion,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("workspace_cancel_button")
                                    ) {
                                        Text(stringResource(R.string.cancel_conversion))
                                    }
                                }
                            }
                        }
                    }

                    if (!isRunning) {
                        Button(
                            onClick = onStartConversion,
                            enabled = selectedFiles.isNotEmpty() &&
                                (!tool.requiresMultipleInputs || selectedFiles.size >= 2),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("workspace_convert_button")
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedFiles.size > 1) {
                                    "Convert ${selectedFiles.size} Files"
                                } else {
                                    stringResource(R.string.start_conversion)
                                },
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Error State Card
                if (conversionState is ConversionState.Error) {
                    item {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = "Error",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Conversion Failed",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                Text(
                                    text = conversionState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = onResetToIdle) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }

                // Success Outputs Card
                if (conversionState is ConversionState.Success) {
                    item {
                        ConversionSuccessCard(
                            outputs = conversionState.outputs,
                            durationMs = conversionState.durationMs,
                            onSaveOutput = onSaveOutput,
                            onSaveAllOutputs = onSaveAllOutputs,
                            onBundleZip = onBundleZip,
                            onShareOutput = onShareOutput,
                            onShareAllOutputs = onShareAllOutputs,
                            onOpenOutput = onOpenOutput
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SelectedFileRow(
    file: SelectedFileItem,
    index: Int,
    total: Int,
    allowReorder: Boolean,
    enabled: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (file.mimeType.startsWith("image/")) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = file.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = file.extension.uppercase().take(4).ifBlank { "FILE" },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val pagesLabel = file.pageCount?.let { " • $it pages" }.orEmpty()
                Text(
                    text = "${FileHelper.formatFileSize(file.sizeBytes)}$pagesLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (allowReorder && total > 1) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = enabled && index > 0,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = enabled && index < total - 1,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
            }

            IconButton(
                onClick = onRemove,
                enabled = enabled,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove file", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ToolOptionsSection(
    tool: ConversionTool,
    options: ConversionOptions,
    selectedFiles: List<SelectedFileItem>,
    enabled: Boolean,
    onUpdateOptions: ((ConversionOptions) -> ConversionOptions) -> Unit
) {
    val hasConfigurableOptions = tool.toolId !in setOf(
        ToolId.JPG_TO_PNG,
        ToolId.WEBP_TO_PNG,
        ToolId.MERGE_PDF,
        ToolId.PDF_TO_TXT,
        ToolId.MARKDOWN_TO_HTML,
        ToolId.HTML_TO_TXT,
        ToolId.TXT_TO_HTML,
        ToolId.CSV_TO_JSON,
        ToolId.JSON_TO_CSV,
        ToolId.MP3_TO_WAV,
        ToolId.AUDIO_TO_M4A,
        ToolId.CREATE_ZIP,
        ToolId.EXTRACT_ZIP,
        ToolId.FILE_CHECKSUM
    )

    if (!hasConfigurableOptions) return

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = tool.category.accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Conversion Settings",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            when (tool.toolId) {
                ToolId.PNG_TO_JPG, ToolId.PNG_TO_WEBP, ToolId.JPG_TO_WEBP, ToolId.IMAGE_COMPRESS -> {
                    QualitySliderRow(
                        label = "Compression Quality",
                        value = options.quality,
                        enabled = enabled,
                        onValueChange = { q -> onUpdateOptions { it.copy(quality = q) } }
                    )
                }

                ToolId.IMAGE_RESIZE -> {
                    Text("Scale Percentage", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 50, 75).forEach { pct ->
                            FilterChip(
                                selected = options.scalePercent == pct,
                                onClick = { if (enabled) onUpdateOptions { it.copy(scalePercent = pct) } },
                                label = { Text("$pct%") }
                            )
                        }
                    }
                    OutputFormatRow(
                        options = listOf("JPG", "PNG", "WEBP"),
                        selected = options.imageOutputFormat,
                        enabled = enabled,
                        onSelect = { fmt -> onUpdateOptions { it.copy(imageOutputFormat = fmt) } }
                    )
                    QualitySliderRow(
                        label = "Quality",
                        value = options.quality,
                        enabled = enabled,
                        onValueChange = { q -> onUpdateOptions { it.copy(quality = q) } }
                    )
                }

                ToolId.IMAGE_ANY_TO_PNG_JPG -> {
                    OutputFormatRow(
                        options = listOf("PNG", "JPG", "WEBP"),
                        selected = options.imageOutputFormat,
                        enabled = enabled,
                        onSelect = { fmt -> onUpdateOptions { it.copy(imageOutputFormat = fmt) } }
                    )
                    if (options.imageOutputFormat != "PNG") {
                        QualitySliderRow(
                            label = "Quality",
                            value = options.quality,
                            enabled = enabled,
                            onValueChange = { q -> onUpdateOptions { it.copy(quality = q) } }
                        )
                    }
                }

                ToolId.SPLIT_PDF -> {
                    Text("Split Strategy", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SplitMode.entries.forEach { mode ->
                            FilterChip(
                                selected = options.splitMode == mode,
                                onClick = { if (enabled) onUpdateOptions { it.copy(splitMode = mode) } },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    if (options.splitMode == SplitMode.CHUNKS) {
                        Text(
                            text = "Pages per output file: ${options.splitChunkSize}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(
                            value = options.splitChunkSize.toFloat(),
                            onValueChange = { v -> onUpdateOptions { it.copy(splitChunkSize = v.roundToInt()) } },
                            valueRange = 1f..10f,
                            steps = 8,
                            enabled = enabled
                        )
                    }
                }

                ToolId.EXTRACT_PDF_PAGES -> {
                    val pageHint = selectedFiles.firstOrNull()?.pageCount?.let { "Document has $it pages" }
                        ?: "Example: 1, 3-5, 8"
                    OutlinedTextField(
                        value = options.pdfPageRange,
                        onValueChange = { v -> onUpdateOptions { it.copy(pdfPageRange = v) } },
                        label = { Text("Pages to extract") },
                        supportingText = { Text(pageHint) },
                        singleLine = true,
                        enabled = enabled,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_page_range_input")
                    )
                }

                ToolId.PDF_TO_IMAGES -> {
                    OutputFormatRow(
                        options = listOf("PNG", "JPG"),
                        selected = options.imageOutputFormat,
                        enabled = enabled,
                        onSelect = { fmt -> onUpdateOptions { it.copy(imageOutputFormat = fmt) } }
                    )
                    Text("Render Resolution (DPI)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(120 to "120 DPI", 180 to "180 DPI", 260 to "260 DPI").forEach { (dpi, label) ->
                            FilterChip(
                                selected = options.pdfDpi == dpi,
                                onClick = { if (enabled) onUpdateOptions { it.copy(pdfDpi = dpi) } },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                ToolId.IMAGES_TO_PDF -> {
                    Text("Page Size", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PdfPageSizeOption.entries.forEach { sz ->
                            FilterChip(
                                selected = options.pdfPageSize == sz,
                                onClick = { if (enabled) onUpdateOptions { it.copy(pdfPageSize = sz) } },
                                label = { Text(sz.label) }
                            )
                        }
                    }
                }

                ToolId.COMPRESS_PDF -> {
                    QualitySliderRow(
                        label = "Page JPEG Quality",
                        value = options.quality,
                        enabled = enabled,
                        onValueChange = { q -> onUpdateOptions { it.copy(quality = q) } }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(96 to "Max Shrink (96 DPI)", 130 to "Balanced (130 DPI)").forEach { (dpi, lbl) ->
                            FilterChip(
                                selected = options.pdfDpi == dpi,
                                onClick = { if (enabled) onUpdateOptions { it.copy(pdfDpi = dpi) } },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }

                ToolId.TXT_TO_PDF, ToolId.MARKDOWN_TO_PDF -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.labelLarge)
                        Text("${options.textFontSizeSp.toInt()} pt", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = options.textFontSizeSp,
                        onValueChange = { sz -> onUpdateOptions { it.copy(textFontSizeSp = sz) } },
                        valueRange = 10f..18f,
                        steps = 7,
                        enabled = enabled
                    )
                    if (tool.toolId == ToolId.TXT_TO_PDF) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Use Monospace Font", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = options.useMonospaceFont,
                                onCheckedChange = { checked ->
                                    if (enabled) onUpdateOptions { it.copy(useMonospaceFont = checked) }
                                }
                            )
                        }
                    }
                }

                ToolId.JSON_FORMAT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Minify JSON", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "Off = pretty-print with indentation, On = single-line compact",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = options.jsonMinify,
                            onCheckedChange = { checked ->
                                if (enabled) onUpdateOptions { it.copy(jsonMinify = checked) }
                            }
                        )
                    }
                }

                ToolId.WAV_TO_MP3, ToolId.AUDIO_COMPRESS -> {
                    Text("Target MP3 Bitrate", style = MaterialTheme.typography.labelLarge)
                    val choices = if (tool.toolId == ToolId.AUDIO_COMPRESS) {
                        listOf(64, 96, 128, 160)
                    } else {
                        listOf(128, 192, 256, 320)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { kbps ->
                            FilterChip(
                                selected = options.audioBitrateKbps == kbps,
                                onClick = { if (enabled) onUpdateOptions { it.copy(audioBitrateKbps = kbps) } },
                                label = { Text("${kbps}k") }
                            )
                        }
                    }
                }

                ToolId.EXTRACT_AUDIO -> {
                    OutputFormatRow(
                        options = listOf("M4A", "MP3", "WAV"),
                        selected = options.extractedAudioFormat,
                        enabled = enabled,
                        onSelect = { fmt -> onUpdateOptions { it.copy(extractedAudioFormat = fmt) } }
                    )
                }

                ToolId.AUDIO_TRIM, ToolId.VIDEO_TRIM -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = options.trimStartSec.toInt().toString(),
                            onValueChange = { v ->
                                val parsed = v.filter { it.isDigit() }.toFloatOrNull() ?: 0f
                                onUpdateOptions { it.copy(trimStartSec = parsed) }
                            },
                            label = { Text("Start (sec)") },
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = options.trimEndSec.toInt().toString(),
                            onValueChange = { v ->
                                val parsed = v.filter { it.isDigit() }.toFloatOrNull() ?: 15f
                                onUpdateOptions { it.copy(trimEndSec = parsed) }
                            },
                            label = { Text("End (sec)") },
                            singleLine = true,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                ToolId.MP4_TO_WEBM, ToolId.VIDEO_COMPRESS -> {
                    Text("Quality & Resolution Preset", style = MaterialTheme.typography.labelLarge)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoQualityPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = options.videoQualityPreset == preset,
                                onClick = { if (enabled) onUpdateOptions { it.copy(videoQualityPreset = preset) } },
                                label = { Text(preset.label) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                ToolId.VIDEO_TO_FRAMES -> {
                    Text("Frames to Capture", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 5, 10, 15).forEach { count ->
                            FilterChip(
                                selected = options.frameCount == count,
                                onClick = { if (enabled) onUpdateOptions { it.copy(frameCount = count) } },
                                label = { Text("$count frames") }
                            )
                        }
                    }
                    OutputFormatRow(
                        options = listOf("PNG", "JPG"),
                        selected = options.imageOutputFormat,
                        enabled = enabled,
                        onSelect = { fmt -> onUpdateOptions { it.copy(imageOutputFormat = fmt) } }
                    )
                }

                else -> Unit
            }
        }
    }
}

@Composable
private fun QualitySliderRow(
    label: String,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("$value%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 20f..100f,
            enabled = enabled
        )
    }
}

@Composable
private fun OutputFormatRow(
    options: List<String>,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Output Format", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { fmt ->
                FilterChip(
                    selected = selected.equals(fmt, ignoreCase = true),
                    onClick = { if (enabled) onSelect(fmt) },
                    label = { Text(fmt) }
                )
            }
        }
    }
}

@Composable
private fun ConversionSuccessCard(
    outputs: List<ConversionOutput>,
    durationMs: Long,
    onSaveOutput: (ConversionOutput) -> Unit,
    onSaveAllOutputs: (List<ConversionOutput>) -> Unit,
    onBundleZip: (List<ConversionOutput>) -> Unit,
    onShareOutput: (ConversionOutput) -> Unit,
    onShareAllOutputs: (List<ConversionOutput>) -> Unit,
    onOpenOutput: (ConversionOutput) -> Unit
) {
    val elapsedLabel = if (durationMs < 1000L) "${durationMs}ms" else "%.1fs".format(durationMs / 1000.0)
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, SuccessGreen.copy(alpha = 0.5f)),
        modifier = Modifier.testTag("conversion_success_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Success",
                        tint = SuccessGreen
                    )
                }
                Column {
                    Text(
                        text = "Conversion Complete",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${outputs.size} output file(s) ready in $elapsedLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (outputs.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionChipButton(
                        label = "Save All",
                        icon = Icons.Rounded.Download,
                        onClick = { onSaveAllOutputs(outputs) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                        testTag = "save_all_button"
                    )
                    ActionChipButton(
                        label = "Share All",
                        icon = Icons.Rounded.Share,
                        onClick = { onShareAllOutputs(outputs) },
                        modifier = Modifier.weight(1f),
                        testTag = "share_all_button"
                    )
                    ActionChipButton(
                        label = "Save ZIP",
                        icon = Icons.Rounded.FolderZip,
                        onClick = { onBundleZip(outputs) },
                        modifier = Modifier.weight(1f),
                        testTag = "bundle_zip_button"
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            outputs.forEachIndexed { idx, out ->
                val savings = FileHelper.formatSavingsLabel(out.sourceSizeBytes, out.sizeBytes)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = out.fileName,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = buildString {
                                        append(FileHelper.formatFileSize(out.sizeBytes))
                                        if (!out.extraNote.isNullOrBlank()) {
                                            append(" • ")
                                            append(out.extraNote)
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (savings.startsWith("-")) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuccessGreen.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = savings,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SuccessGreen,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActionChipButton(
                                label = "Save",
                                icon = Icons.Rounded.Download,
                                onClick = { onSaveOutput(out) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f),
                                testTag = "output_save_$idx"
                            )
                            ActionChipButton(
                                label = "Share",
                                icon = Icons.Rounded.Share,
                                onClick = { onShareOutput(out) },
                                modifier = Modifier.weight(1f),
                                testTag = "output_share_$idx"
                            )
                            ActionChipButton(
                                label = "Open",
                                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                                onClick = { onOpenOutput(out) },
                                modifier = Modifier.weight(1f),
                                testTag = "output_open_$idx"
                            )
                        }
                    }
                }
            }
        }
    }
}
