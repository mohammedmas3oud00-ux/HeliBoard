package com.example.heliboard.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "copied_text_snippets",
    indices = [Index(value = ["text"], unique = true)]
)
data class ClipboardSnippetEntity(
    @PrimaryKey val id: Long,
    val text: String,
    val timestamp: Long,
    val isPinned: Boolean = false,
    val pinnedOrder: Int? = null
)
