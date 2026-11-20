package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversion_history")
data class ConversionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val toolId: String,
    val toolTitle: String,
    val categoryId: String,
    val sourceFileName: String,
    val sourceFormat: String,
    val sourceSizeBytes: Long,
    val outputFileName: String,
    val outputFilePath: String,
    val outputFormat: String,
    val outputMimeType: String,
    val outputSizeBytes: Long,
    val durationMs: Long,
    val timestampMs: Long = System.currentTimeMillis()
)
