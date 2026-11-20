package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OfflineBolt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ConversionRecordEntity
import com.example.engine.FileHelper
import com.example.model.ConversionCategory
import com.example.model.ConversionTool
import com.example.model.ThemeMode
import com.example.model.ToolId
import com.example.model.ToolRegistry
import com.example.ui.components.ConversionCategoryCard
import com.example.ui.components.ConversionToolCard
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.FileFlowLogoMark
import com.example.ui.components.HistoryRecordCard
import com.example.ui.components.ThemeTogglePill
import com.example.ui.components.categoryIcon
import com.example.ui.theme.StudioCobaltDeep
import com.example.ui.theme.StudioMint
import com.example.ui.viewmodel.QuickPickSuggestionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    themeMode: ThemeMode,
    recentHistory: List<ConversionRecordEntity>,
    quickPickState: QuickPickSuggestionState?,
    onToggleTheme: () -> Unit,
    onFilesPicked: (List<android.net.Uri>) -> Unit,
    onDismissQuickPick: () -> Unit,
    onSelectSuggestedTool: (ConversionTool) -> Unit,
    onCategoryClick: (ConversionCategory) -> Unit,
    onToolClick: (ConversionTool) -> Unit,
    onSeeAllHistory: () -> Unit,
    onSeeAllTools: () -> Unit,
    onSaveRecord: (ConversionRecordEntity) -> Unit,
    onShareRecord: (ConversionRecordEntity) -> Unit,
    onOpenRecord: (ConversionRecordEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val safMultiPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onFilesPicked(uris)
    }

    val searchResults = remember(searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else ToolRegistry.allTools.filter { tool ->
            tool.title.lowercase().contains(q) ||
                tool.description.lowercase().contains(q) ||
                tool.inputLabel.lowercase().contains(q) ||
                tool.outputLabel.lowercase().contains(q) ||
                tool.category.title.lowercase().contains(q)
        }
    }

    val quickFeaturedTools = remember {
        listOf(
            ToolId.PNG_TO_JPG,
            ToolId.MERGE_PDF,
            ToolId.MP4_TO_WEBM,
            ToolId.MP3_TO_WAV,
            ToolId.IMAGES_TO_PDF,
            ToolId.CREATE_ZIP
        ).map { ToolRegistry.getTool(it) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // App Logo + Name + Offline Badge + Theme Toggle
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FileFlowLogoMark()
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OfflineBolt,
                                    contentDescription = null,
                                    tint = StudioMint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.offline_badge),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    ThemeTogglePill(
                        themeMode = themeMode,
                        onToggle = onToggleTheme
                    )
                }
            }

            // Hero Studio Card with Generated Art + Primary File Selection CTA
            item {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 4.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(216.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_flow),
                            contentDescription = "FileFlow Studio hero artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            StudioCobaltDeep.copy(alpha = 0.92f),
                                            StudioCobaltDeep.copy(alpha = 0.78f),
                                            Color(0xFF0A2E4D).copy(alpha = 0.65f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = "${ToolRegistry.allTools.size} NATIVE TOOLS • ZERO CLOUD UPLOAD",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.hero_title),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                                Text(
                                    text = "Pick any file to auto-match converters, or browse studio categories below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 2
                                )
                            }

                            Button(
                                onClick = { safMultiPicker.launch(arrayOf("*/*")) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = StudioCobaltDeep
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("home_select_files_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.select_files_cta),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            // Search Conversion Tools Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_tools_placeholder),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search tools"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_input")
                )
            }

            // Live Search Results (when user types)
            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "Matching Tools (${searchResults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (searchResults.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No matching conversion tools",
                            subtitle = "Try keywords like PNG, PDF, WEBP, MP3, WAV, WEBM, CSV, or ZIP.",
                            icon = Icons.Rounded.Search
                        )
                    }
                } else {
                    items(searchResults, key = { it.toolId.id }) { tool ->
                        ConversionToolCard(
                            tool = tool,
                            onClick = { onToolClick(tool) }
                        )
                    }
                }
            } else {
                // Quick Popular Conversions Strip
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick Studio Shortcuts",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(onClick = onSeeAllTools) {
                                Text("All ${ToolRegistry.allTools.size} Tools")
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(quickFeaturedTools, key = { it.toolId.id }) { tool ->
                                Surface(
                                    onClick = { onToolClick(tool) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.testTag("shortcut_${tool.toolId.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(tool.category.accentColor.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = categoryIcon(tool.category),
                                                contentDescription = null,
                                                tint = tool.category.accentColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = tool.shortTitle,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = tool.category.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Conversion Categories 2-Column Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.categories_heading),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        val categories = ConversionCategory.entries
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            categories.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    pair.forEach { category ->
                                        val count = ToolRegistry.toolsForCategory(category).size
                                        ConversionCategoryCard(
                                            category = category,
                                            toolCount = count,
                                            onClick = { onCategoryClick(category) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (pair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent Conversions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.recent_conversions_heading),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (recentHistory.isNotEmpty()) {
                            TextButton(onClick = onSeeAllHistory) {
                                Text("View All")
                            }
                        }
                    }
                }

                if (recentHistory.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No recent conversions",
                            subtitle = stringResource(R.string.no_recent_conversions),
                            icon = Icons.Rounded.History
                        )
                    }
                } else {
                    items(recentHistory, key = { it.id }) { record ->
                        HistoryRecordCard(
                            record = record,
                            onSave = { onSaveRecord(record) },
                            onShare = { onShareRecord(record) },
                            onOpen = { onOpenRecord(record) }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        // Smart Suggested Conversions Bottom Sheet when files are picked from Home
        AnimatedVisibility(
            visible = quickPickState != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            if (quickPickState != null) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = onDismissQuickPick,
                    sheetState = sheetState
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val count = quickPickState.files.size
                        val first = quickPickState.files.first()
                        Text(
                            text = if (count == 1) first.displayName else "$count files selected",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FileHelper.formatFileSize(quickPickState.files.sumOf { it.sizeBytes })} • Choose a recommended conversion:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyColumn(
                            modifier = Modifier.height(340.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(quickPickState.suggestedTools, key = { it.toolId.id }) { tool ->
                                ConversionToolCard(
                                    tool = tool,
                                    onClick = { onSelectSuggestedTool(tool) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
