package com.example.engine

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.pow
import kotlin.math.roundToLong

enum class CellDataFormat(val label: String) {
    GENERAL("Chung"),
    NUMBER("Số thập phân (1,234.56)"),
    CURRENCY_VND("Tiền tệ VNĐ (1.250.000 ₫)"),
    PERCENT("Phần trăm (12.5%)"),
    DATE("Ngày tháng (DD/MM/YYYY)")
}

data class ExcelCell(
    val row: Int, // 0-indexed
    val col: Int, // 0-indexed
    val rawValue: String = "", // Formula starts with '=' or raw text/number
    val displayValue: String = "",
    val format: CellDataFormat = CellDataFormat.GENERAL,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val textColorHex: String = "000000",
    val bgColorHex: String = "",
    val hasBorder: Boolean = true,
    val align: String = "LEFT", // LEFT, CENTER, RIGHT
    val mergedColSpan: Int = 1,
    val mergedRowSpan: Int = 1,
    val validationRule: String = "", // e.g., ">0", "LIST:Đạt,Chưa đạt"
    val formulaStatus: String = "OK" // OK, SUPPORTED_FORMULA, UNSUPPORTED_FORMULA, ERROR
)

data class ExcelChartSpec(
    val id: String,
    val title: String,
    val chartType: String = "BAR", // BAR, LINE, PIE
    val labelRange: String = "A2:A5",
    val valueRange: String = "B2:B5"
)

data class ExcelSheet(
    val id: String,
    val name: String,
    val isHidden: Boolean = false,
    val frozenRows: Int = 1,
    val frozenCols: Int = 0,
    val rowCount: Int = 30,
    val colCount: Int = 10,
    val cells: Map<String, ExcelCell> = emptyMap(), // key: "r_c" e.g. "0_0"
    val charts: List<ExcelChartSpec> = emptyList(),
    val filterColumn: Int = -1,
    val filterQuery: String = "",
    val conditionalRuleGreaterThan: Double? = null
) {
    fun getCell(r: Int, c: Int): ExcelCell = cells["${r}_${c}"] ?: ExcelCell(row = r, col = c)
}

data class ExcelWorkbookModel(
    val title: String,
    val sheets: List<ExcelSheet>,
    val hasMacrosPreserved: Boolean = false,
    val hasPivotTablesPreserved: Boolean = false,
    val unsupportedFormulas: List<String> = emptyList(),
    val originalZipEntries: Map<String, ByteArray> = emptyMap()
)

object ExcelEngine {

    val supportedFunctions = setOf(
        "SUM", "AVERAGE", "COUNT", "COUNTA", "MIN", "MAX",
        "IF", "IFS", "SUMIF", "SUMIFS", "COUNTIF", "COUNTIFS",
        "IFERROR", "VLOOKUP", "XLOOKUP", "INDEX", "MATCH",
        "LEFT", "RIGHT", "MID", "LEN", "ROUND", "ABS", "TODAY", "NOW"
    )

    fun colIndexToLetter(col: Int): String {
        var c = col
        val sb = StringBuilder()
        while (c >= 0) {
            sb.insert(0, ('A' + (c % 26)))
            c = (c / 26) - 1
        }
        return sb.toString()
    }

    fun colLetterToIndex(letters: String): Int {
        var result = 0
        for (ch in letters.uppercase()) {
            if (ch in 'A'..'Z') {
                result = result * 26 + (ch - 'A' + 1)
            }
        }
        return (result - 1).coerceAtLeast(0)
    }

    fun parseCellRef(ref: String): Pair<Int, Int>? {
        val clean = ref.trim().replace("$", "")
        val match = Regex("^([A-Za-z]+)(\\d+)$").find(clean) ?: return null
        val col = colLetterToIndex(match.groupValues[1])
        val row = (match.groupValues[2].toIntOrNull() ?: 1) - 1
        return row.coerceAtLeast(0) to col.coerceAtLeast(0)
    }

    fun openWorkbook(file: File, fallbackTitle: String = file.nameWithoutExtension): ExcelWorkbookModel {
        if (!file.exists()) return createDefaultWorkbook(fallbackTitle)
        return when (file.extension.lowercase()) {
            "csv" -> parseCsvFile(file, fallbackTitle)
            else -> parseXlsxFile(file, fallbackTitle)
        }
    }

    private fun parseCsvFile(file: File, title: String): ExcelWorkbookModel {
        val lines = file.readLines(Charsets.UTF_8)
        val cellMap = mutableMapOf<String, ExcelCell>()
        var maxCols = 6
        lines.forEachIndexed { r, line ->
            val cols = line.split(",")
            maxCols = maxOf(maxCols, cols.size)
            cols.forEachIndexed { c, raw ->
                val trimmed = raw.trim().removeSurrounding("\"")
                cellMap["${r}_${c}"] = ExcelCell(
                    row = r,
                    col = c,
                    rawValue = trimmed,
                    displayValue = trimmed,
                    isBold = (r == 0)
                )
            }
        }
        val sheet = ExcelSheet(
            id = "sheet_1",
            name = "Sheet1",
            rowCount = maxOf(25, lines.size + 5),
            colCount = maxOf(8, maxCols + 2),
            cells = cellMap
        )
        return recalculateWorkbook(ExcelWorkbookModel(title = title, sheets = listOf(sheet)))
    }

