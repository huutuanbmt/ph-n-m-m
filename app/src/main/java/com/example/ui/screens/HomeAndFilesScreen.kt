package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.DocumentEntity
import com.example.data.local.OfficeDocType
import com.example.ui.HomeFilterCategory
import com.example.ui.OfficeViewModel
import com.example.ui.SortOption
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.PdfCrimson
import com.example.ui.theme.PowerPointOrange
import com.example.ui.theme.WordBlue
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeAndFilesScreen(
    viewModel: OfficeViewModel,
    onRequestSafOpen: () -> Unit
) {
    val allDocs by viewModel.documents.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedFolder by viewModel.selectedFolder.collectAsState()
    val customFolders by viewModel.customFolders.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var versionModalDoc by remember { mutableStateOf<DocumentEntity?>(null) }

    val filteredDocs = remember(allDocs, selectedCategory, selectedFolder, searchQuery, sortOption) {
        allDocs.filter { doc ->
            val matchesCat = when (selectedCategory) {
                HomeFilterCategory.ALL -> !doc.isInTrash
                HomeFilterCategory.WORD -> !doc.isInTrash && doc.docType == OfficeDocType.WORD.code
                HomeFilterCategory.EXCEL -> !doc.isInTrash && doc.docType == OfficeDocType.EXCEL.code
                HomeFilterCategory.POWERPOINT -> !doc.isInTrash && doc.docType == OfficeDocType.POWERPOINT.code
                HomeFilterCategory.PDF -> !doc.isInTrash && doc.docType == OfficeDocType.PDF.code
                HomeFilterCategory.RECENT -> !doc.isInTrash && (System.currentTimeMillis() - doc.lastOpened < 7L * 24 * 3600 * 1000)
                HomeFilterCategory.FAVORITES -> !doc.isInTrash && doc.isFavorite
                HomeFilterCategory.OFFLINE -> !doc.isInTrash && doc.isOfflineAvailable
                HomeFilterCategory.TRASH -> doc.isInTrash
            }
            val matchesFolder = selectedFolder == null || doc.folderName == selectedFolder
            val q = searchQuery.trim().lowercase()
            val matchesSearch = q.isEmpty() ||
                doc.title.lowercase().contains(q) ||
                doc.extension.lowercase().contains(q) ||
                doc.folderName.lowercase().contains(q) ||
                doc.detectedFontsCsv.lowercase().contains(q)
            matchesCat && matchesFolder && matchesSearch
        }.let { list ->
            when (sortOption) {
                SortOption.DATE_DESC -> list.sortedByDescending { it.lastModified }
                SortOption.NAME_ASC -> list.sortedBy { it.title.lowercase() }
                SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
                SortOption.TYPE -> list.sortedBy { it.extension }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Hero Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_office_hero),
                        contentDescription = "Banner Văn Phòng Pro",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp),
                        contentScale = ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Văn Phòng Pro – Office 4-in-1",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Word • Excel • PowerPoint • PDF • Tác giả: Nguyễn Hữu Tuấn (0943429595)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                            OutlinedButton(
                                onClick = onRequestSafOpen,
                                modifier = Modifier.testTag("open_saf_file_button"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = "Mở tệp SAF",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Mở tệp máy", color = Color.White)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // Quick Create Row for 4-in-1 Engines
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            QuickCreateChip("+ Word (.docx)", WordBlue, "quick_create_word") {
                                viewModel.createAndOpenDocument("Văn bản Word mới", OfficeDocType.WORD, selectedFolder ?: "Tài liệu công việc")
                            }
                            QuickCreateChip("+ Excel (.xlsx)", ExcelGreen, "quick_create_excel") {
                                viewModel.createAndOpenDocument("Bảng tính Excel mới", OfficeDocType.EXCEL, selectedFolder ?: "Tài chính - Kế toán")
                            }
                            QuickCreateChip("+ PowerPoint (.pptx)", PowerPointOrange, "quick_create_ppt") {
                                viewModel.createAndOpenDocument("Bài trình chiếu mới", OfficeDocType.POWERPOINT, selectedFolder ?: "Tài liệu công việc")
                            }
                            QuickCreateChip("+ PDF (.pdf)", PdfCrimson, "quick_create_pdf") {
                                viewModel.createAndOpenDocument("Tài liệu PDF mới", OfficeDocType.PDF, selectedFolder ?: "Hành chính - Hợp đồng")
                            }
                        }
                    }
                }
            }

            // Search, Sort & Grid/List View Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("document_search_input"),
                    placeholder = { Text("Tìm tên, định dạng, font, thư mục...", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Tìm kiếm") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Box {
                    IconButton(
                        onClick = { showSortMenu = true },
                        modifier = Modifier.testTag("sort_documents_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sắp xếp tài liệu")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        SortOption.entries.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = {
                                    viewModel.updateSortOption(opt)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
                IconButton(
                    onClick = {
                        scope.launch { prefs.let { viewModel.prefsRepo.toggleGridView(!it.isGridView) } }
                    },
                    modifier = Modifier.testTag("toggle_grid_list_button")
                ) {
                    Icon(
                        imageVector = if (prefs.isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Đổi chế độ hiển thị Danh sách / Lưới"
                    )
                }
            }

            // Category Filter Chips (Section III: Tất cả, Word, Excel, PowerPoint, PDF, Gần đây, Yêu thích, Ngoại tuyến, Thùng rác)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HomeFilterCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.selectCategory(cat) },
                        label = { Text(cat.label) },
                        modifier = Modifier.testTag("category_chip_${cat.name.lowercase()}")
                    )
                }
            }

            // Folder Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AssistChip(
                    onClick = { viewModel.selectFolder(null) },
                    label = { Text(if (selectedFolder == null) "✓ Mọi thư mục" else "Mọi thư mục") },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                customFolders.forEach { folder ->
                    AssistChip(
                        onClick = { viewModel.selectFolder(if (selectedFolder == folder) null else folder) },
                        label = { Text(if (selectedFolder == folder) "✓ $folder" else folder) }
                    )
                }
                AssistChip(
                    onClick = { showNewFolderDialog = true },
                    label = { Text("+ Thư mục") },
                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("create_folder_chip")
                )
                if (selectedCategory == HomeFilterCategory.TRASH && filteredDocs.isNotEmpty()) {
                    TextButton(
                        onClick = { scope.launch { viewModel.repository.emptyTrash() } },
                        modifier = Modifier.testTag("empty_trash_button")
                    ) {
                        Text("Dọn sạch thùng rác", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Document List or Grid
            if (filteredDocs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Không có tài liệu nào trong mục '${selectedCategory.label}'",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Nhấn nút (+) góc dưới để tạo mới Word, Excel, PowerPoint, PDF hoặc mở tệp từ bộ nhớ.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (prefs.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 165.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentCardItem(
                            doc = doc,
                            isGrid = true,
                            onOpen = { viewModel.openDocumentSession(doc) },
                            onToggleFav = { scope.launch { viewModel.repository.toggleFavorite(doc) } },
                            onShowVersions = { versionModalDoc = doc },
                            onShare = { viewModel.shareDocumentFile(doc) },
                            onUploadDrive = { viewModel.uploadDocumentToDrive(doc) },
                            onSaveAsTemplate = {
                                scope.launch {
                                    viewModel.repository.saveDocumentAsTemplate(doc, doc.folderName)
                                    viewModel.showBanner("Đã lưu '${doc.title}' vào Kho mẫu tài liệu")
                                }
                            },
                            onTrashOrRestore = {
                                scope.launch {
                                    if (doc.isInTrash) viewModel.repository.restoreFromTrash(doc)
                                    else viewModel.repository.moveToTrash(doc)
                                }
                            },
                            onDeletePermanent = {
                                scope.launch { viewModel.repository.deletePermanently(doc) }
                            }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        DocumentCardItem(
                            doc = doc,
                            isGrid = false,
                            onOpen = { viewModel.openDocumentSession(doc) },
                            onToggleFav = { scope.launch { viewModel.repository.toggleFavorite(doc) } },
                            onShowVersions = { versionModalDoc = doc },
                            onShare = { viewModel.shareDocumentFile(doc) },
                            onUploadDrive = { viewModel.uploadDocumentToDrive(doc) },
                            onSaveAsTemplate = {
                                scope.launch {
                                    viewModel.repository.saveDocumentAsTemplate(doc, doc.folderName)
                                    viewModel.showBanner("Đã lưu '${doc.title}' vào Kho mẫu tài liệu")
                                }
                            },
                            onTrashOrRestore = {
                                scope.launch {
                                    if (doc.isInTrash) viewModel.repository.restoreFromTrash(doc)
                                    else viewModel.repository.moveToTrash(doc)
                                }
                            },
                            onDeletePermanent = {
                                scope.launch { viewModel.repository.deletePermanently(doc) }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // FAB (+) Create New Document or Folder
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_create_new_document"),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tạo tài liệu mới", tint = Color.White)
        }
    }

    if (showCreateDialog) {
        CreateNewDocumentDialog(
            folders = customFolders,
            onDismiss = { showCreateDialog = false },
            onCreateDocument = { title, type, folder ->
                showCreateDialog = false
                viewModel.createAndOpenDocument(title, type, folder)
            },
            onOpenSaf = {
                showCreateDialog = false
                onRequestSafOpen()
            }
        )
    }

    if (showNewFolderDialog) {
        var newFolderText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Tạo thư mục tài liệu mới") },
            text = {
                OutlinedTextField(
                    value = newFolderText,
                    onValueChange = { newFolderText = it },
                    label = { Text("Tên thư mục") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createNewFolder(newFolderText)
                    showNewFolderDialog = false
                }) {
                    Text("Tạo thư mục")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Hủy") }
            }
        )
    }

    versionModalDoc?.let { doc ->
        VersionHistoryDialog(
            doc = doc,
            viewModel = viewModel,
            onDismiss = { versionModalDoc = null }
        )
    }
}

@Composable
private fun QuickCreateChip(
    label: String,
    color: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = color,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun DocumentCardItem(
    doc: DocumentEntity,
    isGrid: Boolean,
    onOpen: () -> Unit,
    onToggleFav: () -> Unit,
    onShowVersions: () -> Unit,
    onShare: () -> Unit,
    onUploadDrive: () -> Unit,
    onSaveAsTemplate: () -> Unit,
    onTrashOrRestore: () -> Unit,
    onDeletePermanent: () -> Unit
) {
    val docType = OfficeDocType.fromCode(doc.docType)
    val badgeColor = when (docType) {
        OfficeDocType.WORD -> WordBlue
        OfficeDocType.EXCEL -> ExcelGreen
        OfficeDocType.POWERPOINT -> PowerPointOrange
        OfficeDocType.PDF -> PdfCrimson
    }
    val badgeIcon = when (docType) {
        OfficeDocType.WORD -> Icons.Default.Description
        OfficeDocType.EXCEL -> Icons.Default.TableChart
        OfficeDocType.POWERPOINT -> Icons.Default.Slideshow
        OfficeDocType.PDF -> Icons.Default.PictureAsPdf
    }
    val dateStr = remember(doc.lastModified) {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(doc.lastModified))
    }
    val sizeKb = maxOf(1L, doc.sizeBytes / 1024L)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("doc_card_${doc.id}"),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(badgeIcon, contentDescription = docType.label, tint = badgeColor, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (isGrid) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${doc.extension.uppercase()} • ${sizeKb} KB • ${doc.folderName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Font: ${doc.detectedFontsCsv} • Sửa: $dateStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleFav) {
                    Icon(
                        imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Yêu thích",
                        tint = if (doc.isFavorite) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (doc.compatibilityWarnings.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠ ${doc.compatibilityWarnings}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF92400E),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = "SHA-256",
                        tint = ExcelGreen,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (doc.isOfflineAvailable) "Ngoại tuyến sẵn sàng" else "Đám mây",
                        style = MaterialTheme.typography.labelSmall,
                        color = ExcelGreen
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = onShowVersions, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.History, contentDescription = "Lịch sử phiên bản", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onUploadDrive, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Đồng bộ Google Drive", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onShare, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Chia sẻ tệp", modifier = Modifier.size(18.dp))
                    }
                    if (!isGrid) {
                        TextButton(onClick = onSaveAsTemplate) {
                            Text("Lưu mẫu", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = onTrashOrRestore, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = if (doc.isInTrash) Icons.Default.RestoreFromTrash else Icons.Default.Delete,
                            contentDescription = if (doc.isInTrash) "Khôi phục" else "Chuyển vào thùng rác",
                            tint = if (doc.isInTrash) ExcelGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (doc.isInTrash) {
                        TextButton(onClick = onDeletePermanent) {
                            Text("Xóa hẳn", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateNewDocumentDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onCreateDocument: (String, OfficeDocType, String) -> Unit,
    onOpenSaf: () -> Unit
) {
    var title by remember { mutableStateOf("Tài liệu mới") }
    var selectedType by remember { mutableStateOf(OfficeDocType.WORD) }
    var selectedFolder by remember { mutableStateOf(folders.firstOrNull() ?: "Tài liệu công việc") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tạo mới hoặc Mở tài liệu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên tài liệu") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_doc_title_input")
                )
                Text("Chọn bộ máy định dạng:", style = MaterialTheme.typography.labelLarge)
                OfficeDocType.entries.forEach { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selectedType == t) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { selectedType = t }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${t.label} (.${t.defaultExt})",
                            fontWeight = if (selectedType == t) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenSaf,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Mở từ Quản lý tệp / USB OTG (SAF)")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreateDocument(title, selectedType, selectedFolder) },
                modifier = Modifier.testTag("confirm_create_doc_button")
            ) {
                Text("Tạo & Mở ngay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

@Composable
private fun VersionHistoryDialog(
    doc: DocumentEntity,
    viewModel: OfficeViewModel,
    onDismiss: () -> Unit
) {
    val versions by viewModel.repository.getVersionsForDocument(doc.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lịch sử Phiên bản & Sao lưu An toàn") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tài liệu: ${doc.title}\nMã toàn vẹn hiện tại (SHA-256): ${doc.sha256Checksum.take(16)}...",
                    style = MaterialTheme.typography.bodySmall
                )
                if (versions.isEmpty()) {
                    Text("Chưa có phiên bản sao lưu nào.")
                } else {
                    versions.take(6).forEach { ver ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Phiên bản #${ver.versionNumber} — ${ver.changeSummary}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "SHA-256: ${ver.sha256Checksum.take(12)} • ${ver.sizeBytes / 1024} KB",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val ok = viewModel.repository.restoreDocumentVersion(doc, ver)
                                            viewModel.showBanner(
                                                if (ok) "Đã khôi phục về Phiên bản #${ver.versionNumber}"
                                                else "Không thể khôi phục phiên bản."
                                            )
                                            onDismiss()
                                        }
                                    }
                                ) {
                                    Text("Khôi phục")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}
