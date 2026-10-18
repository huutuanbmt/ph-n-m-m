package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CompatibilityTestStepResult
import com.example.data.OfficeRepository
import com.example.data.local.DocumentEntity
import com.example.data.local.DocumentVersionEntity
import com.example.data.local.ErrorLogEntity
import com.example.data.local.FontRecordEntity
import com.example.data.local.OfficeDatabase
import com.example.data.local.OfficeDocType
import com.example.data.local.OfficeUserPreferences
import com.example.data.local.SyncHistoryEntity
import com.example.data.local.TemplateEntity
import com.example.data.local.UserPreferencesRepository
import com.example.data.remote.DriveRemoteFile
import com.example.engine.ExcelEngine
import com.example.engine.ExcelWorkbookModel
import com.example.engine.FileSafetyUtils
import com.example.engine.FontCompatibilityReport
import com.example.engine.FontManagerEngine
import com.example.engine.PdfDocumentState
import com.example.engine.PdfEngine
import com.example.engine.PowerPointEngine
import com.example.engine.PowerPointModel
import com.example.engine.WordDocumentModel
import com.example.engine.WordEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class HomeFilterCategory(val label: String) {
    ALL("Tất cả tài liệu"),
    WORD("Word (.docx)"),
    EXCEL("Excel (.xlsx)"),
    POWERPOINT("PowerPoint (.pptx)"),
    PDF("PDF (.pdf)"),
    RECENT("Tài liệu gần đây"),
    FAVORITES("Tài liệu yêu thích"),
    OFFLINE("Tài liệu ngoại tuyến"),
    TRASH("Thùng rác")
}

enum class SortOption(val label: String) {
    DATE_DESC("Mới sửa đổi nhất"),
    NAME_ASC("Tên (A → Z)"),
    SIZE_DESC("Dung lượng giảm dần"),
    TYPE("Theo định dạng")
}

enum class MainNavTab(val label: String) {
    DOCUMENTS("Tài liệu"),
    TEMPLATES("Kho mẫu"),
    FONTS("Font chữ"),
    CLOUD("Đám mây"),
    SETTINGS("Tùy biến & Hệ thống")
}

data class OpenEditorSession(
    val document: DocumentEntity,
    val wordModel: WordDocumentModel? = null,
    val wordUndoStack: List<WordDocumentModel> = emptyList(),
    val wordRedoStack: List<WordDocumentModel> = emptyList(),
    val excelModel: ExcelWorkbookModel? = null,
    val excelUndoStack: List<ExcelWorkbookModel> = emptyList(),
    val pptModel: PowerPointModel? = null,
    val pptUndoStack: List<PowerPointModel> = emptyList(),
    val pdfState: PdfDocumentState? = null,
    val fontReport: FontCompatibilityReport? = null,
    val hasUnsavedChanges: Boolean = false,
    val lastSavedSha256: String = ""
)

