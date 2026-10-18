package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class OfficeDocType(val code: String, val label: String, val defaultExt: String) {
    WORD("WORD", "Microsoft Word", "docx"),
    EXCEL("EXCEL", "Microsoft Excel", "xlsx"),
    POWERPOINT("POWERPOINT", "Microsoft PowerPoint", "pptx"),
    PDF("PDF", "Tài liệu PDF", "pdf");

    companion object {
        fun fromCode(code: String): OfficeDocType =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: WORD

        fun fromExtension(ext: String): OfficeDocType {
            return when (ext.lowercase().trimStart('.')) {
                "docx", "doc", "odt", "rtf", "txt" -> WORD
                "xlsx", "xls", "csv", "ods" -> EXCEL
                "pptx", "ppt", "odp" -> POWERPOINT
                "pdf" -> PDF
                else -> WORD
            }
        }
    }
}

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val originalUri: String? = null,
    val docType: String, // WORD, EXCEL, POWERPOINT, PDF
    val extension: String, // docx, xlsx, pptx, pdf, csv, txt, odt, rtf
    val folderName: String = "Thư mục gốc",
    val sizeBytes: Long = 0L,
    val lastModified: Long = System.currentTimeMillis(),
    val lastOpened: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isOfflineAvailable: Boolean = true,
    val isInTrash: Boolean = false,
    val pageOrSheetCount: Int = 1,
    val detectedFontsCsv: String = "Times New Roman",
    val missingFontsCsv: String = "",
    val compatibilityWarnings: String = "",
    val sha256Checksum: String = "",
    val unsavedDraftPath: String? = null,
    val driveFileId: String? = null,
    val syncStatus: String = "LOCAL_ONLY" // LOCAL_ONLY, SYNCED, PENDING_UPLOAD, CONFLICT
)

@Entity(tableName = "document_versions")
data class DocumentVersionEntity(
    @PrimaryKey(autoGenerate = true) val versionId: Long = 0,
    val documentId: Long,
    val versionNumber: Int,
    val backupFilePath: String,
    val createdAt: Long = System.currentTimeMillis(),
    val changeSummary: String,
    val sha256Checksum: String,
    val sizeBytes: Long
)

@Entity(tableName = "font_records")
data class FontRecordEntity(
    @PrimaryKey(autoGenerate = true) val fontId: Long = 0,
    val familyName: String,
    val officeTargetName: String, // e.g. Times New Roman, Arial, Calibri
    val filePath: String, // "res/font/tinos.ttf" or imported local path
    val isBuiltIn: Boolean = true,
    val isFavorite: Boolean = false,
    val supportsVietnamese: Boolean = true,
    val isHealthy: Boolean = true,
    val defaultForWord: Boolean = false,
    val defaultForExcel: Boolean = false,
    val defaultForPpt: Boolean = false,
    val substitutionFor: String = "", // e.g., "Times New Roman"
    val licenseNote: String = "Apache 2.0 / SIL OFL (Hợp pháp thương mại)"
)

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val templateId: Long = 0,
    val title: String,
    val category: String, // Văn bản hành chính, Báo cáo, Bài tiểu luận, v.v.
    val docType: String, // WORD, EXCEL, POWERPOINT, PDF
    val extension: String,
    val description: String,
    val defaultFont: String,
    val pageSetupInfo: String,
    val filePath: String,
    val isCustomImported: Boolean = false
)

@Entity(tableName = "error_logs")
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val logId: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val errorCode: String,
    val category: String, // FILE_CORRUPT, FONT_MISSING, EXCEL_FORMULA, PDF_SIGNATURE, STORAGE_FULL, etc.
    val documentName: String,
    val message: String,
    val cause: String,
    val resolution: String,
    val isResolved: Boolean = false
)

@Entity(tableName = "sync_history")
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val syncId: Long = 0,
    val documentId: Long,
    val documentTitle: String,
    val driveFileId: String,
    val action: String, // UPLOAD, DOWNLOAD, CONFLICT_RESOLVED, CREATE_FOLDER
    val status: String, // SUCCESS, CONFLICT, FAILED_OFFLINE
    val timestamp: Long = System.currentTimeMillis(),
    val conflictResolution: String = ""
)
