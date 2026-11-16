package com.example.heliboard.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClipboardSnippetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class HeliBoardDatabase : RoomDatabase() {
    abstract fun clipboardSnippetDao(): ClipboardSnippetDao

    companion object {
        @Volatile
        private var INSTANCE: HeliBoardDatabase? = null

        @Volatile
        private var INSTANCE_APP_CONTEXT: Context? = null

        fun getInstance(context: Context): HeliBoardDatabase {
            val appContext = context.applicationContext ?: context
            val current = INSTANCE
            if (current != null && INSTANCE_APP_CONTEXT === appContext && current.isOpen) {
                return current
            }
            return synchronized(this) {
                val again = INSTANCE
                if (again != null && INSTANCE_APP_CONTEXT === appContext && again.isOpen) {
                    again
                } else {
                    if (again != null && INSTANCE_APP_CONTEXT !== appContext) {
                        runCatching { again.close() }
                    }
                    Room.databaseBuilder(
                        appContext,
                        HeliBoardDatabase::class.java,
                        "heliboard_clipboard.db"
                    ).build().also {
                        INSTANCE = it
                        INSTANCE_APP_CONTEXT = appContext
                    }
                }
            }
        }
    }
}
