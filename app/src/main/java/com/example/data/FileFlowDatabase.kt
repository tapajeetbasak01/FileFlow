package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ConversionRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FileFlowDatabase : RoomDatabase() {
    abstract fun conversionDao(): ConversionDao

    companion object {
        @Volatile
        private var instance: FileFlowDatabase? = null

        fun getInstance(context: Context): FileFlowDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FileFlowDatabase::class.java,
                    "fileflow_history.db"
                ).build().also { instance = it }
            }
    }
}
