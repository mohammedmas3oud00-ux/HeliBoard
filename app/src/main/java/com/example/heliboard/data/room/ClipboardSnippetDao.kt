package com.example.heliboard.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardSnippetDao {
    @Query(
        "SELECT * FROM copied_text_snippets " +
            "ORDER BY isPinned DESC, " +
            "CASE WHEN pinnedOrder IS NULL THEN 2147483647 ELSE pinnedOrder END ASC, " +
            "timestamp DESC"
    )
    fun observeAllSnippets(): Flow<List<ClipboardSnippetEntity>>

    @Query("SELECT * FROM copied_text_snippets ORDER BY timestamp DESC")
    fun observeRecentByTime(): Flow<List<ClipboardSnippetEntity>>

    @Query(
        "SELECT * FROM copied_text_snippets " +
            "ORDER BY isPinned DESC, " +
            "CASE WHEN pinnedOrder IS NULL THEN 2147483647 ELSE pinnedOrder END ASC, " +
            "timestamp DESC"
    )
    suspend fun getAllSnippetsOnce(): List<ClipboardSnippetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnippet(snippet: ClipboardSnippetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSnippets(snippets: List<ClipboardSnippetEntity>)

    @Query("DELETE FROM copied_text_snippets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM copied_text_snippets WHERE isPinned = 0")
    suspend fun deleteUnpinned()

    @Query("DELETE FROM copied_text_snippets WHERE isPinned = 0 AND timestamp < :minTimestamp")
    suspend fun deleteExpiredUnpinned(minTimestamp: Long)

    @Query("DELETE FROM copied_text_snippets")
    suspend fun deleteAll()
}