    private fun parseXlsxFile(file: File, title: String): ExcelWorkbookModel {
        val entries = mutableMapOf<String, ByteArray>()
        try {
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val baos = ByteArrayOutputStream()
                        zis.copyTo(baos)
                        entries[entry.name] = baos.toByteArray()
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            return createDefaultWorkbook(title)
        }

        if (entries.isEmpty()) return createDefaultWorkbook(title)

        val hasMacros = entries.keys.any { it.contains("vbaProject", ignoreCase = true) }
        val hasPivot = entries.keys.any { it.contains("pivotTable", ignoreCase = true) }

        val sharedStrings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { ssBytes ->
            try {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(ByteArrayInputStream(ssBytes), "UTF-8")
                var ev = parser.eventType
                var currentSi: StringBuilder? = null
                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.substringAfterLast(':') ?: ""
                    if (ev == XmlPullParser.START_TAG && tag == "si") {
                        currentSi = StringBuilder()
                    } else if (ev == XmlPullParser.START_TAG && tag == "t" && currentSi != null) {
                        currentSi.append(parser.nextText())
                    } else if (ev == XmlPullParser.END_TAG && tag == "si" && currentSi != null) {
                        sharedStrings.add(currentSi.toString())
                        currentSi = null
                    }
                    ev = parser.next()
                }
            } catch (_: Exception) {
            }
        }

