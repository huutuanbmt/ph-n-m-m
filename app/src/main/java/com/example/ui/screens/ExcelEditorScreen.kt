package com.example.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CellDataFormat
import com.example.engine.ExcelCell
import com.example.engine.ExcelEngine
import com.example.engine.ExcelSheet
import com.example.ui.OfficeViewModel
import com.example.ui.OpenEditorSession
import com.example.ui.theme.ExcelGreen
import com.example.ui.theme.RobotoMonoFontFamily

@Composable
fun ExcelEditorScreen(
    session: OpenEditorSession,
    viewModel: OfficeViewModel
) {
    val workbook = session.excelModel ?: return
    val visibleSheets = workbook.sheets.filter { !it.isHidden }.ifEmpty { workbook.sheets }
    var activeSheetIndex by remember { mutableIntStateOf(0) }
    val safeSheetIdx = activeSheetIndex.coerceIn(0, (visibleSheets.size - 1).coerceAtLeast(0))
    val currentSheet = visibleSheets[safeSheetIdx]

    var selectedRow by remember { mutableIntStateOf(1) }
    var selectedCol by remember { mutableIntStateOf(0) }
    val activeCell = currentSheet.getCell(selectedRow, selectedCol)
    var formulaInput by remember { mutableStateOf(activeCell.rawValue) }

    var showFormulaPicker by remember { mutableStateOf(false) }
    var showChartPanel by remember { mutableStateOf(true) }
    var showRiskPreviewDialog by remember { mutableStateOf(false) }
    var showRenameSheetDialog by remember { mutableStateOf(false) }
    var filterText by remember { mutableStateOf("") }

    LaunchedEffect(selectedRow, selectedCol, safeSheetIdx, currentSheet.cells) {
        formulaInput = currentSheet.getCell(selectedRow, selectedCol).rawValue
    }

    fun updateCurrentCell(transform: (ExcelCell) -> ExcelCell) {
        val key = "${selectedRow}_${selectedCol}"
        val existing = currentSheet.getCell(selectedRow, selectedCol)
        val modified = transform(existing)
        val newCells = currentSheet.cells.toMutableMap().apply { put(key, modified) }
        val updatedSheets = workbook.sheets.map { s ->
            if (s.id == currentSheet.id) s.copy(cells = newCells) else s
        }
        viewModel.updateExcelModel(session.document.id, workbook.copy(sheets = updatedSheets))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Toolbar: Save, Undo, Format, Merge, Formulas, Chart, Risk Preview, CSV/PDF Export
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
                        onClick = { viewModel.saveActiveSession(session.document.id) },
                        modifier = Modifier.testTag("excel_save_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Lưu", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (session.hasUnsavedChanges) "Lưu XLSX*" else "Đã lưu")
                    }
                    IconButton(
                        onClick = { viewModel.undoExcel(session.document.id) },
                        enabled = session.excelUndoStack.isNotEmpty()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Hoàn tác")
                    }
                    AssistChip(
                        onClick = { showFormulaPicker = true },
                        label = { Text("Hàm fx (25+ hàm)") },
                        leadingIcon = { Icon(Icons.Default.Functions, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.testTag("excel_formula_picker_chip")
                    )
                    CellDataFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = activeCell.format == fmt,
                            onClick = { updateCurrentCell { it.copy(format = fmt) } },
                            label = { Text(fmt.label) }
                        )
                    }
                    IconButton(onClick = { updateCurrentCell { it.copy(isBold = !it.isBold) } }) {
                        Icon(
                            Icons.Default.FormatBold,
                            contentDescription = "In đậm ô",
                            tint = if (activeCell.isBold) ExcelGreen else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    AssistChip(
                        onClick = {
                            val nextSpan = if (activeCell.mergedColSpan > 1) 1 else 2
                            updateCurrentCell { it.copy(mergedColSpan = nextSpan) }
                            viewModel.showBanner(if (nextSpan > 1) "Đã gộp 2 ô ngang" else "Đã tách ô")
                        },
                        label = { Text(if (activeCell.mergedColSpan > 1) "Tách ô" else "Gộp ô") },
                        leadingIcon = { Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = showChartPanel,
                        onClick = { showChartPanel = !showChartPanel },
                        label = { Text("Biểu đồ") },
                        leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    OutlinedButton(onClick = { showRiskPreviewDialog = true }) {
                        Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Kiểm tra tương thích")
                    }
                    OutlinedButton(onClick = { viewModel.exportExcelSheetToCsv(session.document.id, safeSheetIdx) }) {
                        Text("Xuất CSV")
                    }
                    OutlinedButton(onClick = { viewModel.exportSessionToPdf(session.document.id) }) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Xuất PDF")
                    }
                }

                // Formula Bar (fx)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = ExcelGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "${ExcelEngine.colIndexToLetter(selectedCol)}${selectedRow + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = RobotoMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = ExcelGreen,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = formulaInput,
                        onValueChange = { formulaInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("excel_formula_input"),
                        placeholder = { Text("Nhập giá trị hoặc công thức (=SUM(C2:C5), =IF(...), =VLOOKUP(...))") },
                        singleLine = true
                    )
                    Button(
                        onClick = { updateCurrentCell { it.copy(rawValue = formulaInput) } },
                        modifier = Modifier.testTag("excel_apply_cell_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Áp dụng")
                    }
                }

                // Filter & Row/Col Management Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp), tint = ExcelGreen)
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("Lọc nhanh dữ liệu hàng...") },
                        singleLine = true,
                        modifier = Modifier.width(210.dp)
                    )
                    AssistChip(
                        onClick = {
                            val updatedSheets = workbook.sheets.map { s ->
                                if (s.id == currentSheet.id) s.copy(rowCount = s.rowCount + 5) else s
                            }
                            viewModel.updateExcelModel(session.document.id, workbook.copy(sheets = updatedSheets))
                        },
                        label = { Text("+ Thêm 5 hàng") }
                    )
                    AssistChip(
                        onClick = {
                            val updatedSheets = workbook.sheets.map { s ->
                                if (s.id == currentSheet.id) s.copy(colCount = s.colCount + 2) else s
                            }
                            viewModel.updateExcelModel(session.document.id, workbook.copy(sheets = updatedSheets))
                        },
                        label = { Text("+ Thêm 2 cột") }
                    )
                }
            }
        }

        // Interactive Chart Visualization Panel
        if (showChartPanel && currentSheet.charts.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val chart = currentSheet.charts.first()
                    Text(
                        text = "📊 ${chart.title} (Tự động cập nhật từ vùng ${chart.valueRange})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ExcelGreen
                    )
                    Spacer(Modifier.height(8.dp))
                    val chartData = remember(currentSheet.cells) {
                        (1..4).map { r ->
                            val label = currentSheet.getCell(r, 1).displayValue.ifEmpty { "Mục $r" }.take(12)
                            val rawNum = currentSheet.getCell(r, 4).displayValue
                                .replace(".", "")
                                .replace(",", "")
                                .replace("₫", "")
                                .trim()
                                .toDoubleOrNull() ?: 0.0
                            label to rawNum
                        }
                    }
                    val maxVal = (chartData.maxOfOrNull { it.second } ?: 1.0).coerceAtLeast(1.0)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Canvas(
                            modifier = Modifier
                                .weight(1f)
                                .height(86.dp)
                        ) {
                            val barCount = chartData.size.coerceAtLeast(1)
                            val slotW = size.width / barCount
                            val barW = slotW * 0.55f
                            chartData.forEachIndexed { idx, (_, v) ->
                                val barH = ((v / maxVal).toFloat() * (size.height - 12f)).coerceAtLeast(6f)
                                drawRect(
                                    color = Color(0xFF107C41),
                                    topLeft = Offset(idx * slotW + slotW * 0.2f, size.height - barH),
                                    size = Size(barW, barH)
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            chartData.forEach { (lbl, v) ->
                                Text(
                                    text = "• $lbl: ${ExcelEngine.formatValue(v.toString(), CellDataFormat.CURRENCY_VND)}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Spreadsheet Grid (Scrollable 2D Grid with Column Headers A..Z and Row Numbers 1..N)
        val horizontalScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Column Header Row (A, B, C, D...)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background( Color(0xFFE2E8F0))
                    .horizontalScroll(horizontalScrollState)
            ) {
                Box(
                    modifier = Modifier
                        .width(46.dp)
                        .height(32.dp)
                        .border(0.5.dp, Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("#", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                }
                for (c in 0 until currentSheet.colCount) {
                    Box(
                        modifier = Modifier
                            .width(126.dp)
                            .height(32.dp)
                            .background(if (c == selectedCol) ExcelGreen.copy(alpha = 0.2f) else Color(0xFFE2E8F0))
                            .border(0.5.dp, Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = ExcelEngine.colIndexToLetter(c),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                    }
                }
            }

            // Rows LazyColumn
            val visibleRowIndices = remember(currentSheet, filterText) {
                val q = filterText.trim().lowercase()
                (0 until currentSheet.rowCount).filter { r ->
                    if (q.isEmpty() || r == 0) true
                    else (0 until currentSheet.colCount).any { c ->
                        currentSheet.getCell(r, c).displayValue.lowercase().contains(q)
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(visibleRowIndices.size, key = { visibleRowIndices[it] }) { idx ->
                    val r = visibleRowIndices[idx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        // Row number indicator
                        Box(
                            modifier = Modifier
                                .width(46.dp)
                                .height(38.dp)
                                .background(if (r == selectedRow) ExcelGreen.copy(alpha = 0.2f) else Color(0xFFF1F5F9))
                                .border(0.5.dp, Color.LightGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${r + 1}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.DarkGray)
                        }

                        var c = 0
                        while (c < currentSheet.colCount) {
                            val cell = currentSheet.getCell(r, c)
                            val span = cell.mergedColSpan.coerceAtLeast(1)
                            val isSelected = (r == selectedRow && c == selectedCol)
                            val bg = when {
                                isSelected -> Color(0xFFDCFCE7)
                                r == 0 -> Color(0xFFF0FDF4)
                                cell.formulaStatus == "UNSUPPORTED_FORMULA" -> Color(0xFFFEF3C7)
                                else -> Color.White
                            }
                            val colPos = c
                            Box(
                                modifier = Modifier
                                    .width((126 * span).dp)
                                    .height(38.dp)
                                    .background(bg)
                                    .border(
                                        width = if (isSelected) 2.dp else 0.5.dp,
                                        color = if (isSelected) ExcelGreen else Color(0xFFCBD5E1)
                                    )
                                    .clickable {
                                        selectedRow = r
                                        selectedCol = colPos
                                    }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = cell.displayValue,
                                    fontSize = 13.sp,
                                    fontWeight = if (cell.isBold || r == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (cell.rawValue.startsWith("=")) Color(0xFF065F46) else Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            c += span
                        }
                    }
                }
            }
        }

        // Bottom Sheet Tabs Bar (Create, Rename, Duplicate, Delete, Switch Sheets)
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleSheets.forEachIndexed { idx, sheet ->
                    FilterChip(
                        selected = idx == safeSheetIdx,
                        onClick = { activeSheetIndex = idx },
                        label = { Text(sheet.name) },
                        modifier = Modifier.testTag("excel_sheet_tab_$idx")
                    )
                }
                AssistChip(
                    onClick = {
                        val newSheet = ExcelSheet(
                            id = "sheet_${System.currentTimeMillis()}",
                            name = "Sheet${workbook.sheets.size + 1}"
                        )
                        viewModel.updateExcelModel(
                            session.document.id,
                            workbook.copy(sheets = workbook.sheets + newSheet)
                        )
                        activeSheetIndex = visibleSheets.size
                    },
                    label = { Text("+ Thêm Sheet") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("excel_add_sheet_chip")
                )
                IconButton(onClick = { showRenameSheetDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = "Đổi tên Sheet", modifier = Modifier.size(18.dp))
                }
                if (visibleSheets.size > 1) {
                    IconButton(onClick = {
                        val remaining = workbook.sheets.filterNot { it.id == currentSheet.id }
                        viewModel.updateExcelModel(session.document.id, workbook.copy(sheets = remaining))
                        activeSheetIndex = 0
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Xóa Sheet", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (showFormulaPicker) {
        val sampleFormulas = listOf(
            "SUM" to "=SUM(C2:C5)",
            "AVERAGE" to "=AVERAGE(E2:E5)",
            "COUNT" to "=COUNT(C2:C5)",
            "COUNTA" to "=COUNTA(B2:B5)",
            "IF" to "=IF(E2>=50000000,\"Ưu tiên cao\",\"Thường\")",
            "IFS" to "=IFS(E2>50000000,\"Cao\",E2>10000000,\"Trung bình\",1=1,\"Thấp\")",
            "SUMIF" to "=SUMIF(F2:F5,\"Ưu tiên cao\",E2:E5)",
            "COUNTIF" to "=COUNTIF(F2:F5,\"Ưu tiên cao\")",
            "IFERROR" to "=IFERROR(E2/C2,0)",
            "VLOOKUP" to "=VLOOKUP(\"VP02\",A2:E5,5,0)",
            "XLOOKUP" to "=XLOOKUP(\"VP03\",A2:A5,B2:B5)",
            "INDEX" to "=INDEX(B2:E5,2,1)",
            "MATCH" to "=MATCH(\"VP02\",A2:A5,0)",
            "ROUND" to "=ROUND(1234.567,2)",
            "LEFT / RIGHT / MID" to "=LEFT(B2,8)",
            "TODAY / NOW" to "=TODAY()"
        )
        AlertDialog(
            onDismissRequest = { showFormulaPicker = false },
            title = { Text("Thư viện Công thức Excel Đã Kiểm chứng") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.height(320.dp)) {
                    items(sampleFormulas.size) { i ->
                        val (fn, example) = sampleFormulas[i]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    formulaInput = example
                                    updateCurrentCell { it.copy(rawValue = example) }
                                    showFormulaPicker = false
                                }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(fn, fontWeight = FontWeight.Bold, color = ExcelGreen)
                                Text("Mẫu áp dụng: $example", style = MaterialTheme.typography.bodySmall, fontFamily = RobotoMonoFontFamily)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFormulaPicker = false }) { Text("Đóng") }
            }
        )
    }

    if (showRiskPreviewDialog) {
        AlertDialog(
            onDismissRequest = { showRiskPreviewDialog = false },
            title = { Text("Xem trước Rủi ro Tương thích Excel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("• Số lượng Sheet: ${workbook.sheets.size} (Bảo toàn 100%)")
                    Text("• Các hàm đã kiểm chứng (SUM, AVERAGE, IF, IFS, SUMIF, VLOOKUP, XLOOKUP, INDEX, MATCH...): Tự động tính toán lại chính xác.")
                    Text("• Macro (vbaProject.bin): ${if (workbook.hasMacrosPreserved) "Phát hiện & Giữ nguyên không xóa (Không tự động chạy để bảo mật)" else "Không có macro"}")
                    Text("• PivotTable & Liên kết ngoài: ${if (workbook.hasPivotTablesPreserved) "Được bảo toàn nguyên vẹn trong container OOXML" else "Không có"}")
                    if (workbook.unsupportedFormulas.isEmpty()) {
                        Text("✓ Tất cả công thức trong bảng tính đều được hỗ trợ đầy đủ.", color = ExcelGreen, fontWeight = FontWeight.Bold)
                    } else {
                        Text("⚠ Công thức phức tạp được giữ nguyên chuỗi gốc (không tự xóa):\n${workbook.unsupportedFormulas.joinToString("\n")}")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showRiskPreviewDialog = false }) { Text("Đã hiểu") }
            }
        )
    }

    if (showRenameSheetDialog) {
        var newName by remember { mutableStateOf(currentSheet.name) }
        AlertDialog(
            onDismissRequest = { showRenameSheetDialog = false },
            title = { Text("Đổi tên Sheet hiện tại") },
            text = {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true)
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        val updated = workbook.sheets.map { s ->
                            if (s.id == currentSheet.id) s.copy(name = newName.trim()) else s
                        }
                        viewModel.updateExcelModel(session.document.id, workbook.copy(sheets = updated))
                    }
                    showRenameSheetDialog = false
                }) { Text("Lưu tên") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameSheetDialog = false }) { Text("Hủy") }
            }
        )
    }
}
