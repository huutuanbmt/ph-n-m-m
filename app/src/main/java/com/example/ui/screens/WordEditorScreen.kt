package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.FontManagerEngine
import com.example.engine.HeadingStyle
import com.example.engine.ListStyleType
import com.example.engine.PaperSizePreset
import com.example.engine.ParagraphAlignment
import com.example.engine.WordParagraph
import com.example.engine.WordTableData
import com.example.ui.OfficeViewModel
import com.example.ui.OpenEditorSession
import com.example.ui.theme.OfficeUiStyle
import com.example.ui.theme.ToolbarPositionMode
import com.example.ui.theme.WordBlue
import com.example.ui.theme.resolveComposeFontFamily

enum class WordRibbonGroup(val label: String) {
    HOME("Home"),
    INSERT("Insert"),
    LAYOUT("Layout"),
    REFERENCES("References"),
    REVIEW("Review"),
    VIEW("View")
}

@Composable
fun WordEditorScreen(
    session: OpenEditorSession,
    viewModel: OfficeViewModel
) {
    val model = session.wordModel ?: return
    val prefs by viewModel.userPreferences.collectAsState()
    val installedFonts by viewModel.fonts.collectAsState()

    var activeRibbon by remember { mutableStateOf(WordRibbonGroup.HOME) }
    var selectedParaIndex by remember { mutableIntStateOf(0) }
    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var isReaderMode by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }
    var showSaveCopyDialog by remember { mutableStateOf(false) }
    var showAddTableDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }

    val safeSelectedIdx = selectedParaIndex.coerceIn(0, (model.paragraphs.size - 1).coerceAtLeast(0))
    val currentPara = model.paragraphs.getOrNull(safeSelectedIdx) ?: WordParagraph(id = "p_0", text = "")
    val fontList = remember(installedFonts) {
        (listOf("Times New Roman", "Calibri", "Arial", "Cambria", "Be Vietnam Pro", "Roboto Mono") +
            installedFonts.map { it.officeTargetName }).distinct()
    }

    val toolbarComposable: @Composable () -> Unit = {
        Surface(
            tonalElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                // Top Action Strip: Save, Undo, Redo, Find/Replace, Export PDF, Save Copy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveActiveSession(session.document.id) },
                        modifier = Modifier.testTag("word_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Lưu", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (session.hasUnsavedChanges) "Lưu*" else "Đã lưu")
                    }
                    IconButton(
                        onClick = { viewModel.undoWord(session.document.id) },
                        enabled = session.wordUndoStack.isNotEmpty(),
                        modifier = Modifier.testTag("word_undo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Hoàn tác")
                    }
                    IconButton(
                        onClick = { viewModel.redoWord(session.document.id) },
                        enabled = session.wordRedoStack.isNotEmpty(),
                        modifier = Modifier.testTag("word_redo_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Làm lại")
                    }
                    OutlinedButton(onClick = { showFindReplace = !showFindReplace }) {
                        Icon(Icons.Default.FindReplace, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tìm & Thay thế")
                    }
                    OutlinedButton(
                        onClick = { viewModel.exportSessionToPdf(session.document.id) },
                        modifier = Modifier.testTag("word_export_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Xuất PDF")
                    }
                    OutlinedButton(onClick = { showSaveCopyDialog = true }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Lưu bản sao")
                    }
                }

                // Ribbon Tabs (unless Minimal mode)
                if (prefs.uiStyle != OfficeUiStyle.MINIMAL) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WordRibbonGroup.entries.forEach { group ->
                            FilterChip(
                                selected = activeRibbon == group,
                                onClick = { activeRibbon = group },
                                label = { Text(group.label) },
                                modifier = Modifier.testTag("word_ribbon_${group.name.lowercase()}")
                            )
                        }
                    }

                    // Active Ribbon Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (activeRibbon) {
                            WordRibbonGroup.HOME -> {
                                // Font Cycle Chip
                                AssistChip(
                                    onClick = {
                                        val nextIdx = (fontList.indexOf(currentPara.fontFamily) + 1) % fontList.size
                                        val nextFont = fontList[nextIdx]
                                        val updated = model.paragraphs.mapIndexed { i, p ->
                                            if (i == safeSelectedIdx) p.copy(fontFamily = nextFont) else p
                                        }
                                        viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                    },
                                    label = { Text("Font: ${currentPara.fontFamily}") },
                                    modifier = Modifier.testTag("word_font_family_chip")
                                )
                                // Font Size - / +
                                AssistChip(
                                    onClick = {
                                        val nextSize = if (currentPara.fontSizePt >= 24) 11 else currentPara.fontSizePt + 1
                                        val updated = model.paragraphs.mapIndexed { i, p ->
                                            if (i == safeSelectedIdx) p.copy(fontSizePt = nextSize) else p
                                        }
                                        viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                    },
                                    label = { Text("Cỡ: ${currentPara.fontSizePt}pt") },
                                    modifier = Modifier.testTag("word_font_size_chip")
                                )
                                IconButton(
                                    onClick = {
                                        val updated = model.paragraphs.mapIndexed { i, p ->
                                            if (i == safeSelectedIdx) p.copy(isBold = !p.isBold) else p
                                        }
                                        viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                    },
                                    modifier = Modifier.testTag("word_bold_button")
                                ) {
                                    Icon(
                                        Icons.Default.FormatBold,
                                        contentDescription = "In đậm",
                                        tint = if (currentPara.isBold) WordBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = model.paragraphs.mapIndexed { i, p ->
                                            if (i == safeSelectedIdx) p.copy(isItalic = !p.isItalic) else p
                                        }
                                        viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.FormatItalic,
                                        contentDescription = "In nghiêng",
                                        tint = if (currentPara.isItalic) WordBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val updated = model.paragraphs.mapIndexed { i, p ->
                                            if (i == safeSelectedIdx) p.copy(isUnderline = !p.isUnderline) else p
                                        }
                                        viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.FormatUnderlined,
                                        contentDescription = "Gạch chân",
                                        tint = if (currentPara.isUnderline) WordBlue else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                // Alignment buttons
                                IconButton(onClick = {
                                    val updated = model.paragraphs.mapIndexed { i, p ->
                                        if (i == safeSelectedIdx) p.copy(alignment = ParagraphAlignment.LEFT) else p
                                    }
                                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, contentDescription = "Căn trái")
                                }
                                IconButton(onClick = {
                                    val updated = model.paragraphs.mapIndexed { i, p ->
                                        if (i == safeSelectedIdx) p.copy(alignment = ParagraphAlignment.CENTER) else p
                                    }
                                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                }) {
                                    Icon(Icons.Default.FormatAlignCenter, contentDescription = "Căn giữa")
                                }
                                IconButton(onClick = {
                                    val updated = model.paragraphs.mapIndexed { i, p ->
                                        if (i == safeSelectedIdx) p.copy(alignment = ParagraphAlignment.RIGHT) else p
                                    }
                                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.FormatAlignRight, contentDescription = "Căn phải")
                                }
                                IconButton(onClick = {
                                    val updated = model.paragraphs.mapIndexed { i, p ->
                                        if (i == safeSelectedIdx) p.copy(alignment = ParagraphAlignment.JUSTIFY) else p
                                    }
                                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                }) {
                                    Icon(Icons.Default.FormatAlignJustify, contentDescription = "Căn đều 2 bên")
                                }
                                // Line spacing
                                AssistChip(
                                    onClick = {
                                        val nextSpacing = when (currentPara.lineSpacingMultiplier) {
                                            1.0f -> 1.15f
                                            1.15f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        val updated = model.paragraphs.mapIndexed { i, p ->
                                            if (i == safeSelectedIdx) p.copy(lineSpacingMultiplier = nextSpacing) else p
                                        }
                                        viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                    },
                                    label = { Text("Giãn dòng: ${currentPara.lineSpacingMultiplier}") }
                                )
                                // Bullet & Numbered Lists
                                IconButton(onClick = {
                                    val nextList = if (currentPara.listStyle == ListStyleType.BULLET) ListStyleType.NONE else ListStyleType.BULLET
                                    val updated = model.paragraphs.mapIndexed { i, p ->
                                        if (i == safeSelectedIdx) p.copy(listStyle = nextList) else p
                                    }
                                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Danh sách chấm")
                                }
                                IconButton(onClick = {
                                    val nextList = if (currentPara.listStyle == ListStyleType.NUMBERED) ListStyleType.NONE else ListStyleType.NUMBERED
                                    val updated = model.paragraphs.mapIndexed { i, p ->
                                        if (i == safeSelectedIdx) p.copy(listStyle = nextList) else p
                                    }
                                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                }) {
                                    Icon(Icons.Default.FormatListNumbered, contentDescription = "Danh sách số")
                                }
                            }

                            WordRibbonGroup.INSERT -> {
                                AssistChip(
                                    onClick = {
                                        val newP = WordParagraph(
                                            id = "p_${System.currentTimeMillis()}",
                                            text = "Đoạn văn bản mới...",
                                            fontFamily = currentPara.fontFamily,
                                            fontSizePt = 13,
                                            lineSpacingMultiplier = 1.5f
                                        )
                                        val list = model.paragraphs.toMutableList()
                                        list.add((safeSelectedIdx + 1).coerceAtMost(list.size), newP)
                                        viewModel.updateWordModel(session.document.id, model.copy(paragraphs = list), true)
                                        selectedParaIndex = safeSelectedIdx + 1
                                    },
                                    label = { Text("+ Thêm đoạn văn") },
                                    modifier = Modifier.testTag("word_add_paragraph_chip")
                                )
                                AssistChip(
                                    onClick = { showAddTableDialog = true },
                                    label = { Text("+ Chèn Bảng biểu") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                                AssistChip(
                                    onClick = {
                                        val eqP = WordParagraph(
                                            id = "p_eq_${System.currentTimeMillis()}",
                                            text = "Công thức toán học: f(x) = ∑(i=1..n) [x_i² / √(1 + Δx)] ≈ ∫ e^(-t²) dt",
                                            fontFamily = "Times New Roman",
                                            fontSizePt = 13,
                                            isItalic = true,
                                            alignment = ParagraphAlignment.CENTER
                                        )
                                        viewModel.updateWordModel(
                                            session.document.id,
                                            model.copy(paragraphs = model.paragraphs + eqP),
                                            true
                                        )
                                    },
                                    label = { Text("+ Chèn Công thức (∑)") }
                                )
                                AssistChip(
                                    onClick = {
                                        val updated = model.paragraphs.mapIndexed { i, p ->
                                            if (i == safeSelectedIdx) p.copy(hasPageBreakAfter = !p.hasPageBreakAfter) else p
                                        }
                                        viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                    },
                                    label = { Text(if (currentPara.hasPageBreakAfter) "✓ Đã ngắt trang sau đoạn" else "+ Ngắt trang (Page Break)") }
                                )
                            }

                            WordRibbonGroup.LAYOUT -> {
                                PaperSizePreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = model.pageSetup.paperSize == preset,
                                        onClick = {
                                            viewModel.updateWordModel(
                                                session.document.id,
                                                model.copy(pageSetup = model.pageSetup.copy(paperSize = preset)),
                                                true
                                            )
                                        },
                                        label = { Text(preset.label) }
                                    )
                                }
                                FilterChip(
                                    selected = model.pageSetup.isLandscape,
                                    onClick = {
                                        viewModel.updateWordModel(
                                            session.document.id,
                                            model.copy(pageSetup = model.pageSetup.copy(isLandscape = !model.pageSetup.isLandscape)),
                                            true
                                        )
                                    },
                                    label = { Text(if (model.pageSetup.isLandscape) "Khổ Ngang" else "Khổ Dọc") }
                                )
                                AssistChip(
                                    onClick = {
                                        val nextCols = if (model.pageSetup.columns == 1) 2 else 1
                                        viewModel.updateWordModel(
                                            session.document.id,
                                            model.copy(pageSetup = model.pageSetup.copy(columns = nextCols)),
                                            true
                                        )
                                    },
                                    label = { Text("Số cột: ${model.pageSetup.columns}") }
                                )
                            }

                            WordRibbonGroup.REFERENCES -> {
                                FilterChip(
                                    selected = model.hasAutoToc,
                                    onClick = {
                                        viewModel.updateWordModel(
                                            session.document.id,
                                            model.copy(hasAutoToc = !model.hasAutoToc),
                                            true
                                        )
                                    },
                                    label = { Text(if (model.hasAutoToc) "✓ Mục lục tự động (Đang bật)" else "+ Bật Mục lục tự động") }
                                )
                                HeadingStyle.entries.forEach { hs ->
                                    FilterChip(
                                        selected = currentPara.headingStyle == hs,
                                        onClick = {
                                            val updated = model.paragraphs.mapIndexed { i, p ->
                                                if (i == safeSelectedIdx) p.copy(
                                                    headingStyle = hs,
                                                    fontSizePt = hs.defaultSizePt,
                                                    isBold = hs != HeadingStyle.NORMAL
                                                ) else p
                                            }
                                            viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                        },
                                        label = { Text(hs.label) }
                                    )
                                }
                            }

                            WordRibbonGroup.REVIEW -> {
                                AssistChip(
                                    onClick = { showCommentDialog = true },
                                    label = { Text("+ Thêm Bình luận / Chú thích") },
                                    leadingIcon = { Icon(Icons.Default.Comment, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                                AssistChip(
                                    onClick = {
                                        val allText = model.paragraphs.joinToString("\n") { it.text }
                                        val warnings = FontManagerEngine.checkVietnameseUnicodeText(allText)
                                        val wordCount = allText.split(Regex("\\s+")).count { it.isNotEmpty() }
                                        viewModel.showBanner(
                                            if (warnings.isEmpty()) "Kiểm tra Tiếng Việt: Chuẩn Unicode dựng sẵn (NFC) • $wordCount từ • ${model.estimatePageCount()} trang"
                                            else warnings.first()
                                        )
                                    },
                                    label = { Text("Kiểm tra Chính tả & Dấu Tiếng Việt") }
                                )
                            }

                            WordRibbonGroup.VIEW -> {
                                FilterChip(
                                    selected = isReaderMode,
                                    onClick = { isReaderMode = !isReaderMode },
                                    label = { Text(if (isReaderMode) "Chế độ Đọc tài liệu" else "Chế độ Trang in A4") }
                                )
                                Text("Thu phóng: ${(zoomScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                                Slider(
                                    value = zoomScale,
                                    onValueChange = { zoomScale = it },
                                    valueRange = 0.75f..1.6f,
                                    modifier = Modifier.width(140.dp)
                                )
                            }
                        }
                    }
                }

                // Find & Replace Bar
                if (showFindReplace) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = findQuery,
                            onValueChange = { findQuery = it },
                            placeholder = { Text("Tìm từ...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = replaceQuery,
                            onValueChange = { replaceQuery = it },
                            placeholder = { Text("Thay bằng...") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (findQuery.isNotEmpty()) {
                                    val updated = model.paragraphs.map { p ->
                                        p.copy(text = p.text.replace(findQuery, replaceQuery, ignoreCase = true))
                                    }
                                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                    viewModel.showBanner("Đã thay thế '$findQuery' bằng '$replaceQuery'")
                                }
                            }
                        ) {
                            Text("Thay tất cả")
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (prefs.toolbarPosition == ToolbarPositionMode.TOP) {
            toolbarComposable()
        }

        // Font Compatibility Status Banner (Section V)
        session.fontReport?.let { rep ->
            Surface(
                color = if (rep.missingFonts.isEmpty()) Color(0xFFDBEAFE) else Color(0xFFFEF3C7),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = WordBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = rep.summaryMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1E3A8A)
                    )
                }
            }
        }

        // A4 Document Page Canvas
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = (model.pageSetup.marginLeftMm * 0.8f).dp,
                                vertical = (model.pageSetup.marginTopMm * 0.8f).dp
                            )
                    ) {
                        // Document Header
                        if (model.pageSetup.headerText.isNotBlank()) {
                            Text(
                                text = model.pageSetup.headerText,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFE2E8F0))
                        }

                        // Automatic Table of Contents Box (Section 4.3 & 4.4)
                        val tocEntries = remember(model.paragraphs, model.hasAutoToc) {
                            if (model.hasAutoToc) model.generateAutoTocEntries() else emptyList()
                        }
                        if (model.hasAutoToc && tocEntries.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "MỤC LỤC TỰ ĐỘNG (Cập nhật theo tiêu đề chương mục)",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = WordBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    tocEntries.forEach { (headingTitle, pageNum) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = headingTitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF1E293B)
                                            )
                                            Text(
                                                text = "Trang $pageNum",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = WordBlue
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Paragraphs list inside the A4 Page look
            itemsIndexed(model.paragraphs, key = { _, p -> p.id }) { index, para ->
                val isSelected = index == safeSelectedIdx
                val composeFont = resolveComposeFontFamily(para.fontFamily)
                val scaledFontSize = (para.fontSizePt * zoomScale).sp

                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .border(
                            width = if (isSelected && !isReaderMode) 1.5.dp else 0.dp,
                            color = if (isSelected && !isReaderMode) WordBlue.copy(alpha = 0.6f) else Color.Transparent
                        )
                        .clickable { selectedParaIndex = index }
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                ) {
                    Column {
                        val prefix = when (para.listStyle) {
                            ListStyleType.BULLET -> "• "
                            ListStyleType.NUMBERED -> "${index + 1}. "
                            ListStyleType.NONE -> ""
                        }
                        if (isReaderMode) {
                            Text(
                                text = prefix + para.text,
                                style = TextStyle(
                                    fontFamily = composeFont,
                                    fontSize = scaledFontSize,
                                    fontWeight = if (para.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (para.isItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (para.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                    color = Color(0xFF0F172A),
                                    textAlign = when (para.alignment) {
                                        ParagraphAlignment.CENTER -> TextAlign.Center
                                        ParagraphAlignment.RIGHT -> TextAlign.End
                                        ParagraphAlignment.JUSTIFY -> TextAlign.Justify
                                        ParagraphAlignment.LEFT -> TextAlign.Start
                                    },
                                    lineHeight = (para.fontSizePt * para.lineSpacingMultiplier * zoomScale).sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        } else {
                            OutlinedTextField(
                                value = para.text,
                                onValueChange = { newText ->
                                    val updated = model.paragraphs.mapIndexed { i, p ->
                                        if (i == index) p.copy(text = newText) else p
                                    }
                                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), false)
                                },
                                textStyle = TextStyle(
                                    fontFamily = composeFont,
                                    fontSize = scaledFontSize,
                                    fontWeight = if (para.isBold) FontWeight.Bold else FontWeight.Normal,
                                    fontStyle = if (para.isItalic) FontStyle.Italic else FontStyle.Normal,
                                    textDecoration = if (para.isUnderline) TextDecoration.Underline else TextDecoration.None,
                                    color = Color(0xFF0F172A),
                                    textAlign = when (para.alignment) {
                                        ParagraphAlignment.CENTER -> TextAlign.Center
                                        ParagraphAlignment.RIGHT -> TextAlign.End
                                        ParagraphAlignment.JUSTIFY -> TextAlign.Justify
                                        ParagraphAlignment.LEFT -> TextAlign.Start
                                    },
                                    lineHeight = (para.fontSizePt * para.lineSpacingMultiplier * zoomScale).sp
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("word_paragraph_input_$index"),
                                label = {
                                    Text(
                                        "${para.headingStyle.label} • ${para.fontFamily} ${para.fontSizePt}pt • Giãn dòng ${para.lineSpacingMultiplier}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = if (model.paragraphs.size > 1 && isSelected) {
                                    {
                                        IconButton(onClick = {
                                            val updated = model.paragraphs.filterIndexed { i, _ -> i != index }
                                            viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                                        }) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Xóa đoạn")
                                        }
                                    }
                                } else null
                            )
                        }

                        if (para.footnoteText.isNotBlank() || para.commentText.isNotBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFFEF3C7))
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (para.footnoteText.isNotBlank()) {
                                    Text("Chú thích: ${para.footnoteText}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF92400E))
                                }
                                if (para.commentText.isNotBlank()) {
                                    Text("Bình luận: ${para.commentText}", style = MaterialTheme.typography.labelSmall, color = WordBlue)
                                }
                            }
                        }

                        if (para.hasPageBreakAfter) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f))
                                Text(
                                    " --- NGẮT TRANG (PAGE BREAK) --- ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WordBlue
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Tables inside the Word Document
            itemsIndexed(model.tables, key = { _, t -> t.id }) { tIdx, table ->
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = table.caption.ifEmpty { "Bảng ${tIdx + 1}" },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = WordBlue
                        )
                        Spacer(Modifier.height(6.dp))
                        table.rows.forEachIndexed { rIdx, row ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                row.forEachIndexed { cIdx, cellText ->
                                    OutlinedTextField(
                                        value = cellText,
                                        onValueChange = { newVal ->
                                            val updatedRows = table.rows.mapIndexed { rr, rList ->
                                                if (rr == rIdx) {
                                                    rList.mapIndexed { cc, oldCell -> if (cc == cIdx) newVal else oldCell }
                                                } else rList
                                            }
                                            val updatedTables = model.tables.mapIndexed { tt, tbl ->
                                                if (tt == tIdx) tbl.copy(rows = updatedRows) else tbl
                                            }
                                            viewModel.updateWordModel(session.document.id, model.copy(tables = updatedTables), false)
                                        },
                                        modifier = Modifier.weight(1f),
                                        textStyle = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = if (rIdx == 0) FontWeight.Bold else FontWeight.Normal,
                                            color = Color.Black
                                        ),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Page Footer Info
            item {
                Surface(
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = model.pageSetup.footerText.replace("{PAGE}", "1 / ${model.estimatePageCount()}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                            Text(
                                text = "${model.pageSetup.paperSize.label} • Lề T:${model.pageSetup.marginLeftMm}mm P:${model.pageSetup.marginRightMm}mm",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }

        if (prefs.toolbarPosition == ToolbarPositionMode.BOTTOM) {
            toolbarComposable()
        }
    }

    if (showAddTableDialog) {
        var rowsCount by remember { mutableStateOf("3") }
        var colsCount by remember { mutableStateOf("3") }
        var caption by remember { mutableStateOf("Bảng ${model.tables.size + 1}: Số liệu tổng hợp") }
        AlertDialog(
            onDismissRequest = { showAddTableDialog = false },
            title = { Text("Chèn Bảng biểu mới vào Word") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = caption, onValueChange = { caption = it }, label = { Text("Chú thích bảng") })
                    OutlinedTextField(value = rowsCount, onValueChange = { rowsCount = it }, label = { Text("Số hàng (2-10)") })
                    OutlinedTextField(value = colsCount, onValueChange = { colsCount = it }, label = { Text("Số cột (2-6)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val r = (rowsCount.toIntOrNull() ?: 3).coerceIn(2, 10)
                    val c = (colsCount.toIntOrNull() ?: 3).coerceIn(2, 6)
                    val matrix = List(r) { rIdx ->
                        List(c) { cIdx -> if (rIdx == 0) "Cột ${cIdx + 1}" else "Dữ liệu" }
                    }
                    val newTable = WordTableData(id = "tbl_${System.currentTimeMillis()}", caption = caption, rows = matrix)
                    viewModel.updateWordModel(session.document.id, model.copy(tables = model.tables + newTable), true)
                    showAddTableDialog = false
                }) {
                    Text("Chèn bảng")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTableDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (showCommentDialog) {
        var commentTxt by remember { mutableStateOf(currentPara.commentText) }
        var footnoteTxt by remember { mutableStateOf(currentPara.footnoteText) }
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text("Bình luận & Chú thích cuối trang") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = commentTxt,
                        onValueChange = { commentTxt = it },
                        label = { Text("Nội dung bình luận (Review Comment)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = footnoteTxt,
                        onValueChange = { footnoteTxt = it },
                        label = { Text("Chú thích cuối trang (Footnote)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val updated = model.paragraphs.mapIndexed { i, p ->
                        if (i == safeSelectedIdx) p.copy(commentText = commentTxt, footnoteText = footnoteTxt) else p
                    }
                    viewModel.updateWordModel(session.document.id, model.copy(paragraphs = updated), true)
                    showCommentDialog = false
                }) {
                    Text("Lưu chú thích")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommentDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (showSaveCopyDialog) {
        var copyName by remember { mutableStateOf("${session.document.title.substringBeforeLast('.')}_Ban_sao") }
        AlertDialog(
            onDismissRequest = { showSaveCopyDialog = false },
            title = { Text("Lưu thành bản sao mới (.docx)") },
            text = {
                OutlinedTextField(
                    value = copyName,
                    onValueChange = { copyName = it },
                    label = { Text("Tên tệp mới") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.saveSessionAsCopy(session.document.id, copyName)
                    showSaveCopyDialog = false
                }) {
                    Text("Lưu bản sao")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveCopyDialog = false }) { Text("Hủy") }
            }
        )
    }
}
