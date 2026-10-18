package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficeDao {
    // Documents
    @Query("SELECT * FROM documents ORDER BY lastModified DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity): Long

    @Update
    suspend fun updateDocument(doc: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentPermanently(id: Long)

    @Query("DELETE FROM documents WHERE isInTrash = 1")
    suspend fun emptyTrash()

    // Document Versions
    @Query("SELECT * FROM document_versions WHERE documentId = :docId ORDER BY versionNumber DESC")
    fun getVersionsForDocument(docId: Long): Flow<List<DocumentVersionEntity>>

    @Query("SELECT * FROM document_versions WHERE documentId = :docId ORDER BY versionNumber DESC")
    suspend fun getVersionsForDocumentList(docId: Long): List<DocumentVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: DocumentVersionEntity): Long

    // Fonts
    @Query("SELECT * FROM font_records ORDER BY isFavorite DESC, familyName ASC")
    fun getAllFonts(): Flow<List<FontRecordEntity>>

    @Query("SELECT * FROM font_records")
    suspend fun getAllFontsList(): List<FontRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFont(font: FontRecordEntity): Long

    @Update
    suspend fun updateFont(font: FontRecordEntity)

    @Query("UPDATE font_records SET defaultForWord = 0")
    suspend fun clearDefaultWordFont()

    @Query("UPDATE font_records SET defaultForExcel = 0")
    suspend fun clearDefaultExcelFont()

    @Query("UPDATE font_records SET defaultForPpt = 0")
    suspend fun clearDefaultPptFont()

    // Templates
    @Query("SELECT * FROM templates ORDER BY isCustomImported DESC, templateId ASC")
    fun getAllTemplates(): Flow<List<TemplateEntity>>

    @Query("SELECT COUNT(*) FROM templates")
    suspend fun getTemplateCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: TemplateEntity): Long

    // Error Logs
    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC")
    fun getAllErrorLogs(): Flow<List<ErrorLogEntity>>

    @Query("SELECT * FROM error_logs ORDER BY timestamp DESC")
    suspend fun getAllErrorLogsList(): List<ErrorLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertErrorLog(log: ErrorLogEntity): Long

    @Query("UPDATE error_logs SET isResolved = 1 WHERE logId = :logId")
    suspend fun markErrorResolved(logId: Long)

    @Query("DELETE FROM error_logs")
    suspend fun clearErrorLogs()

    // Sync History
    @Query("SELECT * FROM sync_history ORDER BY timestamp DESC")
    fun getAllSyncHistory(): Flow<List<SyncHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncHistory(entry: SyncHistoryEntity): Long
}
