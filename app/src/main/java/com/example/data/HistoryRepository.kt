package com.example.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class HistoryRepository(private val dao: ConversionDao) {
    val allHistory: Flow<List<ConversionRecordEntity>> = dao.observeAll()

    fun recentHistory(limit: Int = 6): Flow<List<ConversionRecordEntity>> =
        dao.observeRecent(limit)

    suspend fun recordConversions(records: List<ConversionRecordEntity>) {
        if (records.isNotEmpty()) {
            dao.insertAll(records)
        }
    }

    suspend fun deleteRecord(record: ConversionRecordEntity) {
        dao.deleteById(record.id)
        runCatching {
            val file = File(record.outputFilePath)
            if (file.exists()) file.delete()
        }
    }

    suspend fun clearAll(convertedDir: File) {
        dao.clearAll()
        runCatching {
            if (convertedDir.exists()) {
                convertedDir.listFiles()?.forEach { it.delete() }
            }
        }
    }
}
