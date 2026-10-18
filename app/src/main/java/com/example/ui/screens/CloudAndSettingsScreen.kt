package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.ui.OfficeViewModel
import com.example.ui.theme.DeviceFormFactorMode
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.IconScaleMode
import com.example.ui.theme.OfficeUiStyle
import com.example.ui.theme.PdfCrimson
import com.example.ui.theme.PowerPointOrange
import com.example.ui.theme.ThemeAccentColor
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.ToolbarPositionMode
import com.example.ui.theme.WordBlue
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

@Composable
fun CloudSyncScreen(viewModel: OfficeViewModel) {
    val prefs by viewModel.userPreferences.collectAsState()
    val docs by viewModel.documents.collectAsState()
    val syncHistory by viewModel.syncHistory.collectAsState()
    val remoteFiles by viewModel.driveRemoteFiles.collectAsState()
    val isLoading by viewModel.isCloudLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var manualEmail by remember { mutableStateOf(prefs.connectedGoogleEmail.ifEmpty { "nguyenhuutuanbmt@gmail.com" }) }
    var manualAccessToken by remember { mutableStateOf(prefs.googleAccessToken) }
    var driveSearchQuery by remember { mutableStateOf("") }
    var newDriveFolderName by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (prefs.cloudSyncEnabled) Icons.Default.CloudDone else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (prefs.cloudSyncEnabled) ExcelGreen else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Đồng bộ Đám mây Google Drive (OAuth 2.0)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (prefs.cloudSyncEnabled) "Đang bật theo chủ đích người dùng (${prefs.connectedGoogleEmail})"
                                    else "Đang tắt (Mặc định bảo mật ngoại tuyến — Không tự động tải tệp lên Internet)",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Switch(
                            checked = prefs.cloudSyncEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    viewModel.connectGoogleAccount(manualEmail, manualAccessToken)
                                } else {
                                    viewModel.disconnectGoogleDrive()
                                }
                            },
                            modifier = Modifier.testTag("cloud_sync_master_switch")
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = manualEmail,
                        onValueChange = { manualEmail = it },
                        label = { Text("Tài khoản Google kết nối") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualAccessToken,
                        onValueChange = { manualAccessToken = it },
                        label = { Text("OAuth 2.0 Access Token (Phạm vi drive.file & drive.readonly)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val credentialManager = CredentialManager.create(context)
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId("329762627946.apps.googleusercontent.com")
                                            .build()
                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()
                                        val result = credentialManager.getCredential(context, request)
                                        val cred = GoogleIdTokenCredential.createFrom(result.credential.data)
                                        manualEmail = cred.id
                                        viewModel.connectGoogleAccount(cred.id, cred.idToken)
                                    } catch (e: Exception) {
                                        viewModel.connectGoogleAccount(manualEmail, manualAccessToken)
                                    }
                                }
                            },
                            modifier = Modifier.testTag("google_sign_in_button")
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Đăng nhập Google OAuth")
                        }
                        if (prefs.cloudSyncEnabled) {
                            OutlinedButton(onClick = { viewModel.refreshDriveFiles(driveSearchQuery) }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Làm mới Drive")
                            }
                        }
                    }
                }
            }
        }

        // Create Folder & Search on Google Drive
        if (prefs.cloudSyncEnabled) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quản lý Thư mục & Tìm kiếm trên Google Drive", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newDriveFolderName,
                                onValueChange = { newDriveFolderName = it },
                                placeholder = { Text("Tên thư mục Drive mới...") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(onClick = {
                                if (newDriveFolderName.isNotBlank()) {
                                    scope.launch {
                                        val res = viewModel.repository.cloudProvider.createFolder(
                                            prefs.googleAccessToken,
                                            newDriveFolderName
                                        )
                                        res.onSuccess { folder ->
                                            viewModel.repository.recordSyncEvent(0, folder.name, folder.id, "CREATE_FOLDER", "SUCCESS")
                                            viewModel.showBanner("Đã tạo thư mục '${folder.name}' trên Google Drive")
                                        }.onFailure {
                                            viewModel.repository.recordSyncEvent(0, newDriveFolderName, "local_folder", "CREATE_FOLDER", "QUEUED_OFFLINE")
                                            viewModel.showBanner("Đã ghi nhận tạo thư mục '$newDriveFolderName' (Đồng bộ khi trực tuyến)")
                                        }
                                        newDriveFolderName = ""
                                    }
                                }
                            }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tạo")
                            }
                        }

                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        } else if (remoteFiles.isNotEmpty()) {
                            remoteFiles.forEach { rf ->
                                Text("☁ ${rf.name} (${rf.mimeType})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // Local Documents Sync & Conflict Resolution Section
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Đồng bộ Tài liệu & Xử lý Xung đột Đa thiết bị",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Làm việc ngoại tuyến 100% với các bản trên máy và chủ động đồng bộ hoặc phân giải xung đột.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    docs.filter { !it.isInTrash }.forEach { doc ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(doc.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "Trạng thái: ${doc.syncStatus} • SHA-256: ${doc.sha256Checksum.take(10)}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AssistChip(
                                        onClick = { viewModel.uploadDocumentToDrive(doc) },
                                        label = { Text("Tải lên Drive") },
                                        leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    )
                                    AssistChip(
                                        onClick = { viewModel.resolveSyncConflict(doc, "KEEP_BOTH") },
                                        label = { Text("Giữ cả 2 bản") }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sync History Log
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Lịch sử Đồng bộ Đám mây", fontWeight = FontWeight.Bold)
                    if (syncHistory.isEmpty()) {
                        Text("Chưa có phiên đồng bộ nào được kích hoạt.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        syncHistory.take(8).forEach { item ->
                            Text(
                                text = "• [${item.action}] ${item.documentTitle} → ${item.status} ${if (item.conflictResolution.isNotEmpty()) "(${item.conflictResolution})" else ""}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAndDiagnosticsScreen(viewModel: OfficeViewModel) {
    val prefs by viewModel.userPreferences.collectAsState()
    val compatResults by viewModel.compatibilityResults.collectAsState()
    val isRunningTests by viewModel.isRunningTests.collectAsState()
    val errorLogs by viewModel.errorLogs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. UI Modes & Personalization (Section IX)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Tùy biến Giao diện & Thanh công cụ (7 Chế độ)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text("Chế độ bố cục giao diện:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OfficeUiStyle.entries.forEach { style ->
                            FilterChip(
                                selected = prefs.uiStyle == style,
                                onClick = { scope.launch { viewModel.prefsRepo.updateUiStyle(style) } },
                                label = { Text(style.label) },
                                modifier = Modifier.testTag("ui_style_${style.name.lowercase()}")
                            )
                        }
                    }

                    Text("Chế độ thiết bị (Điện thoại / Máy tính bảng):", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeviceFormFactorMode.entries.forEach { mode ->
                            FilterChip(
                                selected = prefs.formFactorMode == mode,
                                onClick = { scope.launch { viewModel.prefsRepo.updateFormFactor(mode) } },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Text("Chế độ Sáng / Tối:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeBrightnessMode.entries.forEach { b ->
                            FilterChip(
                                selected = prefs.brightnessMode == b,
                                onClick = { scope.launch { viewModel.prefsRepo.updateBrightness(b) } },
                                label = { Text(b.label) },
                                modifier = Modifier.testTag("brightness_${b.name.lowercase()}")
                            )
                        }
                    }

                    Text("Màu chủ đề (Theme Accent):", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeAccentColor.entries.forEach { colorPreset ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (prefs.accentColor == colorPreset) colorPreset.primaryLight.copy(alpha = 0.2f)
                                        else Color.Transparent
                                    )
                                    .clickable { scope.launch { viewModel.prefsRepo.updateAccentColor(colorPreset) } }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(colorPreset.primaryLight)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(colorPreset.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text("Vị trí Thanh công cụ & Kích thước Biểu tượng:", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ToolbarPositionMode.entries.forEach { pos ->
                            FilterChip(
                                selected = prefs.toolbarPosition == pos,
                                onClick = { scope.launch { viewModel.prefsRepo.updateToolbarPosition(pos) } },
                                label = { Text(pos.label) }
                            )
                        }
                        IconScaleMode.entries.forEach { sc ->
                            FilterChip(
                                selected = prefs.iconScale == sc,
                                onClick = { scope.launch { viewModel.prefsRepo.updateIconScale(sc) } },
                                label = { Text(sc.label) }
                            )
                        }
                    }

                    HorizontalDivider()
                    SettingToggleRow(
                        title = "Tự động lưu an toàn (Atomic Auto-Save)",
                        subtitle = "Ghi qua tệp tạm và kiểm tra SHA-256 trước khi cập nhật tệp chính",
                        checked = prefs.autoSaveEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefsRepo.setAutoSave(it) } }
                    )
                    SettingToggleRow(
                        title = "Hỗ trợ bút cảm ứng (Stylus) & Phím tắt bàn phím rời",
                        subtitle = "Hỗ trợ Ctrl+S (Lưu), Ctrl+Z (Hoàn tác), Ctrl+B (Đậm), Ctrl+F (Tìm)",
                        checked = prefs.stylusPressureEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefsRepo.setStylusPressure(it) } }
                    )
                    SettingToggleRow(
                        title = "Khóa bảo mật ứng dụng (Biometric / PIN)",
                        subtitle = "Bảo vệ tài liệu nhạy cảm và tự động dọn tệp tạm khi đóng",
                        checked = prefs.biometricLockEnabled,
                        onCheckedChange = { scope.launch { viewModel.prefsRepo.setBiometricLock(it) } }
                    )
                }
            }
        }

        // 2. Automated 9-Step Document Compatibility Test Suite (Section XIV)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "2. Hệ thống Kiểm thử Tương thích Tài liệu Thực tế (9 bước)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Kiểm định mở tệp gốc → sửa nội dung → lưu bản sao → mở lại & đối chiếu font, bảng, công thức, chữ ký.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = { viewModel.run9StepCompatibilityTests() },
                            enabled = !isRunningTests,
                            modifier = Modifier.testTag("run_compatibility_suite_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isRunningTests) "Đang kiểm thử..." else "Chạy Kiểm thử")
                        }
                    }

                    if (compatResults.isNotEmpty()) {
                        compatResults.forEach { res ->
                            Surface(
                                color = if (res.step9Passed) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = "${if (res.step9Passed) "✓ ĐẠT CHUẨN" else "✗ CẢNH BÁO"} — ${res.docType} (${res.documentName})",
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.step9Passed) Color(0xFF065F46) else Color(0xFF991B1B)
                                    )
                                    Text(
                                        text = res.verifiedDetails,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "SHA-256 gốc: ${res.originalSha256} → SHA-256 bản sao: ${res.savedCopySha256}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Error Management & Privacy-Safe Technical Diagnostic Export (Section XV)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "3. Quản lý Lỗi & Xuất Báo cáo Kỹ thuật (${errorLogs.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Không tự động đính kèm nội dung tài liệu cá nhân vào báo cáo lỗi.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.exportDiagnosticReport { reportFile ->
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            reportFile
                                        )
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Xuất báo cáo kỹ thuật"))
                                    } catch (_: Exception) {
                                        viewModel.showBanner("Đã lưu báo cáo kỹ thuật tại: ${reportFile.name}")
                                    }
                                }
                            },
                            modifier = Modifier.testTag("export_diagnostic_report_button")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Xuất Báo cáo")
                        }
                    }

                    if (errorLogs.isEmpty()) {
                        Text(
                            text = "✓ Không có lỗi hệ thống hay lỗi tương thích nào phát sinh.",
                            color = ExcelGreen,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        errorLogs.take(5).forEach { log ->
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("[${log.errorCode}] ${log.category} — ${log.documentName}", fontWeight = FontWeight.Bold)
                                    Text("Nguyên nhân: ${log.cause}", style = MaterialTheme.typography.labelSmall)
                                    Text("Hướng xử lý: ${log.resolution}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Transparent Engine & Feature Status Classification Matrix (Section II & XVII)
        item {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, tint = WordBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "4. Phân loại Minh bạch Bộ máy & Trạng thái Chức năng (Mục XVII)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    FeatureStatusItem(
                        title = "Bộ máy OOXML Native DOM (DOCX, XLSX, PPTX) & Android PdfRenderer/PdfDocument",
                        status = "Đã hoàn thành và kiểm thử",
                        statusColor = ExcelGreen,
                        detail = "Hoạt động 100% ngoại tuyến trên thiết bị, giữ nguyên container ZIP OOXML, không biến DOCX thành HTML/Text."
                    )
                    FeatureStatusItem(
                        title = "Hệ thống Font chữ & Chống lỗi Tiếng Việt Unicode (Tinos, Arimo, Carlito, Be Vietnam Pro)",
                        status = "Đã hoàn thành và kiểm thử",
                        statusColor = ExcelGreen,
                        detail = "Hỗ trợ nhập TTF/OTF, kiểm tra bảng mã cmap Tiếng Việt và bảo toàn tên font gốc trong file nguồn."
                    )
                    FeatureStatusItem(
                        title = "Cơ chế Lưu tệp An toàn (Atomic Temp-File + SHA-256 + Lịch sử Phiên bản Room DB)",
                        status = "Đã hoàn thành và kiểm thử",
                        statusColor = ExcelGreen,
                        detail = "Ghi tệp tạm, xác minh toàn vẹn trước khi thay thế tệp đích, khôi phục phiên bản trước."
                    )
                    FeatureStatusItem(
                        title = "Đồng bộ Google Drive (OAuth 2.0 & REST API v3) + Phím tắt bàn phím rời & Bút cảm ứng",
                        status = "Đã triển khai nhưng chưa kiểm thử đầy đủ",
                        statusColor = WordBlue,
                        detail = "Đã tích hợp CredentialManager, Retrofit Drive v3 API và hàng đợi ngoại tuyến; cần xác thực tài khoản thực trên thiết bị."
                    )
                    FeatureStatusItem(
                        title = "Tích hợp Máy chủ ONLYOFFICE Docs Developer / Collabora Online & Thực thi VBA Macro",
                        status = "Cần giấy phép thương mại hoặc máy chủ",
                        statusColor = PowerPointOrange,
                        detail = "Cần máy chủ ONLYOFFICE/Collabora riêng hoặc SDK thương mại; ứng dụng bảo toàn nguyên vẹn Macro/PivotTable trong file mà không tự ý xóa."
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun FeatureStatusItem(
    title: String,
    status: String,
    statusColor: Color,
    detail: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = status,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