        val sheetNames = mutableListOf<Pair<String, Boolean>>()
        entries["xl/workbook.xml"]?.let { wbBytes ->
            try {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(ByteArrayInputStream(wbBytes), "UTF-8")
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && parser.name.substringAfterLast(':') == "sheet") {
                        val sName = parser.getAttributeValue(null, "name") ?: "Sheet${sheetNames.size + 1}"
                        val state = parser.getAttributeValue(null, "state") ?: ""
                        sheetNames.add(sName to (state == "hidden"))
                    }
                    ev = parser.next()
                }
            } catch (_: Exception) {
            }
        }

        val sheetFiles = entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sorted()

        val parsedSheets = mutableListOf<ExcelSheet>()
        for ((idx, sheetPath) in sheetFiles.withIndex()) {
            val sBytes = entries[sheetPath] ?: continue
            val (sName, isHidden) = sheetNames.getOrElse(idx) { "Sheet${idx + 1}" to false }
            val cells = mutableMapOf<String, ExcelCell>()
            var maxRow = 24
            var maxCol = 8

            try {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(ByteArrayInputStream(sBytes), "UTF-8")
                var ev = parser.eventType
                var curRef = ""
                var curType = ""
                var curFormula = ""
                var curVal = ""
                var curFormat = CellDataFormat.GENERAL
                var curBold = false

                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.substringAfterLast(':') ?: ""
                    when (ev) {
                        XmlPullParser.START_TAG -> {
                            when (tag) {
                                "c" -> {
                                    curRef = parser.getAttributeValue(null, "r") ?: ""
                                    curType = parser.getAttributeValue(null, "t") ?: ""
                                    val fmtAttr = parser.getAttributeValue(null, "vppFmt") ?: ""
                                    curFormat = runCatching { CellDataFormat.valueOf(fmtAttr) }
                                        .getOrDefault(CellDataFormat.GENERAL)
                                    curBold = parser.getAttributeValue(null, "vppBold") == "1"
                                    curFormula = ""
                                    curVal = ""
                                }
                                "f" -> curFormula = parser.nextText()
                                "v", "t" -> curVal = parser.nextText()
                                "mergeCell" -> {
                                    val refRange = parser.getAttributeValue(null, "ref") ?: ""
                                    val parts = refRange.split(":")
                                    if (parts.size == 2) {
                                        val start = parseCellRef(parts[0])
                                        val end = parseCellRef(parts[1])
                                        if (start != null && end != null) {
                                            val key = "${start.first}_${start.second}"
                                            val existing = cells[key] ?: ExcelCell(row = start.first, col = start.second)
                                            cells[key] = existing.copy(
                                                mergedRowSpan = (end.first - start.first + 1).coerceAtLeast(1),
                                                mergedColSpan = (end.second - start.second + 1).coerceAtLeast(1)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tag == "c" && curRef.isNotEmpty()) {
                                val rc = parseCellRef(curRef)
                                if (rc != null) {
                                    val (r, c) = rc
                                    maxRow = maxOf(maxRow, r + 5)
                                    maxCol = maxOf(maxCol, c + 2)
                                    val resolvedVal = if (curType == "s") {
                                        val sIdx = curVal.toIntOrNull() ?: -1
                                        sharedStrings.getOrElse(sIdx) { curVal }
                                    } else {
                                        curVal
                                    }
                                    val raw = if (curFormula.isNotEmpty()) "=$curFormula" else resolvedVal
                                    cells["${r}_${c}"] = ExcelCell(
                                        row = r,
                                        col = c,
                                        rawValue = raw,
                                        displayValue = resolvedVal,
                                        format = curFormat,
                                        isBold = curBold || (r == 0)
                                    )
                                }
                            }
                        }
                    }
                    ev = parser.next()
                }
            } catch (_: Exception) {
            }

            parsedSheets.add(
                ExcelSheet(
                    id = "sheet_${idx + 1}",
                    name = sName,
                    isHidden = isHidden,
                    rowCount = maxRow,
                    colCount = maxCol,
                    cells = cells
                )
            )
        }

        val initialModel = ExcelWorkbookModel(
            title = title,
            sheets = parsedSheets.ifEmpty { createDefaultWorkbook(title).sheets },
            hasMacrosPreserved = hasMacros,
            hasPivotTablesPreserved = hasPivot,
            originalZipEntries = entries
        )
        return recalculateWorkbook(initialModel)
    }

    /**
     * Evaluates all formulas across all sheets in the workbook and flags any unsupported formulas
     * while preserving their raw formula expressions untouched.
     */
    fun recalculateWorkbook(workbook: ExcelWorkbookModel): ExcelWorkbookModel {
        val unsupported = mutableSetOf<String>()
        val updatedSheets = workbook.sheets.map { sheet ->
            val updatedCells = sheet.cells.toMutableMap()
            for ((key, cell) in sheet.cells) {
                if (cell.rawValue.startsWith("=")) {
                    val evalResult = evaluateFormula(cell.rawValue, sheet, workbook.sheets)
                    if (evalResult.status == "UNSUPPORTED_FORMULA") {
                        unsupported.add("${sheet.name}!${colIndexToLetter(cell.col)}${cell.row + 1}: ${cell.rawValue}")
                    }
                    updatedCells[key] = cell.copy(
                        displayValue = formatValue(evalResult.value, cell.format),
                        formulaStatus = evalResult.status
                    )
                } else {
                    updatedCells[key] = cell.copy(
                        displayValue = formatValue(cell.rawValue, cell.format),
                        formulaStatus = "OK"
                    )
                }
            }
            sheet.copy(cells = updatedCells)
        }
        return workbook.copy(
            sheets = updatedSheets,
            unsupportedFormulas = unsupported.toList()
        )
    }

    data class FormulaEvalResult(val value: String, val status: String)

    fun evaluateFormula(
        rawFormula: String,
        currentSheet: ExcelSheet,
        allSheets: List<ExcelSheet>
    ): FormulaEvalResult {
        val expr = rawFormula.removePrefix("=").trim()
        if (expr.isEmpty()) return FormulaEvalResult("", "OK")

        return try {
            val upper = expr.uppercase()
            val fnMatch = Regex("^([A-Z0-9_]+)\\((.*)\\)$").find(expr.trim())
            if (fnMatch != null) {
                val fnName = fnMatch.groupValues[1].uppercase()
                val argsStr = fnMatch.groupValues[2]
                val args = splitTopLevelArgs(argsStr)

                if (fnName !in supportedFunctions) {
                    val fallbackDisplay = currentSheet.cells.values
                        .find { it.rawValue == rawFormula }
                        ?.displayValue
                        ?.takeIf { it.isNotBlank() && !it.startsWith("=") }
                        ?: "#COMPAT($fnName)"
                    return FormulaEvalResult(fallbackDisplay, "UNSUPPORTED_FORMULA")
                }

                val resultStr = when (fnName) {
                    "SUM" -> {
                        val nums = args.flatMap { resolveNumericTokens(it, currentSheet, allSheets) }
                        formatNumberClean(nums.sum())
                    }
                    "AVERAGE" -> {
                        val nums = args.flatMap { resolveNumericTokens(it, currentSheet, allSheets) }
                        if (nums.isEmpty()) "0" else formatNumberClean(nums.average())
                    }
                    "COUNT" -> {
                        val nums = args.flatMap { resolveNumericTokens(it, currentSheet, allSheets) }
                        nums.size.toString()
                    }
                    "COUNTA" -> {
                        val vals = args.flatMap { resolveStringTokens(it, currentSheet, allSheets) }
                        vals.count { it.isNotBlank() }.toString()
                    }
                    "MIN" -> {
                        val nums = args.flatMap { resolveNumericTokens(it, currentSheet, allSheets) }
                        if (nums.isEmpty()) "0" else formatNumberClean(nums.minOrNull() ?: 0.0)
                    }
                    "MAX" -> {
                        val nums = args.flatMap { resolveNumericTokens(it, currentSheet, allSheets) }
                        if (nums.isEmpty()) "0" else formatNumberClean(nums.maxOrNull() ?: 0.0)
                    }
                    "IF" -> {
                        if (args.size >= 2) {
                            val cond = evaluateCondition(args[0], currentSheet, allSheets)
                            val branch = if (cond) args[1] else args.getOrElse(2) { "\"\"" }
                            resolveSingleScalar(branch, currentSheet, allSheets)
                        } else "#VALUE!"
                    }
                    "IFS" -> {
                        var matchedVal: String? = null
                        var i = 0
                        while (i + 1 < args.size) {
                            if (evaluateCondition(args[i], currentSheet, allSheets)) {
                                matchedVal = resolveSingleScalar(args[i + 1], currentSheet, allSheets)
                                break
                            }
                            i += 2
                        }
                        matchedVal ?: "#N/A"
                    }
                    "IFERROR" -> {
                        if (args.size >= 2) {
                            val primary = resolveSingleScalar(args[0], currentSheet, allSheets)
                            if (primary.startsWith("#")) {
                                resolveSingleScalar(args[1], currentSheet, allSheets)
                            } else primary
                        } else "#VALUE!"
                    }
                    "SUMIF" -> {
                        if (args.size >= 2) {
                            val rangeVals = resolveStringTokens(args[0], currentSheet, allSheets)
                            val criterion = args[1].trim().removeSurrounding("\"")
                            val sumVals = if (args.size >= 3) {
                                resolveNumericTokens(args[2], currentSheet, allSheets, keepZerosForNonNumeric = true)
                            } else {
                                rangeVals.map { it.toDoubleOrNull() ?: 0.0 }
                            }
                            var total = 0.0
                            for (i in rangeVals.indices) {
                                if (matchesCriterion(rangeVals[i], criterion)) {
                                    total += sumVals.getOrElse(i) { 0.0 }
                                }
                            }
                            formatNumberClean(total)
                        } else "0"
                    }
                    "SUMIFS" -> {
                        if (args.size >= 3) {
                            val sumVals = resolveNumericTokens(args[0], currentSheet, allSheets, keepZerosForNonNumeric = true)
                            val criteriaRange = resolveStringTokens(args[1], currentSheet, allSheets)
                            val criterion = args[2].trim().removeSurrounding("\"")
                            var total = 0.0
                            for (i in sumVals.indices) {
                                if (matchesCriterion(criteriaRange.getOrElse(i) { "" }, criterion)) {
                                    total += sumVals[i]
                                }
                            }
                            formatNumberClean(total)
                        } else "0"
                    }
                    "COUNTIF", "COUNTIFS" -> {
                        if (args.size >= 2) {
                            val rangeVals = resolveStringTokens(args[0], currentSheet, allSheets)
                            val criterion = args[1].trim().removeSurrounding("\"")
                            rangeVals.count { matchesCriterion(it, criterion) }.toString()
                        } else "0"
                    }
                    "VLOOKUP" -> {
                        if (args.size >= 3) {
                            val lookupKey = resolveSingleScalar(args[0], currentSheet, allSheets)
                            val colIdx = (resolveSingleScalar(args[2], currentSheet, allSheets).toIntOrNull() ?: 1) - 1
                            val tableMatrix = resolveRangeMatrix(args[1], currentSheet, allSheets)
                            val foundRow = tableMatrix.find { row ->
                                row.firstOrNull()?.equals(lookupKey, ignoreCase = true) == true
                            }
                            foundRow?.getOrNull(colIdx) ?: "#N/A"
                        } else "#N/A"
                    }
                    "XLOOKUP" -> {
                        if (args.size >= 3) {
                            val lookupKey = resolveSingleScalar(args[0], currentSheet, allSheets)
                            val lookupList = resolveStringTokens(args[1], currentSheet, allSheets)
                            val returnList = resolveStringTokens(args[2], currentSheet, allSheets)
                            val idx = lookupList.indexOfFirst { it.equals(lookupKey, ignoreCase = true) }
                            if (idx >= 0) returnList.getOrElse(idx) { "#N/A" }
                            else args.getOrNull(3)?.let { resolveSingleScalar(it, currentSheet, allSheets) } ?: "#N/A"
                        } else "#N/A"
                    }
                    "INDEX" -> {
                        if (args.size >= 2) {
                            val matrix = resolveRangeMatrix(args[0], currentSheet, allSheets)
                            val rIdx = (resolveSingleScalar(args[1], currentSheet, allSheets).toIntOrNull() ?: 1) - 1
                            val cIdx = (args.getOrNull(2)?.let { resolveSingleScalar(it, currentSheet, allSheets).toIntOrNull() } ?: 1) - 1
                            matrix.getOrNull(rIdx)?.getOrNull(cIdx) ?: "#REF!"
                        } else "#REF!"
                    }
                    "MATCH" -> {
                        if (args.size >= 2) {
                            val key = resolveSingleScalar(args[0], currentSheet, allSheets)
                            val list = resolveStringTokens(args[1], currentSheet, allSheets)
                            val idx = list.indexOfFirst { it.equals(key, ignoreCase = true) }
                            if (idx >= 0) (idx + 1).toString() else "#N/A"
                        } else "#N/A"
                    }
                    "LEFT" -> {
                        val s = resolveSingleScalar(args.getOrElse(0) { "" }, currentSheet, allSheets)
                        val n = resolveSingleScalar(args.getOrElse(1) { "1" }, currentSheet, allSheets).toIntOrNull() ?: 1
                        s.take(n.coerceAtLeast(0))
                    }
                    "RIGHT" -> {
                        val s = resolveSingleScalar(args.getOrElse(0) { "" }, currentSheet, allSheets)
                        val n = resolveSingleScalar(args.getOrElse(1) { "1" }, currentSheet, allSheets).toIntOrNull() ?: 1
                        s.takeLast(n.coerceAtLeast(0))
                    }
                    "MID" -> {
                        val s = resolveSingleScalar(args.getOrElse(0) { "" }, currentSheet, allSheets)
                        val start = ((resolveSingleScalar(args.getOrElse(1) { "1" }, currentSheet, allSheets).toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
                        val len = (resolveSingleScalar(args.getOrElse(2) { "1" }, currentSheet, allSheets).toIntOrNull() ?: 1).coerceAtLeast(0)
                        if (start >= s.length) "" else s.substring(start, minOf(s.length, start + len))
                    }
                    "LEN" -> {
                        val s = resolveSingleScalar(args.getOrElse(0) { "" }, currentSheet, allSheets)
                        s.length.toString()
                    }
                    "ROUND" -> {
                        val num = resolveSingleScalar(args.getOrElse(0) { "0" }, currentSheet, allSheets).toDoubleOrNull() ?: 0.0
                        val digits = resolveSingleScalar(args.getOrElse(1) { "0" }, currentSheet, allSheets).toIntOrNull() ?: 0
                        val factor = 10.0.pow(digits)
                        formatNumberClean((num * factor).roundToLong() / factor)
                    }
                    "ABS" -> {
                        val num = resolveSingleScalar(args.getOrElse(0) { "0" }, currentSheet, allSheets).toDoubleOrNull() ?: 0.0
                        formatNumberClean(kotlin.math.abs(num))
                    }
                    "TODAY" -> LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    "NOW" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    else -> "#NAME?"
                }
                FormulaEvalResult(resultStr, "SUPPORTED_FORMULA")
            } else {
                // Arithmetic expression e.g. A1+B1*2
                val arithmeticVal = evaluateSimpleArithmetic(expr, currentSheet, allSheets)
                FormulaEvalResult(formatNumberClean(arithmeticVal), "SUPPORTED_FORMULA")
            }
        } catch (e: Exception) {
            FormulaEvalResult("#VALUE!", "ERROR")
        }
    }

    private fun evaluateSimpleArithmetic(
        expr: String,
        currentSheet: ExcelSheet,
        allSheets: List<ExcelSheet>
    ): Double {
        val replaced = Regex("([A-Za-z0-9_]+!)?[A-Za-z]+\\d+").replace(expr) { match ->
            val v = resolveSingleScalar(match.value, currentSheet, allSheets)
            (v.replace(",", "").toDoubleOrNull() ?: 0.0).toString()
        }
        // Parse addition/subtraction/multiplication/division
        val addParts = replaced.split("+")
        return addParts.sumOf { part ->
            val subParts = part.split("-")
            val first = evalMulDiv(subParts.firstOrNull().orEmpty())
            val rest = subParts.drop(1).sumOf { evalMulDiv(it) }
            first - rest
        }
    }

    private fun evalMulDiv(term: String): Double {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return 0.0
        if (trimmed.contains("*")) {
            return trimmed.split("*").map { it.trim().toDoubleOrNull() ?: 0.0 }.reduce { a, b -> a * b }
        }
        if (trimmed.contains("/")) {
            val parts = trimmed.split("/")
            val num = parts[0].trim().toDoubleOrNull() ?: 0.0
            val den = parts.getOrNull(1)?.trim()?.toDoubleOrNull() ?: 1.0
            return if (den == 0.0) 0.0 else num / den
        }
        return trimmed.toDoubleOrNull() ?: 0.0
    }

    private fun evaluateCondition(condStr: String, currentSheet: ExcelSheet, allSheets: List<ExcelSheet>): Boolean {
        val ops = listOf(">=", "<=", "<>", "!=", "=", ">", "<")
        for (op in ops) {
            if (condStr.contains(op)) {
                val parts = condStr.split(op, limit = 2)
                val left = resolveSingleScalar(parts[0], currentSheet, allSheets)
                val right = resolveSingleScalar(parts[1], currentSheet, allSheets)
                val lNum = left.toDoubleOrNull()
                val rNum = right.toDoubleOrNull()
                return if (lNum != null && rNum != null) {
                    when (op) {
                        ">=" -> lNum >= rNum
                        "<=" -> lNum <= rNum
                        "<>", "!=" -> lNum != rNum
                        "=" -> lNum == rNum
                        ">" -> lNum > rNum
                        "<" -> lNum < rNum
                        else -> false
                    }
                } else {
                    when (op) {
                        "=" -> left.equals(right, ignoreCase = true)
                        "<>", "!=" -> !left.equals(right, ignoreCase = true)
                        else -> false
                    }
                }
            }
        }
        return false
    }

    private fun matchesCriterion(cellVal: String, criterion: String): Boolean {
        val ops = listOf(">=", "<=", "<>", ">", "<", "=")
        for (op in ops) {
            if (criterion.startsWith(op)) {
                val targetStr = criterion.removePrefix(op).trim()
                val cNum = cellVal.toDoubleOrNull()
                val tNum = targetStr.toDoubleOrNull()
                if (cNum != null && tNum != null) {
                    return when (op) {
                        ">=" -> cNum >= tNum
                        "<=" -> cNum <= tNum
                        "<>" -> cNum != tNum
                        ">" -> cNum > tNum
                        "<" -> cNum < tNum
                        "=" -> cNum == tNum
                        else -> false
                    }
                }
            }
        }
        return cellVal.equals(criterion, ignoreCase = true)
    }

    private fun resolveSingleScalar(token: String, currentSheet: ExcelSheet, allSheets: List<ExcelSheet>): String {
        val trimmed = token.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 2) {
            return trimmed.substring(1, trimmed.length - 1)
        }
        if (trimmed.toDoubleOrNull() != null) return trimmed
        if (trimmed.contains("(") && trimmed.endsWith(")")) {
            return evaluateFormula("=$trimmed", currentSheet, allSheets).value
        }
        val (targetSheet, cellToken) = resolveTargetSheet(trimmed, currentSheet, allSheets)
        val rc = parseCellRef(cellToken)
        if (rc != null) {
            val cell = targetSheet.getCell(rc.first, rc.second)
            return if (cell.rawValue.startsWith("=") && cell.rawValue != "=$trimmed") {
                evaluateFormula(cell.rawValue, targetSheet, allSheets).value
            } else {
                cell.rawValue
            }
        }
        return trimmed
    }

    private fun resolveStringTokens(
        arg: String,
        currentSheet: ExcelSheet,
        allSheets: List<ExcelSheet>
    ): List<String> {
        val (targetSheet, rangeToken) = resolveTargetSheet(arg.trim(), currentSheet, allSheets)
        if (rangeToken.contains(":")) {
            val parts = rangeToken.split(":")
            val start = parseCellRef(parts[0])
            val end = parseCellRef(parts[1])
            if (start != null && end != null) {
                val out = mutableListOf<String>()
                for (r in minOf(start.first, end.first)..maxOf(start.first, end.first)) {
                    for (c in minOf(start.second, end.second)..maxOf(start.second, end.second)) {
                        val cell = targetSheet.getCell(r, c)
                        val v = if (cell.rawValue.startsWith("=")) {
                            evaluateFormula(cell.rawValue, targetSheet, allSheets).value
                        } else cell.rawValue
                        out.add(v)
                    }
                }
                return out
            }
        }
        return listOf(resolveSingleScalar(arg, currentSheet, allSheets))
    }

    private fun resolveNumericTokens(
        arg: String,
        currentSheet: ExcelSheet,
        allSheets: List<ExcelSheet>,
        keepZerosForNonNumeric: Boolean = false
    ): List<Double> {
        return resolveStringTokens(arg, currentSheet, allSheets).mapNotNull { str ->
            val cleaned = str.replace(",", "").replace("₫", "").replace("%", "").trim()
            val num = cleaned.toDoubleOrNull()
            if (num != null) num else if (keepZerosForNonNumeric) 0.0 else null
        }
    }

    private fun resolveRangeMatrix(
        arg: String,
        currentSheet: ExcelSheet,
        allSheets: List<ExcelSheet>
    ): List<List<String>> {
        val (targetSheet, rangeToken) = resolveTargetSheet(arg.trim(), currentSheet, allSheets)
        if (rangeToken.contains(":")) {
            val parts = rangeToken.split(":")
            val start = parseCellRef(parts[0])
            val end = parseCellRef(parts[1])
            if (start != null && end != null) {
                val rows = mutableListOf<List<String>>()
                for (r in minOf(start.first, end.first)..maxOf(start.first, end.first)) {
                    val rowList = mutableListOf<String>()
                    for (c in minOf(start.second, end.second)..maxOf(start.second, end.second)) {
                        val cell = targetSheet.getCell(r, c)
                        val v = if (cell.rawValue.startsWith("=")) {
                            evaluateFormula(cell.rawValue, targetSheet, allSheets).value
                        } else cell.rawValue
                        rowList.add(v)
                    }
                    rows.add(rowList)
                }
                return rows
            }
        }
        return emptyList()
    }

    private fun resolveTargetSheet(
        token: String,
        currentSheet: ExcelSheet,
        allSheets: List<ExcelSheet>
    ): Pair<ExcelSheet, String> {
        if (token.contains("!")) {
            val sheetName = token.substringBefore("!").trim('\'')
            val rest = token.substringAfter("!")
            val matched = allSheets.find { it.name.equals(sheetName, ignoreCase = true) } ?: currentSheet
            return matched to rest
        }
        return currentSheet to token
    }

    private fun splitTopLevelArgs(argsStr: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var depth = 0
        var inQuotes = false
        for (ch in argsStr) {
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                !inQuotes && ch == '(' -> {
                    depth++
                    current.append(ch)
                }
                !inQuotes && ch == ')' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    current.append(ch)
                }
                !inQuotes && depth == 0 && (ch == ',' || ch == ';') -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString().trim())
        return result
    }

    fun formatValue(rawOrComputed: String, format: CellDataFormat): String {
        val num = rawOrComputed.toDoubleOrNull() ?: return rawOrComputed
        return when (format) {
            CellDataFormat.GENERAL -> formatNumberClean(num)
            CellDataFormat.NUMBER -> DecimalFormat("#,##0.00").format(num)
            CellDataFormat.CURRENCY_VND -> "${DecimalFormat("#,##0").format(num)} ₫"
            CellDataFormat.PERCENT -> "${DecimalFormat("#,##0.0").format(num)}%"
            CellDataFormat.DATE -> rawOrComputed
        }
    }

    private fun formatNumberClean(d: Double): String {
        return if (d == d.toLong().toDouble()) d.toLong().toString()
        else DecimalFormat("#.##").format(d).replace(",", ".")
    }

    /**
     * Writes the workbook to a valid OOXML .xlsx file while preserving all untouched original entries
     * (macros, pivot tables, themes, drawings) for compatibility with desktop Excel.
     */
    fun writeXlsxToFile(workbook: ExcelWorkbookModel, targetFile: File) {
        val entries = workbook.originalZipEntries.toMutableMap()
        val sheets = workbook.sheets

        entries["[Content_Types].xml"] = buildXlsxContentTypes(sheets.size).toByteArray(Charsets.UTF_8)
        entries["_rels/.rels"] = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        entries["xl/workbook.xml"] = buildWorkbookXml(sheets).toByteArray(Charsets.UTF_8)
        entries["xl/_rels/workbook.xml.rels"] = buildWorkbookRelsXml(sheets.size).toByteArray(Charsets.UTF_8)

        for ((idx, sheet) in sheets.withIndex()) {
            entries["xl/worksheets/sheet${idx + 1}.xml"] = buildSheetXml(sheet).toByteArray(Charsets.UTF_8)
        }

        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    fun exportSheetToCsv(sheet: ExcelSheet, targetFile: File) {
        val sb = StringBuilder()
        val maxR = (sheet.cells.keys.mapNotNull { it.substringBefore("_").toIntOrNull() }.maxOrNull() ?: 10)
        val maxC = (sheet.cells.keys.mapNotNull { it.substringAfter("_").toIntOrNull() }.maxOrNull() ?: 5)
        for (r in 0..maxR) {
            val rowVals = (0..maxC).map { c ->
                val v = sheet.getCell(r, c).displayValue.replace("\"", "\"\"")
                if (v.contains(",")) "\"$v\"" else v
            }
            sb.append(rowVals.joinToString(",")).append("\n")
        }
        targetFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    fun exportWorkbookToPdf(workbook: ExcelWorkbookModel, outputPdfFile: File): Boolean {
        return try {
            val pdfDoc = PdfDocument()
            for ((sIdx, sheet) in workbook.sheets.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(842, 595, sIdx + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas
                val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 15f
                    isFakeBoldText = true
                    color = AndroidColor.rgb(16, 124, 65)
                }
                canvas.drawText("${workbook.title} — Sheet: ${sheet.name}", 36f, 36f, titlePaint)

                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = AndroidColor.LTGRAY
                }
                val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 10f
                    color = AndroidColor.BLACK
                }

                val maxR = minOf(18, sheet.rowCount - 1)
                val maxC = minOf(7, sheet.colCount - 1)
                val colW = 760f / (maxC + 1)
                val rowH = 24f
                var y = 56f

                for (r in 0..maxR) {
                    for (c in 0..maxC) {
                        val cell = sheet.getCell(r, c)
                        val x = 36f + c * colW
                        canvas.drawRect(x, y, x + colW, y + rowH, borderPaint)
                        textPaint.isFakeBoldText = cell.isBold || r == 0
                        canvas.drawText(cell.displayValue.take(18), x + 4f, y + 16f, textPaint)
                    }
                    y += rowH
                }
                pdfDoc.finishPage(page)
            }
            FileOutputStream(outputPdfFile).use { pdfDoc.writeTo(it) }
            pdfDoc.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildXlsxContentTypes(sheetCount: Int): String {
        val overrides = (1..sheetCount).joinToString("\n") {
            """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                $overrides
            </Types>
        """.trimIndent()
    }

    private fun buildWorkbookXml(sheets: List<ExcelSheet>): String {
        val sheetTags = sheets.mapIndexed { i, s ->
            val stateAttr = if (s.isHidden) """ state="hidden"""" else ""
            """<sheet name="${escapeXml(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"$stateAttr/>"""
        }.joinToString("")
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets>$sheetTags</sheets>
            </workbook>
        """.trimIndent()
    }

    private fun buildWorkbookRelsXml(sheetCount: Int): String {
        val rels = (1..sheetCount).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$it.xml"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>
        """.trimIndent()
    }

    private fun buildSheetXml(sheet: ExcelSheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<sheetData>")

        val groupedByRow = sheet.cells.values
            .filter { it.rawValue.isNotEmpty() || it.mergedColSpan > 1 || it.mergedRowSpan > 1 }
            .groupBy { it.row }
            .toSortedMap()

        val merges = mutableListOf<String>()

        for ((r, rowCells) in groupedByRow) {
            sb.append("""<row r="${r + 1}">""")
            for (cell in rowCells.sortedBy { it.col }) {
                val ref = "${colIndexToLetter(cell.col)}${r + 1}"
                val isFormula = cell.rawValue.startsWith("=")
                val isNumeric = !isFormula && cell.rawValue.toDoubleOrNull() != null
                val tAttr = if (isFormula || isNumeric) "" else """ t="inlineStr""""
                val fmtAttr = """ vppFmt="${cell.format.name}" vppBold="${if (cell.isBold) "1" else "0"}""""
                sb.append("""<c r="$ref"$tAttr$fmtAttr>""")
                if (isFormula) {
                    sb.append("<f>${escapeXml(cell.rawValue.removePrefix("="))}</f>")
                    sb.append("<v>${escapeXml(cell.displayValue)}</v>")
                } else if (isNumeric) {
                    sb.append("<v>${escapeXml(cell.rawValue)}</v>")
                } else {
                    sb.append("<is><t>${escapeXml(cell.rawValue)}</t></is>")
                }
                sb.append("</c>")

                if (cell.mergedColSpan > 1 || cell.mergedRowSpan > 1) {
                    val endRef = "${colIndexToLetter(cell.col + cell.mergedColSpan - 1)}${r + cell.mergedRowSpan}"
                    merges.add("$ref:$endRef")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData>")
        if (merges.isNotEmpty()) {
            sb.append("""<mergeCells count="${merges.size}">""")
            merges.forEach { m -> sb.append("""<mergeCell ref="$m"/>""") }
            sb.append("</mergeCells>")
        }
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun createDefaultWorkbook(title: String): ExcelWorkbookModel {
        val cells1 = mutableMapOf<String, ExcelCell>()
        val headers = listOf("Mã SP", "Hạng mục chi phí", "Số lượng", "Đơn giá (VNĐ)", "Thành tiền", "Phân loại")
        headers.forEachIndexed { c, h ->
            cells1["0_$c"] = ExcelCell(row = 0, col = c, rawValue = h, isBold = true, bgColorHex = "D1FAE5")
        }
        val rowsData = listOf(
            listOf("VP01", "Máy tính trạm làm việc", "5", "18500000", "=C2*D2", "=IF(E2>=50000000,\"Ưu tiên cao\",\"Thường\")"),
            listOf("VP02", "Bản quyền phần mềm", "12", "2400000", "=C3*D3", "=IF(E3>=50000000,\"Ưu tiên cao\",\"Thường\")"),
            listOf("VP03", "Thiết bị mạng & Lưu trữ", "3", "19200000", "=C4*D4", "=IF(E4>=50000000,\"Ưu tiên cao\",\"Thường\")"),
            listOf("VP04", "Văn phòng phẩm Quý IV", "20", "850000", "=C5*D5", "=IF(E5>=50000000,\"Ưu tiên cao\",\"Thường\")")
        )
        rowsData.forEachIndexed { idx, rList ->
            val r = idx + 1
            rList.forEachIndexed { c, v ->
                val fmt = if (c == 3 || c == 4) CellDataFormat.CURRENCY_VND else CellDataFormat.GENERAL
                cells1["${r}_${c}"] = ExcelCell(row = r, col = c, rawValue = v, format = fmt)
            }
        }
        cells1["5_0"] = ExcelCell(row = 5, col = 0, rawValue = "TỔNG CỘNG", isBold = true)
        cells1["5_2"] = ExcelCell(row = 5, col = 2, rawValue = "=SUM(C2:C5)", isBold = true)
        cells1["5_4"] = ExcelCell(row = 5, col = 4, rawValue = "=SUM(E2:E5)", format = CellDataFormat.CURRENCY_VND, isBold = true)
        cells1["6_0"] = ExcelCell(row = 6, col = 0, rawValue = "TRUNG BÌNH", isBold = true)
        cells1["6_4"] = ExcelCell(row = 6, col = 4, rawValue = "=AVERAGE(E2:E5)", format = CellDataFormat.CURRENCY_VND)
        cells1["7_0"] = ExcelCell(row = 7, col = 0, rawValue = "TỔNG MỤC ƯU TIÊN", isBold = true)
        cells1["7_4"] = ExcelCell(row = 7, col = 4, rawValue = "=SUMIF(F2:F5,\"Ưu tiên cao\",E2:E5)", format = CellDataFormat.CURRENCY_VND, isBold = true)

        val sheet1 = ExcelSheet(
            id = "sheet_1",
            name = "Báo cáo Tài chính",
            rowCount = 25,
            colCount = 8,
            cells = cells1,
            charts = listOf(
                ExcelChartSpec(
                    id = "chart_1",
                    title = "Biểu đồ Chi phí theo Hạng mục",
                    chartType = "BAR",
                    labelRange = "B2:B5",
                    valueRange = "E2:E5"
                )
            )
        )

        val cells2 = mutableMapOf<String, ExcelCell>(
            "0_0" to ExcelCell(0, 0, "Chỉ tiêu tổng hợp", isBold = true),
            "0_1" to ExcelCell(0, 1, "Giá trị tham chiếu Sheet1", isBold = true),
            "1_0" to ExcelCell(1, 0, "Tổng ngân sách Quý IV"),
            "1_1" to ExcelCell(1, 1, "=Báo cáo Tài chính!E6", format = CellDataFormat.CURRENCY_VND, isBold = true),
            "2_0" to ExcelCell(2, 0, "Ngày lập biểu"),
            "2_1" to ExcelCell(2, 1, "=TODAY()", format = CellDataFormat.DATE)
        )
        val sheet2 = ExcelSheet(
            id = "sheet_2",
            name = "Tổng hợp Đa Sheet",
            rowCount = 20,
            colCount = 6,
            cells = cells2
        )

        return recalculateWorkbook(
            ExcelWorkbookModel(
                title = title,
                sheets = listOf(sheet1, sheet2)
            )
        )
    }
}
