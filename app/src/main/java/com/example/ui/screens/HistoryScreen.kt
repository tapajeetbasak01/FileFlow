package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ConversionRecordEntity
import com.example.model.ConversionCategory
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HistoryRecordCard

@Composable
fun HistoryScreen(
    records: List<ConversionRecordEntity>,
    onSaveRecord: (ConversionRecordEntity) -> Unit,
    onShareRecord: (ConversionRecordEntity) -> Unit,
    onOpenRecord: (ConversionRecordEntity) -> Unit,
    onDeleteRecord: (ConversionRecordEntity) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }

    val filtered = remember(records, searchQuery, selectedCategoryId) {
        val q = searchQuery.trim().lowercase()
        records.filter { rec ->
            val catOk = selectedCategoryId == null || rec.categoryId == selectedCategoryId
            val queryOk = q.isEmpty() ||
                rec.outputFileName.lowercase().contains(q) ||
                rec.sourceFileName.lowercase().contains(q) ||
                rec.toolTitle.lowercase().contains(q)
            catOk && queryOk
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Conversion History",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${records.size} completed conversion(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (records.isNotEmpty()) {
                    TextButton(
                        onClick = { showClearConfirm = true },
                        modifier = Modifier.testTag("history_clear_button")
                    ) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                        Text(" ${stringResource(R.string.clear_history)}")
                    }
                }
            }

            if (records.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search converted filenames…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search history") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
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
                        .padding(horizontal = 16.dp)
                        .testTag("history_search_input")
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategoryId == null,
                            onClick = { selectedCategoryId = null },
                            label = { Text("All") }
                        )
                    }
                    items(ConversionCategory.entries, key = { it.id }) { cat ->
                        val selected = selectedCategoryId == cat.id
                        FilterChip(
                            selected = selected,
                            onClick = {
                                selectedCategoryId = if (selected) null else cat.id
                            },
                            label = { Text(cat.title) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    EmptyStateCard(
                        title = if (records.isEmpty()) "No conversions yet" else "No matching history",
                        subtitle = if (records.isEmpty()) {
                            "Converted files appear here so you can re-share, open, or save them to Downloads anytime."
                        } else {
                            "Try clearing the filter or search query."
                        },
                        icon = Icons.Rounded.History
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { record ->
                        HistoryRecordCard(
                            record = record,
                            onSave = { onSaveRecord(record) },
                            onShare = { onShareRecord(record) },
                            onOpen = { onOpenRecord(record) },
                            onDelete = { onDeleteRecord(record) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("Clear Conversion History?") },
                text = {
                    Text("This removes all history entries and cleans up temporary converted copies inside FileFlow. Files already saved to Downloads are untouched.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showClearConfirm = false
                            onClearAll()
                        }
                    ) {
                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
