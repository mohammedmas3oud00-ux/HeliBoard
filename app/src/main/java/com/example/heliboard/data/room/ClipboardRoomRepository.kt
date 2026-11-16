package com.example.heliboard.data.room

import androidx.room.withTransaction
import com.example.heliboard.data.ClipboardEntry
import kotlinx.coroutines.flow.Flow

class ClipboardRoomRepository(
    private val database: HeliBoardDatabase,
    private val snippetDao: ClipboardSnippetDao = database.clipboardSnippetDao()
) {
    val snippets: Flow<List<ClipboardSnippetEntity>> = snippetDao.observeAllSnippets()

    val recentSnippetsByTime: Flow<List<ClipboardSnippetEntity>> = snippetDao.observeRecentByTime()

    suspend fun getAllOnce(): List<ClipboardSnippetEntity> = snippetDao.getAllSnippetsOnce()

    suspend fun upsertTextSnippet(entry: ClipboardEntry) {
        if (entry.isImage || entry.text.isBlank()) return
        snippetDao.upsertSnippet(entry.toEntity())
    }

    suspend fun syncAllTextSnippets(entries: List<ClipboardEntry>) {
        val textEntities = entries.filter { !it.isImage && it.text.isNotBlank() }.map { it.toEntity() }
        database.withTransaction {
            snippetDao.deleteAll()
            if (textEntities.isNotEmpty()) {
                snippetDao.upsertSnippets(textEntities)
            }
        }
    }

    suspend fun deleteSnippet(id: Long) {
        snippetDao.deleteById(id)
    }

    suspend fun clearUnpinned() {
        snippetDao.deleteUnpinned()
    }

    suspend fun pruneExpiredUnpinned(minTimestamp: Long) {
        snippetDao.deleteExpiredUnpinned(minTimestamp)
    }
}

fun ClipboardEntry.toEntity(): ClipboardSnippetEntity = ClipboardSnippetEntity(
    id = id,
    text = text,
    timestamp = timestamp,
    isPinned = isPinned,
    pinnedOrder = pinnedOrder
)

fun ClipboardSnippetEntity.toClipboardEntry(): ClipboardEntry = ClipboardEntry(
    id = id,
    text = text,
    timestamp = timestamp,
    isPinned = isPinned,
    pinnedOrder = pinnedOrder
)