class OfficeViewModel(
    private val appContext: Context,
    val repository: OfficeRepository,
    val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val documents: StateFlow<List<DocumentEntity>> = repository.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fonts: StateFlow<List<FontRecordEntity>> = repository.allFonts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val templates: StateFlow<List<TemplateEntity>> = repository.allTemplates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val errorLogs: StateFlow<List<ErrorLogEntity>> = repository.allErrorLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val syncHistory: StateFlow<List<SyncHistoryEntity>> = repository.allSyncHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPreferences: StateFlow<OfficeUserPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OfficeUserPreferences())

    private val _currentNavTab = MutableStateFlow(MainNavTab.DOCUMENTS)
    val currentNavTab: StateFlow<MainNavTab> = _currentNavTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow(HomeFilterCategory.ALL)
    val selectedCategory: StateFlow<HomeFilterCategory> = _selectedCategory.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _customFolders = MutableStateFlow(
        listOf("Hành chính - Hợp đồng", "Tài chính - Kế toán", "Tài liệu công việc", "Học tập - Nghiên cứu")
    )
    val customFolders: StateFlow<List<String>> = _customFolders.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.DATE_DESC)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    // Multi-document open sessions & active session ID
    private val _openSessions = MutableStateFlow<List<OpenEditorSession>>(emptyList())
    val openSessions: StateFlow<List<OpenEditorSession>> = _openSessions.asStateFlow()

    private val _activeSessionDocId = MutableStateFlow<Long?>(null)
    val activeSessionDocId: StateFlow<Long?> = _activeSessionDocId.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _driveRemoteFiles = MutableStateFlow<List<DriveRemoteFile>>(emptyList())
    val driveRemoteFiles: StateFlow<List<DriveRemoteFile>> = _driveRemoteFiles.asStateFlow()

    private val _isCloudLoading = MutableStateFlow(false)
    val isCloudLoading: StateFlow<Boolean> = _isCloudLoading.asStateFlow()

    private val _compatibilityResults = MutableStateFlow<List<CompatibilityTestStepResult>>(emptyList())
    val compatibilityResults: StateFlow<List<CompatibilityTestStepResult>> = _compatibilityResults.asStateFlow()

    private val _isRunningTests = MutableStateFlow(false)
    val isRunningTests: StateFlow<Boolean> = _isRunningTests.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureInitialized()
        }
    }

    fun selectNavTab(tab: MainNavTab) {
        _currentNavTab.value = tab
    }

    fun selectCategory(category: HomeFilterCategory) {
        _selectedCategory.value = category
    }

    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    fun createNewFolder(folderName: String) {
        val clean = folderName.trim()
        if (clean.isNotEmpty() && clean !in _customFolders.value) {
            _customFolders.value = _customFolders.value + clean
            showBanner("Đã tạo thư mục '$clean'")
        }
    }

    fun updateSearchQuery(q: String) {
        _searchQuery.value = q
    }

    fun updateSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun showBanner(msg: String?) {
        _statusBannerMessage.value = msg
    }

    fun createAndOpenDocument(title: String, docType: OfficeDocType, folderName: String) {
        viewModelScope.launch {
            val doc = repository.createNewDocument(title, docType, folderName)
            openDocumentSession(doc)
            showBanner("Đã tạo và mở '${doc.title}'")
        }
    }

    fun createFromTemplateAndOpen(template: TemplateEntity) {
        viewModelScope.launch {
            val doc = repository.createDocumentFromTemplate(template)
            openDocumentSession(doc)
            showBanner("Đã tạo tài liệu từ mẫu '${template.title}'")
        }
    }

    fun importFromSafUri(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importDocumentFromUri(uri)
            result.onSuccess { doc ->
                openDocumentSession(doc)
                showBanner("Đã mở tệp '${doc.title}' từ thiết bị")
            }.onFailure { err ->
                showBanner("Lỗi mở tệp: ${err.localizedMessage}")
            }
        }
    }

    fun importCustomFontUri(uri: Uri) {
        viewModelScope.launch {
            val result = repository.importCustomFontFromUri(uri)
            result.onSuccess { font ->
                showBanner("Đã nhập font '${font.familyName}' (${if (font.supportsVietnamese) "Chuẩn Tiếng Việt" else "Thiếu dấu TV"})")
            }.onFailure { err ->
                showBanner("Lỗi nhập font: ${err.localizedMessage}")
            }
        }
    }

    fun openDocumentSession(doc: DocumentEntity) {
        val existing = _openSessions.value.find { it.document.id == doc.id }
        if (existing != null) {
            _activeSessionDocId.value = doc.id
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val file = File(doc.filePath)
            val installedFonts = fonts.value.ifEmpty { FontManagerEngine.defaultBuiltInFonts() }
            val docType = OfficeDocType.fromCode(doc.docType)

            val session = when (docType) {
                OfficeDocType.WORD -> {
                    val model = WordEngine.openDocument(file, doc.title.substringBeforeLast('.'))
                    val fontRep = FontManagerEngine.analyzeDocumentFonts(model.detectedFonts, installedFonts)
                    OpenEditorSession(
                        document = doc,
                        wordModel = model,
                        fontReport = fontRep,
                        lastSavedSha256 = FileSafetyUtils.computeSha256(file)
                    )
                }
                OfficeDocType.EXCEL -> {
                    val model = ExcelEngine.openWorkbook(file, doc.title.substringBeforeLast('.'))
                    val fontRep = FontManagerEngine.analyzeDocumentFonts(listOf("Calibri"), installedFonts)
                    OpenEditorSession(
                        document = doc,
                        excelModel = model,
                        fontReport = fontRep,
                        lastSavedSha256 = FileSafetyUtils.computeSha256(file)
                    )
                }
                OfficeDocType.POWERPOINT -> {
                    val model = PowerPointEngine.openPresentation(file, doc.title.substringBeforeLast('.'))
                    val fontRep = FontManagerEngine.analyzeDocumentFonts(model.detectedFonts, installedFonts)
                    OpenEditorSession(
                        document = doc,
                        pptModel = model,
                        fontReport = fontRep,
                        lastSavedSha256 = FileSafetyUtils.computeSha256(file)
                    )
                }
                OfficeDocType.PDF -> {
                    val pdfState = PdfEngine.inspectAndOpenPdf(file, doc.title.substringBeforeLast('.'))
                    val fontRep = FontManagerEngine.analyzeDocumentFonts(listOf("Tinos", "Roboto"), installedFonts)
                    OpenEditorSession(
                        document = doc,
                        pdfState = pdfState,
                        fontReport = fontRep,
                        lastSavedSha256 = FileSafetyUtils.computeSha256(file)
                    )
                }
            }

            repository.updateDocument(doc.copy(lastOpened = System.currentTimeMillis()))
            _openSessions.value = _openSessions.value + session
            _activeSessionDocId.value = doc.id
        }
    }

    fun switchActiveSession(docId: Long?) {
        _activeSessionDocId.value = docId
    }

    fun closeSession(docId: Long) {
        val currentList = _openSessions.value.filterNot { it.document.id == docId }
        _openSessions.value = currentList
        if (_activeSessionDocId.value == docId) {
            _activeSessionDocId.value = currentList.lastOrNull()?.document?.id
        }
    }

    // Word Editing Mutations + Undo/Redo
    fun updateWordModel(docId: Long, newModel: WordDocumentModel, triggerAutoSave: Boolean = false) {
        _openSessions.value = _openSessions.value.map { session ->
            if (session.document.id == docId && session.wordModel != null) {
                val newUndo = (session.wordUndoStack + session.wordModel).takeLast(25)
                val fontRep = FontManagerEngine.analyzeDocumentFonts(
                    newModel.paragraphs.map { it.fontFamily } + newModel.detectedFonts,
                    fonts.value
                )
                session.copy(
                    wordModel = newModel,
                    wordUndoStack = newUndo,
                    wordRedoStack = emptyList(),
                    fontReport = fontRep,
                    hasUnsavedChanges = true
                )
            } else session
        }
        if (triggerAutoSave && userPreferences.value.autoSaveEnabled) {
            saveActiveSession(docId, isAutoSave = true)
        }
    }

    fun undoWord(docId: Long) {
        _openSessions.value = _openSessions.value.map { session ->
            if (session.document.id == docId && session.wordModel != null && session.wordUndoStack.isNotEmpty()) {
                val prev = session.wordUndoStack.last()
                session.copy(
                    wordModel = prev,
                    wordUndoStack = session.wordUndoStack.dropLast(1),
                    wordRedoStack = session.wordRedoStack + session.wordModel,
                    hasUnsavedChanges = true
                )
            } else session
        }
    }

    fun redoWord(docId: Long) {
        _openSessions.value = _openSessions.value.map { session ->
            if (session.document.id == docId && session.wordModel != null && session.wordRedoStack.isNotEmpty()) {
                val next = session.wordRedoStack.last()
                session.copy(
                    wordModel = next,
                    wordUndoStack = session.wordUndoStack + session.wordModel,
                    wordRedoStack = session.wordRedoStack.dropLast(1),
                    hasUnsavedChanges = true
                )
            } else session
        }
    }

    // Excel Editing Mutations + Undo
    fun updateExcelModel(docId: Long, newModel: ExcelWorkbookModel) {
        val recalculated = ExcelEngine.recalculateWorkbook(newModel)
        _openSessions.value = _openSessions.value.map { session ->
            if (session.document.id == docId && session.excelModel != null) {
                session.copy(
                    excelModel = recalculated,
                    excelUndoStack = (session.excelUndoStack + session.excelModel).takeLast(20),
                    hasUnsavedChanges = true
                )
            } else session
        }
    }

    fun undoExcel(docId: Long) {
        _openSessions.value = _openSessions.value.map { session ->
            if (session.document.id == docId && session.excelUndoStack.isNotEmpty()) {
                session.copy(
                    excelModel = session.excelUndoStack.last(),
                    excelUndoStack = session.excelUndoStack.dropLast(1),
                    hasUnsavedChanges = true
                )
            } else session
        }
    }

    // PowerPoint Editing Mutations + Undo
    fun updatePptModel(docId: Long, newModel: PowerPointModel) {
        _openSessions.value = _openSessions.value.map { session ->
            if (session.document.id == docId && session.pptModel != null) {
                session.copy(
                    pptModel = newModel,
                    pptUndoStack = (session.pptUndoStack + session.pptModel).takeLast(20),
                    hasUnsavedChanges = true
                )
            } else session
        }
    }

    fun undoPpt(docId: Long) {
        _openSessions.value = _openSessions.value.map { session ->
            if (session.document.id == docId && session.pptUndoStack.isNotEmpty()) {
                session.copy(
                    pptModel = session.pptUndoStack.last(),
                    pptUndoStack = session.pptUndoStack.dropLast(1),
                    hasUnsavedChanges = true
                )
            } else session
        }
    }

    // PDF Editing Mutations
    fun updatePdfState(docId: Long, newState: PdfDocumentState) {
        _openSessions.value = _openSessions.value.map { session ->
            if (session.document.id == docId) {
                session.copy(pdfState = newState, hasUnsavedChanges = true)
            } else session
        }
    }

    /**
     * Performs Atomic Safe Save (Section XI): writes to temp file first, verifies integrity and SHA-256,
     * records a version backup in Room, and never overwrites the original file if an error occurs.
     */
    fun saveActiveSession(docId: Long, isAutoSave: Boolean = false, customSummary: String? = null) {
        val session = _openSessions.value.find { it.document.id == docId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val targetFile = File(session.document.filePath)
            val ext = session.document.extension
            val docType = OfficeDocType.fromCode(session.document.docType)

            val saveResult = FileSafetyUtils.atomicSaveFile(
                targetFile = targetFile,
                extension = ext,
                expectedLastSha256 = session.lastSavedSha256,
                backupDir = repository.getVersionsDir()
            ) { tempFile ->
                when (docType) {
                    OfficeDocType.WORD -> {
                        val model = session.wordModel ?: return@atomicSaveFile
                        WordEngine.writeDocxToFile(model, tempFile)
                    }
                    OfficeDocType.EXCEL -> {
                        val model = session.excelModel ?: return@atomicSaveFile
                        ExcelEngine.writeXlsxToFile(model, tempFile)
                    }
                    OfficeDocType.POWERPOINT -> {
                        val model = session.pptModel ?: return@atomicSaveFile
                        PowerPointEngine.writePptxToFile(model, tempFile)
                    }
                    OfficeDocType.PDF -> {
                        val pdfState = session.pdfState ?: return@atomicSaveFile
                        PdfEngine.savePdfWithAnnotations(targetFile, pdfState, tempFile)
                    }
                }
            }

            if (saveResult.success) {
                val summary = customSummary ?: if (isAutoSave) "Tự động lưu an toàn" else "Lưu thủ công (Đã xác minh toàn vẹn SHA-256)"
                repository.recordVersionOnSave(
                    doc = session.document,
                    backupFilePath = saveResult.backupFilePath,
                    changeSummary = summary,
                    newSha256 = saveResult.sha256,
                    newSizeBytes = saveResult.sizeBytes
                )
                _openSessions.value = _openSessions.value.map { s ->
                    if (s.document.id == docId) {
                        s.copy(
                            hasUnsavedChanges = false,
                            lastSavedSha256 = saveResult.sha256
                        )
                    } else s
                }
                if (!isAutoSave) {
                    val warn = if (saveResult.warningMessage.isNotEmpty()) " (${saveResult.warningMessage})" else ""
                    showBanner("Đã lưu '${session.document.title}' an toàn • SHA-256: ${saveResult.sha256.take(10)}$warn")
                }
            } else {
                repository.logError(
                    errorCode = "ERR_SAVE_FAILED",
                    category = "Không lưu được tài liệu",
                    documentName = session.document.title,
                    message = saveResult.errorMessage,
                    cause = "Lỗi ghi tệp tạm hoặc kiểm tra toàn vẹn thất bại.",
                    resolution = "Kiểm tra dung lượng trống và thử Lưu thành bản sao."
                )
                showBanner(saveResult.errorMessage)
            }
        }
    }

    /**
     * Saves the active document as a brand-new copy with a new name (Section XI: Lưu thành bản sao / Lưu với tên mới).
     */
    fun saveSessionAsCopy(docId: Long, newTitle: String) {
        val session = _openSessions.value.find { it.document.id == docId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ext = session.document.extension
            val cleanBase = newTitle.removeSuffix(".$ext").trim().ifEmpty { "${session.document.title.substringBeforeLast('.')}_Ban_sao" }
            val copyFile = File(repository.getDocumentsDir(), "${cleanBase.replace(" ", "_")}_${System.currentTimeMillis() % 10000}.$ext")
            val docType = OfficeDocType.fromCode(session.document.docType)

            when (docType) {
                OfficeDocType.WORD -> session.wordModel?.let { WordEngine.writeDocxToFile(it, copyFile) }
                OfficeDocType.EXCEL -> session.excelModel?.let { ExcelEngine.writeXlsxToFile(it, copyFile) }
                OfficeDocType.POWERPOINT -> session.pptModel?.let { PowerPointEngine.writePptxToFile(it, copyFile) }
                OfficeDocType.PDF -> session.pdfState?.let {
                    PdfEngine.savePdfWithAnnotations(File(session.document.filePath), it, copyFile)
                }
            }

            val sha = FileSafetyUtils.computeSha256(copyFile)
            val newDoc = session.document.copy(
                id = 0,
                title = "$cleanBase.$ext",
                filePath = copyFile.absolutePath,
                sizeBytes = copyFile.length(),
                lastModified = System.currentTimeMillis(),
                sha256Checksum = sha
            )
            repository.createNewDocument(cleanBase, docType, newDoc.folderName)
            showBanner("Đã lưu bản sao mới: '$cleanBase.$ext'")
        }
    }

    /**
     * Exports any open Word, Excel, or PowerPoint session to a real PDF document and registers it in the library!
     */
    fun exportSessionToPdf(docId: Long) {
        val session = _openSessions.value.find { it.document.id == docId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val baseName = session.document.title.substringBeforeLast('.')
            val pdfFile = File(repository.getDocumentsDir(), "${baseName.replace(" ", "_")}_Xuat_${System.currentTimeMillis() % 10000}.pdf")
            val docType = OfficeDocType.fromCode(session.document.docType)

            val ok = when (docType) {
                OfficeDocType.WORD -> session.wordModel?.let { WordEngine.exportToPdf(it, pdfFile) } ?: false
                OfficeDocType.EXCEL -> session.excelModel?.let { ExcelEngine.exportWorkbookToPdf(it, pdfFile) } ?: false
                OfficeDocType.POWERPOINT -> session.pptModel?.let { PowerPointEngine.exportPresentationToPdf(it, pdfFile) } ?: false
                OfficeDocType.PDF -> session.pdfState?.let {
                    PdfEngine.savePdfWithAnnotations(File(session.document.filePath), it, pdfFile)
                } ?: false
            }

            if (ok && pdfFile.exists()) {
                val pdfDoc = DocumentEntity(
                    title = "$baseName (Bản xuất).pdf",
                    filePath = pdfFile.absolutePath,
                    docType = OfficeDocType.PDF.code,
                    extension = "pdf",
                    folderName = session.document.folderName,
                    sizeBytes = pdfFile.length(),
                    pageOrSheetCount = 2,
                    sha256Checksum = FileSafetyUtils.computeSha256(pdfFile)
                )
                val newId = OfficeDatabase.getInstance(appContext).officeDao().insertDocument(pdfDoc)
                showBanner("Đã xuất PDF thành công: '${pdfDoc.title}'")
                openDocumentSession(pdfDoc.copy(id = newId))
            } else {
                showBanner("Không thể xuất tệp PDF.")
            }
        }
    }

    fun exportExcelSheetToCsv(docId: Long, sheetIndex: Int) {
        val session = _openSessions.value.find { it.document.id == docId } ?: return
        val sheet = session.excelModel?.sheets?.getOrNull(sheetIndex) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val baseName = session.document.title.substringBeforeLast('.')
            val csvFile = File(repository.getDocumentsDir(), "${baseName}_${sheet.name.replace(" ", "_")}.csv")
            ExcelEngine.exportSheetToCsv(sheet, csvFile)
            val csvDoc = DocumentEntity(
                title = csvFile.name,
                filePath = csvFile.absolutePath,
                docType = OfficeDocType.EXCEL.code,
                extension = "csv",
                folderName = session.document.folderName,
                sizeBytes = csvFile.length(),
                pageOrSheetCount = 1,
                sha256Checksum = FileSafetyUtils.computeSha256(csvFile)
            )
            OfficeDatabase.getInstance(appContext).officeDao().insertDocument(csvDoc)
            showBanner("Đã xuất bảng tính sang CSV: '${csvFile.name}'")
        }
    }

    fun shareDocumentFile(doc: DocumentEntity) {
        try {
            val file = File(doc.filePath)
            if (!file.exists()) {
                showBanner("Tệp không tồn tại trên bộ nhớ.")
                return
            }
            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(shareIntent, "Chia sẻ ${doc.title}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(chooser)
        } catch (e: Exception) {
            showBanner("Không thể mở hộp thoại chia sẻ: ${e.localizedMessage}")
        }
    }

    // Google Drive Cloud Operations
    fun connectGoogleAccount(email: String, accessToken: String) {
        viewModelScope.launch {
            prefsRepo.setCloudSync(enabled = true, email = email, token = accessToken)
            showBanner("Đã kết nối Google Drive cho tài khoản $email")
            refreshDriveFiles()
        }
    }

    fun disconnectGoogleDrive() {
        viewModelScope.launch {
            prefsRepo.setCloudSync(enabled = false, email = "", token = "")
            _driveRemoteFiles.value = emptyList()
            showBanner("Đã tắt đồng bộ đám mây. Mọi tài liệu được giữ an toàn ngoại tuyến trên thiết bị.")
        }
    }

    fun refreshDriveFiles(query: String = "") {
        viewModelScope.launch {
            val token = userPreferences.value.googleAccessToken
            if (token.isBlank()) return@launch
            _isCloudLoading.value = true
            val res = repository.cloudProvider.listRemoteFiles(token, query)
            _isCloudLoading.value = false
            res.onSuccess { list ->
                _driveRemoteFiles.value = list
            }.onFailure { err ->
                repository.logError(
                    errorCode = "ERR_CLOUD_SYNC",
                    category = "Mất kết nối khi đồng bộ",
                    documentName = "Google Drive",
                    message = err.localizedMessage ?: "Không thể tải danh sách tệp từ Google Drive",
                    cause = "Token hết hạn hoặc thiết bị đang ngoại tuyến.",
                    resolution = "Làm việc ngoại tuyến với bản đã tải xuống và đồng bộ lại khi có mạng."
                )
                showBanner("Chế độ ngoại tuyến: ${err.localizedMessage}")
            }
        }
    }

    fun uploadDocumentToDrive(doc: DocumentEntity) {
        viewModelScope.launch {
            val prefs = userPreferences.value
            if (!prefs.cloudSyncEnabled) {
                showBanner("Vui lòng bật Đồng bộ đám mây trong tab Đám mây trước khi tải tệp lên.")
                return@launch
            }
            _isCloudLoading.value = true
            val file = File(doc.filePath)
            val mime = when (doc.extension) {
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            val res = repository.cloudProvider.uploadFile(prefs.googleAccessToken, file, mime, doc.driveFileId)
            _isCloudLoading.value = false
            res.onSuccess { remote ->
                repository.updateDocument(doc.copy(driveFileId = remote.id, syncStatus = "SYNCED"))
                repository.recordSyncEvent(doc.id, doc.title, remote.id, "UPLOAD", "SUCCESS")
                showBanner("Đã đồng bộ '${doc.title}' lên Google Drive")
            }.onFailure {
                // Queue for offline sync & record status transparently
                repository.updateDocument(doc.copy(syncStatus = "PENDING_UPLOAD"))
                repository.recordSyncEvent(
                    doc.id,
                    doc.title,
                    doc.driveFileId ?: "pending_${doc.id}",
                    "UPLOAD",
                    "QUEUED_OFFLINE",
                    "Đã lưu hàng đợi ngoại tuyến; tự động đồng bộ khi có mạng và token hợp lệ."
                )
                showBanner("Đã lưu '${doc.title}' vào hàng đợi đồng bộ ngoại tuyến.")
            }
        }
    }

    fun resolveSyncConflict(doc: DocumentEntity, resolutionStrategy: String) {
        viewModelScope.launch {
            when (resolutionStrategy) {
                "KEEP_BOTH" -> {
                    saveSessionAsCopy(doc.id, "${doc.title.substringBeforeLast('.')}_Ban_Thiet_Bi")
                }
            }
            repository.updateDocument(doc.copy(syncStatus = "SYNCED"))
            repository.recordSyncEvent(
                docId = doc.id,
                docTitle = doc.title,
                driveFileId = doc.driveFileId ?: "drive_${doc.id}",
                action = "CONFLICT_RESOLVED",
                status = "SUCCESS",
                conflictResolution = resolutionStrategy
            )
            showBanner("Đã xử lý xung đột đồng bộ cho '${doc.title}' ($resolutionStrategy)")
        }
    }

    fun run9StepCompatibilityTests() {
        viewModelScope.launch {
            _isRunningTests.value = true
            val results = repository.runFullCompatibilityVerificationSuite()
            _compatibilityResults.value = results
            _isRunningTests.value = false
            val allPassed = results.all { it.step9Passed }
            showBanner(
                if (allPassed) "Đã hoàn tất kiểm thử 9 bước trên tài liệu thật: ĐẠT 4/4 (Word, Excel, PowerPoint, PDF)"
                else "Kiểm thử tương thích phát hiện cảnh báo. Xem chi tiết trong bảng kết quả."
            )
        }
    }

    fun exportDiagnosticReport(onReady: (File) -> Unit) {
        viewModelScope.launch {
            val file = repository.exportTechnicalDiagnosticReport()
            withContext(Dispatchers.Main) {
                onReady(file)
            }
        }
    }

    class Factory(
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val db = OfficeDatabase.getInstance(appContext)
            val repo = OfficeRepository(appContext, db.officeDao())
            val prefs = UserPreferencesRepository(appContext)
            return OfficeViewModel(appContext, repo, prefs) as T
        }
    }
}
