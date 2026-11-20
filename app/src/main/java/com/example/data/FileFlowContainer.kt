package com.example.data

import android.content.Context
import com.example.engine.ConversionOrchestrator

class FileFlowContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database: FileFlowDatabase = FileFlowDatabase.getInstance(appContext)
    val historyRepository: HistoryRepository = HistoryRepository(database.conversionDao())
    val themePreferences: ThemePreferences = ThemePreferences(appContext)
    val orchestrator: ConversionOrchestrator = ConversionOrchestrator(appContext, historyRepository)
}
