package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.OfficeDocType
import com.example.ui.MainNavTab
import com.example.ui.OfficeViewModel
import com.example.ui.screens.CloudSyncScreen
import com.example.ui.screens.ExcelEditorScreen
import com.example.ui.screens.FontManagerScreen
import com.example.ui.screens.HomeAndFilesScreen
import com.example.ui.screens.PdfEditorScreen
import com.example.ui.screens.PowerPointEditorScreen
import com.example.ui.screens.SettingsAndDiagnosticsScreen
import com.example.ui.screens.TemplatesLibraryScreen
import com.example.ui.screens.WordEditorScreen
import com.example.ui.theme.DeviceFormFactorMode
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PdfCrimson
import com.example.ui.theme.PowerPointOrange
import com.example.ui.theme.ThemeBrightnessMode
import com.example.ui.theme.WordBlue

class MainActivity : ComponentActivity() {

    private val officeViewModel: OfficeViewModel by viewModels {
        OfficeViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            val prefs by officeViewModel.userPreferences.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val useDark = when (prefs.brightnessMode) {
                ThemeBrightnessMode.SYSTEM -> systemDark
                ThemeBrightnessMode.LIGHT -> false
                ThemeBrightnessMode.DARK -> true
            }

            MyApplicationTheme(
                darkTheme = useDark,
                accentColor = prefs.accentColor
            ) {
                OfficeProRootApp(viewModel = officeViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val dataUri = intent?.data ?: return
        officeViewModel.importFromSafUri(dataUri)
    }
}

@Composable
fun OfficeProRootApp(viewModel: OfficeViewModel) {
    val currentNavTab by viewModel.currentNavTab.collectAsState()
    val openSessions by viewModel.openSessions.collectAsState()
    val activeSessionDocId by viewModel.activeSessionDocId.collectAsState()
    val statusBanner by viewModel.statusBannerMessage.collectAsState()
    val prefs by viewModel.userPreferences.collectAsState()

    val activeSession = openSessions.find { it.document.id == activeSessionDocId }

    // SAF Open Document Launcher (supports DOCX, XLSX, PPTX, PDF, TXT, CSV, ODT, RTF)
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromSafUri(uri)
        }
    }

    // SAF Open Font Launcher (.ttf / .otf)
    val openFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCustomFontUri(uri)
        }
    }

    // Handle system Back press when an editor session is open or on secondary tabs
    BackHandler(enabled = activeSession != null || currentNavTab != MainNavTab.DOCUMENTS) {
        if (activeSession != null) {
            viewModel.switchActiveSession(null)
        } else {
            viewModel.selectNavTab(MainNavTab.DOCUMENTS)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                // External keyboard shortcuts (Ctrl+S to save, Ctrl+Z to undo)
                if (prefs.externalKeyboardShortcutsEnabled && activeSession != null && event.isCtrlPressed) {
                    when (event.key) {
                        Key.S -> {
                            viewModel.saveActiveSession(activeSession.document.id)
                            true
                        }
                        Key.Z -> {
                            viewModel.undoWord(activeSession.document.id)
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        val isWideScreen = when (prefs.formFactorMode) {
            DeviceFormFactorMode.TABLET -> true
            DeviceFormFactorMode.PHONE -> false
            DeviceFormFactorMode.AUTO -> maxWidth >= 640.dp
        }

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Multi-Document Switcher Tab Strip (Section III: Cho phép mở nhiều tài liệu và chuyển đổi giữa các tài liệu đang làm việc)
                    if (openSessions.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.switchActiveSession(null) },
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("back_to_home_button")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Về Trang chủ",
                                        tint = Color.White
                                    )
                                }

                                openSessions.forEach { sess ->
                                    val isCurrent = sess.document.id == activeSessionDocId
                                    val typeColor = when (OfficeDocType.fromCode(sess.document.docType)) {
                                        OfficeDocType.WORD -> WordBlue
                                        OfficeDocType.EXCEL -> ExcelGreen
                                        OfficeDocType.POWERPOINT -> PowerPointOrange
                                        OfficeDocType.PDF -> PdfCrimson
                                    }
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isCurrent) Color.White else Color.White.copy(alpha = 0.16f))
                                            .clickable { viewModel.switchActiveSession(sess.document.id) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("open_tab_${sess.document.id}"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(typeColor)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = sess.document.title.take(20) + if (sess.hasUnsavedChanges) "*" else "",
                                            color = if (isCurrent) Color(0xFF0F172A) else Color.White,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Đóng tài liệu",
                                            tint = if (isCurrent) Color.DarkGray else Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier
                                                .size(15.dp)
                                                .clickable { viewModel.closeSession(sess.document.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Status Notification Banner
                    AnimatedVisibility(visible = statusBanner != null) {
                        statusBanner?.let { msg ->
                            Surface(
                                color = Color(0xFF0F2547),
                                contentColor = Color.White,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.showBanner(null) }) {
                                        Text("Đóng", color = Color(0xFFFBBF24))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!isWideScreen && activeSession == null) {
                    NavigationBar {
                        MainNavTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentNavTab == tab,
                                onClick = { viewModel.selectNavTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            MainNavTab.DOCUMENTS -> Icons.Default.Home
                                            MainNavTab.TEMPLATES -> Icons.Default.FolderCopy
                                            MainNavTab.FONTS -> Icons.Default.FontDownload
                                            MainNavTab.CLOUD -> Icons.Default.Cloud
                                            MainNavTab.SETTINGS -> Icons.Default.Settings
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWideScreen && activeSession == null) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        MainNavTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentNavTab == tab,
                                onClick = { viewModel.selectNavTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = when (tab) {
                                            MainNavTab.DOCUMENTS -> Icons.Default.Description
                                            MainNavTab.TEMPLATES -> Icons.Default.FolderCopy
                                            MainNavTab.FONTS -> Icons.Default.FontDownload
                                            MainNavTab.CLOUD -> Icons.Default.Cloud
                                            MainNavTab.SETTINGS -> Icons.Default.Settings
                                        },
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (activeSession != null) {
                        when (OfficeDocType.fromCode(activeSession.document.docType)) {
                            OfficeDocType.WORD -> WordEditorScreen(session = activeSession, viewModel = viewModel)
                            OfficeDocType.EXCEL -> ExcelEditorScreen(session = activeSession, viewModel = viewModel)
                            OfficeDocType.POWERPOINT -> PowerPointEditorScreen(session = activeSession, viewModel = viewModel)
                            OfficeDocType.PDF -> PdfEditorScreen(session = activeSession, viewModel = viewModel)
                        }
                    } else {
                        when (currentNavTab) {
                            MainNavTab.DOCUMENTS -> HomeAndFilesScreen(
                                viewModel = viewModel,
                                onRequestSafOpen = {
                                    openDocumentLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            MainNavTab.TEMPLATES -> TemplatesLibraryScreen(
                                viewModel = viewModel,
                                onRequestImportTemplateSaf = {
                                    openDocumentLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            MainNavTab.FONTS -> FontManagerScreen(
                                viewModel = viewModel,
                                onRequestImportFontSaf = {
                                    openFontLauncher.launch(arrayOf("*/*"))
                                }
                            )
                            MainNavTab.CLOUD -> CloudSyncScreen(viewModel = viewModel)
                            MainNavTab.SETTINGS -> SettingsAndDiagnosticsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}
