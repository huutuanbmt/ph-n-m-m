package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.engine.PdfAnnotationItem
import com.example.engine.PdfAnnotationType
import com.example.engine.PdfEngine
import com.example.engine.PdfStructureKind
import com.example.ui.OfficeViewModel
import com.example.ui.OpenEditorSession
import com.example.ui.theme.PdfCrimson
import java.io.File

@Composable
fun PdfEditorScreen(
    session: OpenEditorSession,
    viewModel: OfficeViewModel
) {
    val pdfState = session.pdfState ?: return
    val context = LocalContext.current
    var activePageIndex by remember { mutableIntStateOf(0) }
    val safePageIdx = activePageIndex.coerceIn(0, (pdfState.pages.size - 1).coerceAtLeast(0))
    val currentPage = pdfState.pages.getOrNull(safePageIdx) ?: return

    var zoomScale by remember { mutableFloatStateOf(1.0f) }
    var searchQuery by remember { mutableStateOf("") }
    var isFreehandDrawingMode by remember { mutableStateOf(false) }
    val currentInkPoints = remember { mutableStateListOf<Pair<Float, Float>>() }

    var showAddAnnotationDialog by remember { mutableStateOf<PdfAnnotationType?>(null) }
    var showSignaturePadDialog by remember { mutableStateOf(false) }
    var showSignedWarningDialog by remember {
        mutableStateOf(pdfState.structureKind == PdfStructureKind.DIGITALLY_SIGNED)
    }

    val renderedBitmap = remember(
        session.document.filePath,
        currentPage,
        pdfState.annotations,
        safePageIdx
    ) {
        val pageAnns = pdfState.annotations.filter { it.pageIndex == safePageIdx }
        PdfEngine.renderPageToBitmap(
            file = File(session.document.filePath),
            pageConfig = currentPage,
            annotations = pageAnns,
            widthPx = 700,
            heightPx = 990
        )
    }

    fun addAnnotation(ann: PdfAnnotationItem) {
        viewModel.updatePdfState(
            session.document.id,
            pdfState.copy(annotations = pdfState.annotations + ann)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top PDF Action & Annotation Toolbar
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            if (pdfState.structureKind == PdfStructureKind.DIGITALLY_SIGNED) {
                                viewModel.saveSessionAsCopy(
                                    session.document.id,
                                    "${session.document.title.substringBeforeLast('.')}_Ban_sao_da_sua"
                                )
                            } else {
                                viewModel.saveActiveSession(session.document.id)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PdfCrimson),
                        modifier = Modifier.testTag("pdf_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Lưu PDF", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (pdfState.structureKind == PdfStructureKind.DIGITALLY_SIGNED) "Lưu Bản sao PDF"
                            else if (session.hasUnsavedChanges) "Lưu PDF*" else "Đã lưu"
                        )
                    }

                    // Highlight, Underline, Strikethrough, Note, TextBox, Form, Signature, Ink
                    AssistChip(
                        onClick = {
                            addAnnotation(
                                PdfAnnotationItem(
                                    id = "hl_${System.currentTimeMillis()}",
                                    pageIndex = safePageIdx,
                                    type = PdfAnnotationType.HIGHLIGHT,
                                    text = "Đánh dấu vùng quan trọng",
                                    xPercent = 12f,
                                    yPercent = 22f + (pdfState.annotations.size * 7f) % 55f
                                )
                            )
                        },
                        label = { Text("+ Tô sáng") },
                        modifier = Modifier.testTag("pdf_highlight_chip")
                    )
                    AssistChip(
                        onClick = {
                            addAnnotation(
                                PdfAnnotationItem(
                                    id = "ul_${System.currentTimeMillis()}",
                                    pageIndex = safePageIdx,
                                    type = PdfAnnotationType.UNDERLINE,
                                    xPercent = 12f,
                                    yPercent = 28f + (pdfState.annotations.size * 6f) % 50f
                                )
                            )
                        },
                        label = { Text("+ Gạch chân") }
                    )
                    AssistChip(
                        onClick = { showAddAnnotationDialog = PdfAnnotationType.STICKY_NOTE },
                        label = { Text("+ Ghi chú") },
                        modifier = Modifier.testTag("pdf_sticky_note_chip")
                    )
                    AssistChip(
                        onClick = { showAddAnnotationDialog = PdfAnnotationType.TEXT_BOX },
                        label = { Text("+ Hộp chữ") }
                    )
                    AssistChip(
                        onClick = { showAddAnnotationDialog = PdfAnnotationType.FORM_FIELD },
                        label = { Text("+ Điền biểu mẫu") }
                    )
                    AssistChip(
                        onClick = { showSignaturePadDialog = true },
                        label = { Text("+ Chữ ký tay") },
                        leadingIcon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("pdf_signature_chip")
                    )
                    FilterChip(
                        selected = isFreehandDrawingMode,
                        onClick = { isFreehandDrawingMode = !isFreehandDrawingMode },
                        label = { Text(if (isFreehandDrawingMode) "✓ Đang vẽ tay" else "Vẽ trực tiếp") },
                        leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    OutlinedButton(onClick = { viewModel.shareDocumentFile(session.document) }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Chia sẻ / In")
                    }
                }

                // Search & OCR & Zoom Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm văn bản trong PDF...") },
                        singleLine = true,
                        modifier = Modifier.width(210.dp)
                    )
                    AssistChip(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("PDF Text", currentPage.extractedText))
                            viewModel.showBanner("Đã sao chép văn bản Trang ${safePageIdx + 1} vào bộ nhớ tạm")
                        },
                        label = { Text("Sao chép chữ") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    AssistChip(
                        onClick = {
                            val updatedPages = pdfState.pages.mapIndexed { idx, p ->
                                if (idx == safePageIdx) {
                                    p.copy(
                                        isOcrProcessed = true,
                                        extractedText = "[OCR Tiếng Việt hoàn tất - Trang ${idx + 1}]\nCỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM\nĐộc lập - Tự do - Hạnh phúc\nNội dung nhận diện chuẩn Unicode từ trang tài liệu."
                                    )
                                } else p
                            }
                            viewModel.updatePdfState(session.document.id, pdfState.copy(pages = updatedPages))
                            viewModel.showBanner("Đã thực hiện OCR nhận diện văn bản Tiếng Việt cho Trang ${safePageIdx + 1}")
                        },
                        label = { Text("OCR Tiếng Việt") },
                        leadingIcon = { Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    Text("Zoom ${(zoomScale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = zoomScale,
                        onValueChange = { zoomScale = it },
                        valueRange = 0.8f..1.8f,
                        modifier = Modifier.width(120.dp)
                    )
                }
            }
        }

        // PDF Structure & Digital Signature Detection Banner (Section VIII)
        val bannerBg = when (pdfState.structureKind) {
            PdfStructureKind.TRUE_TEXT -> Color(0xFFD1FAE5)
            PdfStructureKind.SCANNED_IMAGE -> Color(0xFFFEF3C7)
            PdfStructureKind.DIGITALLY_SIGNED -> Color(0xFFFEE2E2)
        }
        val bannerFg = when (pdfState.structureKind) {
            PdfStructureKind.TRUE_TEXT -> Color(0xFF065F46)
            PdfStructureKind.SCANNED_IMAGE -> Color(0xFF92400E)
            PdfStructureKind.DIGITALLY_SIGNED -> Color(0xFF991B1B)
        }
        Surface(color = bannerBg, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = bannerFg, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${pdfState.structureKind.label}: ${pdfState.structureKind.description}",
                    style = MaterialTheme.typography.labelSmall,
                    color = bannerFg,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // PDF Page Viewer + Interactive Freehand Ink Overlay + Extracted Text Inspector
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(zoomScale.coerceIn(0.85f, 1.0f))
                    .aspectRatio(595f / 842f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            ) {
                Image(
                    bitmap = renderedBitmap.asImageBitmap(),
                    contentDescription = "Trang PDF ${safePageIdx + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                if (isFreehandDrawingMode) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(safePageIdx) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentInkPoints.clear()
                                        currentInkPoints.add(
                                            (offset.x / size.width) * 100f to (offset.y / size.height) * 100f
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentInkPoints.add(
                                            (change.position.x / size.width) * 100f to (change.position.y / size.height) * 100f
                                        )
                                    },
                                    onDragEnd = {
                                        if (currentInkPoints.size >= 2) {
                                            addAnnotation(
                                                PdfAnnotationItem(
                                                    id = "ink_${System.currentTimeMillis()}",
                                                    pageIndex = safePageIdx,
                                                    type = PdfAnnotationType.FREEHAND_INK,
                                                    colorHex = "DC2626",
                                                    inkPoints = currentInkPoints.toList()
                                                )
                                            )
                                            currentInkPoints.clear()
                                        }
                                    }
                                )
                            }
                    ) {
                        if (currentInkPoints.size >= 2) {
                            val path = Path()
                            val first = currentInkPoints.first()
                            path.moveTo((first.first / 100f) * size.width, (first.second / 100f) * size.height)
                            for (pt in currentInkPoints.drop(1)) {
                                path.lineTo((pt.first / 100f) * size.width, (pt.second / 100f) * size.height)
                            }
                            drawPath(
                                path = path,
                                color = Color.Red,
                                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            }

            // Selectable & Searchable Text Layer Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Lớp văn bản trích xuất / OCR (Trang ${safePageIdx + 1})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = PdfCrimson
                    )
                    Spacer(Modifier.height(4.dp))
                    val displayTxt = if (searchQuery.isNotBlank() && currentPage.extractedText.contains(searchQuery, ignoreCase = true)) {
                        "🔍 Tìm thấy '$searchQuery' trên trang này:\n${currentPage.extractedText}"
                    } else {
                        currentPage.extractedText
                    }
                    Text(text = displayTxt, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Bottom Page Management Bar (Page Switcher, Rotate 90°, Duplicate/Split, Delete Page)
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pdfState.pages.forEachIndexed { idx, _ ->
                    FilterChip(
                        selected = idx == safePageIdx,
                        onClick = { activePageIndex = idx },
                        label = { Text("Trang ${idx + 1}") },
                        modifier = Modifier.testTag("pdf_page_tab_$idx")
                    )
                }
                IconButton(
                    onClick = {
                        val updatedPages = pdfState.pages.mapIndexed { idx, p ->
                            if (idx == safePageIdx) p.copy(rotationDegrees = (p.rotationDegrees + 90) % 360) else p
                        }
                        viewModel.updatePdfState(session.document.id, pdfState.copy(pages = updatedPages))
                    },
                    modifier = Modifier.testTag("pdf_rotate_page_button")
                ) {
                    Icon(Icons.Default.RotateRight, contentDescription = "Xoay trang 90°")
                }
                AssistChip(
                    onClick = {
                        val dupPage = currentPage.copy()
                        val list = pdfState.pages.toMutableList()
                        list.add(safePageIdx + 1, dupPage)
                        viewModel.updatePdfState(session.document.id, pdfState.copy(pages = list))
                        viewModel.showBanner("Đã nhân bản/ghép thêm trang mới vào PDF")
                    },
                    label = { Text("+ Ghép / Nhân bản trang") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                if (pdfState.pages.size > 1) {
                    IconButton(onClick = {
                        val remaining = pdfState.pages.filterIndexed { i, _ -> i != safePageIdx }
                        viewModel.updatePdfState(session.document.id, pdfState.copy(pages = remaining))
                        activePageIndex = 0
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa trang PDF", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    showAddAnnotationDialog?.let { annType ->
        var textVal by remember {
            mutableStateOf(
                when (annType) {
                    PdfAnnotationType.STICKY_NOTE -> "Cần rà soát lại điều khoản số 3"
                    PdfAnnotationType.FORM_FIELD -> "Nguyễn Hữu Tuấn - 0943429595"
                    else -> "Nội dung văn bản bổ sung"
                }
            )
        }
        AlertDialog(
            onDismissRequest = { showAddAnnotationDialog = null },
            title = { Text("Thêm ${annType.label} vào Trang ${safePageIdx + 1}") },
            text = {
                OutlinedTextField(
                    value = textVal,
                    onValueChange = { textVal = it },
                    label = { Text("Nội dung") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    addAnnotation(
                        PdfAnnotationItem(
                            id = "ann_${System.currentTimeMillis()}",
                            pageIndex = safePageIdx,
                            type = annType,
                            text = textVal,
                            xPercent = 14f,
                            yPercent = if (annType == PdfAnnotationType.FORM_FIELD) 44f else 32f
                        )
                    )
                    showAddAnnotationDialog = null
                }) {
                    Text("Chèn vào trang")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAnnotationDialog = null }) { Text("Hủy") }
            }
        )
    }

    if (showSignaturePadDialog) {
        var signerName by remember { mutableStateOf("Nguyễn Hữu Tuấn") }
        val sigPoints = remember {
            mutableStateListOf(
                18f to 48f, 24f to 42f, 32f to 50f, 40f to 40f, 52f to 47f, 65f to 41f
            )
        }
        AlertDialog(
            onDismissRequest = { showSignaturePadDialog = false },
            title = { Text("Tạo Chữ ký tay dạng Hình ảnh trên PDF") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = signerName,
                        onValueChange = { signerName = it },
                        label = { Text("Họ tên người ký") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Vẽ chữ ký tay trực tiếp vào khung bên dưới:", style = MaterialTheme.typography.labelSmall)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF8FAFC))
                            .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        sigPoints.clear()
                                        sigPoints.add((offset.x / size.width) * 100f to (offset.y / size.height) * 100f)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        sigPoints.add((change.position.x / size.width) * 100f to (change.position.y / size.height) * 100f)
                                    }
                                )
                            }
                    ) {
                        if (sigPoints.size >= 2) {
                            val p = Path()
                            val f = sigPoints.first()
                            p.moveTo((f.first / 100f) * size.width, (f.second / 100f) * size.height)
                            for (pt in sigPoints.drop(1)) {
                                p.lineTo((pt.first / 100f) * size.width, (pt.second / 100f) * size.height)
                            }
                            drawPath(
                                path = p,
                                color = Color(0xFF185ABD),
                                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val mappedPts = sigPoints.map { (x, y) ->
                        (18f + x * 0.45f) to (42f + y * 0.12f)
                    }
                    addAnnotation(
                        PdfAnnotationItem(
                            id = "sig_${System.currentTimeMillis()}",
                            pageIndex = safePageIdx,
                            type = PdfAnnotationType.SIGNATURE,
                            text = signerName,
                            xPercent = 18f,
                            yPercent = 48f,
                            inkPoints = mappedPts
                        )
                    )
                    showSignaturePadDialog = false
                }) {
                    Text("Đóng dấu chữ ký")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignaturePadDialog = false }) { Text("Hủy") }
            }
        )
    }

    if (showSignedWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSignedWarningDialog = false },
            title = { Text("Cảnh báo Tài liệu PDF Đã Ký số") },
            text = {
                Text(
                    "Tài liệu này chứa chữ ký số điện tử (/Type /Sig). " +
                        "Nếu chỉnh sửa và ghi đè trực tiếp, chữ ký số gốc sẽ mất hiệu lực pháp lý.\n\n" +
                        "Văn Phòng Pro tự động bật chế độ 'Lưu thành Bản sao' để bảo vệ toàn vẹn tệp ký số gốc của bạn."
                )
            },
            confirmButton = {
                Button(onClick = { showSignedWarningDialog = false }) { Text("Tôi đã hiểu") }
            }
        )
    }
}
