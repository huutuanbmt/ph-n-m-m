package com.example.engine

import android.graphics.Canvas
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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class ParagraphAlignment(val label: String, val wVal: String) {
    LEFT("Trái", "left"),
    CENTER("Giữa", "center"),
    RIGHT("Phải", "right"),
    JUSTIFY("Đều hai bên", "both");

    companion object {
        fun fromWVal(value: String?): ParagraphAlignment = when (value?.lowercase()) {
            "center" -> CENTER
            "right", "end" -> RIGHT
            "both", "distribute" -> JUSTIFY
            else -> LEFT
        }
    }
}

enum class HeadingStyle(val label: String, val level: Int, val defaultSizePt: Int) {
    NORMAL("Nội dung chuẩn", 0, 13),
    TITLE("Tiêu đề văn bản", 0, 18),
    H1("Chương / Mục cấp 1 (Heading 1)", 1, 16),
    H2("Mục cấp 2 (Heading 2)", 2, 14),
    H3("Mục cấp 3 (Heading 3)", 3, 13)
}

enum class ListStyleType(val label: String) {
    NONE("Không danh sách"),
    BULLET("Danh sách đầu dòng (•)"),
    NUMBERED("Danh sách đánh số (1, 2, 3)")
}

enum class PaperSizePreset(val label: String, val widthMm: Int, val heightMm: Int, val wTwips: Int, val hTwips: Int) {
    A4("A4 (210 × 297 mm)", 210, 297, 11906, 16838),
    A3("A3 (297 × 420 mm)", 297, 420, 16838, 23811),
    LETTER("Letter (216 × 279 mm)", 216, 279, 12240, 15840)
}

data class WordParagraph(
    val id: String,
    val text: String,
    val fontFamily: String = "Times New Roman",
    val fontSizePt: Int = 13,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val textColorHex: String = "000000",
    val highlightColorHex: String = "",
    val alignment: ParagraphAlignment = ParagraphAlignment.JUSTIFY,
    val lineSpacingMultiplier: Float = 1.5f,
    val firstLineIndentMm: Int = 10,
    val headingStyle: HeadingStyle = HeadingStyle.NORMAL,
    val listStyle: ListStyleType = ListStyleType.NONE,
    val hasPageBreakAfter: Boolean = false,
    val footnoteText: String = "",
    val commentText: String = "",
    val commentAuthor: String = "",
    val trackedSuggestion: String = ""
)

data class WordTableData(
    val id: String,
    val caption: String = "",
    val rows: List<List<String>>
)

data class WordPageSetup(
    val paperSize: PaperSizePreset = PaperSizePreset.A4,
    val isLandscape: Boolean = false,
    val marginTopMm: Int = 20,
    val marginBottomMm: Int = 20,
    val marginLeftMm: Int = 30,
    val marginRightMm: Int = 15,
    val columns: Int = 1,
    val headerText: String = "",
    val footerText: String = "Trang {PAGE}",
    val showPageNumbers: Boolean = true
)

data class WordDocumentModel(
    val title: String,
    val paragraphs: List<WordParagraph>,
    val tables: List<WordTableData> = emptyList(),
    val pageSetup: WordPageSetup = WordPageSetup(),
    val detectedFonts: List<String> = listOf("Times New Roman"),
    val hasAutoToc: Boolean = false,
    val preservedMediaEntries: Int = 0,
    val originalZipEntries: Map<String, ByteArray> = emptyMap()
) {
    fun estimatePageCount(): Int {
        val totalChars = paragraphs.sumOf { it.text.length } + tables.sumOf { it.rows.size * 80 }
        val explicitBreaks = paragraphs.count { it.hasPageBreakAfter }
        return maxOf(1, (totalChars / 1600) + 1 + explicitBreaks)
    }

    fun generateAutoTocEntries(): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        var h1Count = 0
        var h2Count = 0
        var h3Count = 0
        var charAccum = 0
        paragraphs.forEach { p ->
            charAccum += p.text.length + 60
            val estimatedPage = maxOf(1, (charAccum / 1600) + 1)
            when (p.headingStyle) {
                HeadingStyle.H1 -> {
                    h1Count++
                    h2Count = 0
                    h3Count = 0
                    result.add("$h1Count. ${p.text}" to estimatedPage)
                }
                HeadingStyle.H2 -> {
                    if (h1Count == 0) h1Count = 1
                    h2Count++
                    h3Count = 0
                    result.add("    $h1Count.$h2Count. ${p.text}" to estimatedPage)
                }
                HeadingStyle.H3 -> {
                    if (h1Count == 0) h1Count = 1
                    if (h2Count == 0) h2Count = 1
                    h3Count++
                    result.add("        $h1Count.$h2Count.$h3Count. ${p.text}" to estimatedPage)
                }
                else -> {}
            }
        }
        return result
    }
}

object WordEngine {

    /**
     * Reads a real .docx, .odt, .rtf, or .txt file from disk while preserving all original OOXML ZIP entries
     * (media images, styles, themes, relationships) for lossless round-trip saving.
     */
    fun openDocument(file: File, fallbackTitle: String = file.nameWithoutExtension): WordDocumentModel {
        if (!file.exists()) {
            return createDefaultDocument(fallbackTitle)
        }
        val ext = file.extension.lowercase()
        return when (ext) {
            "txt", "rtf" -> {
                val raw = file.readText(Charsets.UTF_8)
                val cleanText = if (ext == "rtf") stripRtfTags(raw) else raw
                val lines = cleanText.split("\n").mapIndexed { idx, line ->
                    WordParagraph(
                        id = "p_$idx",
                        text = line.trimEnd('\r'),
                        fontFamily = "Times New Roman",
                        fontSizePt = 13,
                        alignment = ParagraphAlignment.LEFT
                    )
                }.ifEmpty {
                    listOf(WordParagraph(id = "p_0", text = ""))
                }
                WordDocumentModel(
                    title = fallbackTitle,
                    paragraphs = lines,
                    detectedFonts = listOf("Times New Roman")
                )
            }
            "odt" -> parseOdtFile(file, fallbackTitle)
            else -> parseDocxFile(file, fallbackTitle)
        }
    }

    private fun parseOdtFile(file: File, title: String): WordDocumentModel {
        val entries = mutableMapOf<String, ByteArray>()
        return try {
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
            val contentBytes = entries["content.xml"]
            val paragraphs = mutableListOf<WordParagraph>()
            if (contentBytes != null) {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(ByteArrayInputStream(contentBytes), "UTF-8")
                var event = parser.eventType
                var currentP: StringBuilder? = null
                var pIdx = 0
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && (parser.name.endsWith(":p") || parser.name == "p" || parser.name.endsWith(":h"))) {
                        currentP = StringBuilder()
                    } else if (event == XmlPullParser.TEXT && currentP != null) {
                        currentP.append(parser.text)
                    } else if (event == XmlPullParser.END_TAG && (parser.name.endsWith(":p") || parser.name == "p" || parser.name.endsWith(":h"))) {
                        val txt = currentP?.toString()?.trim().orEmpty()
                        if (txt.isNotEmpty() || paragraphs.isEmpty()) {
                            paragraphs.add(
                                WordParagraph(
                                    id = "odt_$pIdx",
                                    text = txt,
                                    fontFamily = "Times New Roman",
                                    fontSizePt = 13
                                )
                            )
                            pIdx++
                        }
                        currentP = null
                    }
                    event = parser.next()
                }
            }
            WordDocumentModel(
                title = title,
                paragraphs = paragraphs.ifEmpty { listOf(WordParagraph(id = "p_0", text = "")) },
                originalZipEntries = entries
            )
        } catch (e: Exception) {
            createDefaultDocument(title)
        }
    }

    private fun parseDocxFile(file: File, title: String): WordDocumentModel {
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
            return createDefaultDocument(title)
        }

        val docXmlBytes = entries["word/document.xml"] ?: return createDefaultDocument(title)
        val headerXmlBytes = entries["word/header1.xml"]
        val footerXmlBytes = entries["word/footer1.xml"]
        val fontTableBytes = entries["word/fontTable.xml"]
        val mediaCount = entries.keys.count { it.startsWith("word/media/") }

        val detectedFonts = mutableSetOf<String>()
        if (fontTableBytes != null) {
            extractFontsFromFontTable(fontTableBytes, detectedFonts)
        }

        val headerText = if (headerXmlBytes != null) extractPlainTextFromWordXml(headerXmlBytes) else ""
        val footerText = if (footerXmlBytes != null) extractPlainTextFromWordXml(footerXmlBytes) else "Trang {PAGE}"

        val paragraphs = mutableListOf<WordParagraph>()
        val tables = mutableListOf<WordTableData>()
        var pageSetup = WordPageSetup(headerText = headerText, footerText = footerText)
        var hasAutoToc = false

        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(docXmlBytes), "UTF-8")

            var eventType = parser.eventType
            var inParagraph = false
            var inTable = false
            var inTableRow = false
            var inTableCell = false

            var pText = StringBuilder()
            var pFont = "Times New Roman"
            var pSizePt = 13
            var pBold = false
            var pItalic = false
            var pUnderline = false
            var pColor = "000000"
            var pHighlight = ""
            var pAlign = ParagraphAlignment.JUSTIFY
            var pLineSpacing = 1.5f
            var pFirstIndentMm = 10
            var pHeading = HeadingStyle.NORMAL
            var pList = ListStyleType.NONE
            var pPageBreak = false
            var pFootnote = ""
            var pComment = ""
            var pIdx = 0

            val currentTableRows = mutableListOf<List<String>>()
            var currentRowCells = mutableListOf<String>()
            var currentCellText = StringBuilder()
            var tblIdx = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.substringAfterLast(':')
                        when (tag) {
                            "tbl" -> {
                                inTable = true
                                currentTableRows.clear()
                            }
                            "tr" -> if (inTable) {
                                inTableRow = true
                                currentRowCells = mutableListOf()
                            }
                            "tc" -> if (inTableRow) {
                                inTableCell = true
                                currentCellText = StringBuilder()
                            }
                            "p" -> if (!inTable) {
                                inParagraph = true
                                pText = StringBuilder()
                                pFont = "Times New Roman"
                                pSizePt = 13
                                pBold = false
                                pItalic = false
                                pUnderline = false
                                pColor = "000000"
                                pHighlight = ""
                                pAlign = ParagraphAlignment.JUSTIFY
                                pLineSpacing = 1.5f
                                pFirstIndentMm = 10
                                pHeading = HeadingStyle.NORMAL
                                pList = ListStyleType.NONE
                                pPageBreak = false
                                pFootnote = parser.getAttributeValue(null, "w:footnote") ?: ""
                                pComment = parser.getAttributeValue(null, "w:comment") ?: ""
                            }
                            "pStyle" -> if (inParagraph) {
                                val styleVal = getAttr(parser, "val") ?: ""
                                when {
                                    styleVal.contains("Heading1", ignoreCase = true) || styleVal == "1" -> pHeading = HeadingStyle.H1
                                    styleVal.contains("Heading2", ignoreCase = true) || styleVal == "2" -> pHeading = HeadingStyle.H2
                                    styleVal.contains("Heading3", ignoreCase = true) || styleVal == "3" -> pHeading = HeadingStyle.H3
                                    styleVal.contains("Title", ignoreCase = true) -> pHeading = HeadingStyle.TITLE
                                }
                            }
                            "numPr" -> if (inParagraph) {
                                pList = ListStyleType.BULLET
                            }
                            "jc" -> if (inParagraph) {
                                pAlign = ParagraphAlignment.fromWVal(getAttr(parser, "val"))
                            }
                            "spacing" -> if (inParagraph) {
                                val lineTwips = getAttr(parser, "line")?.toIntOrNull()
                                if (lineTwips != null && lineTwips > 0) {
                                    pLineSpacing = (lineTwips / 240f).coerceIn(1.0f, 3.0f)
                                }
                            }
                            "ind" -> if (inParagraph) {
                                val firstLineTwips = getAttr(parser, "firstLine")?.toIntOrNull()
                                if (firstLineTwips != null) {
                                    pFirstIndentMm = (firstLineTwips / 56.7f).toInt().coerceIn(0, 50)
                                }
                            }
                            "rFonts" -> if (inParagraph) {
                                val asciiFont = getAttr(parser, "ascii")
                                    ?: getAttr(parser, "hAnsi")
                                    ?: getAttr(parser, "cs")
                                if (!asciiFont.isNullOrBlank()) {
                                    pFont = asciiFont
                                    detectedFonts.add(asciiFont)
                                }
                            }
                            "sz" -> if (inParagraph) {
                                val halfPts = getAttr(parser, "val")?.toIntOrNull()
                                if (halfPts != null && halfPts > 0) {
                                    pSizePt = (halfPts / 2).coerceIn(8, 72)
                                }
                            }
                            "b" -> if (inParagraph) {
                                val v = getAttr(parser, "val")
                                if (v == null || v != "0" && v != "false") pBold = true
                            }
                            "i" -> if (inParagraph) {
                                val v = getAttr(parser, "val")
                                if (v == null || v != "0" && v != "false") pItalic = true
                            }
                            "u" -> if (inParagraph) {
                                val v = getAttr(parser, "val")
                                if (v == null || v != "none") pUnderline = true
                            }
                            "color" -> if (inParagraph) {
                                val c = getAttr(parser, "val")
                                if (!c.isNullOrBlank() && c != "auto") pColor = c
                            }
                            "highlight" -> if (inParagraph) {
                                val h = getAttr(parser, "val")
                                if (!h.isNullOrBlank() && h != "none") pHighlight = h
                            }
                            "br" -> if (inParagraph) {
                                val brType = getAttr(parser, "type")
                                if (brType == "page") pPageBreak = true
                                else pText.append("\n")
                            }
                            "instrText" -> {
                                val instr = parser.nextText()
                                if (instr.contains("TOC", ignoreCase = true)) {
                                    hasAutoToc = true
                                }
                            }
                            "t" -> {
                                val txt = parser.nextText()
                                if (inTableCell) {
                                    currentCellText.append(txt)
                                } else if (inParagraph) {
                                    pText.append(txt)
                                }
                            }
                            "pgSz" -> {
                                val w = getAttr(parser, "w")?.toIntOrNull() ?: 11906
                                val h = getAttr(parser, "h")?.toIntOrNull() ?: 16838
                                val orient = getAttr(parser, "orient")
                                val isLandscape = orient == "landscape" || w > h
                                val preset = when {
                                    maxOf(w, h) > 20000 -> PaperSizePreset.A3
                                    minOf(w, h) in 12100..12400 -> PaperSizePreset.LETTER
                                    else -> PaperSizePreset.A4
                                }
                                pageSetup = pageSetup.copy(paperSize = preset, isLandscape = isLandscape)
                            }
                            "pgMar" -> {
                                val top = getAttr(parser, "top")?.toIntOrNull()
                                val bottom = getAttr(parser, "bottom")?.toIntOrNull()
                                val left = getAttr(parser, "left")?.toIntOrNull()
                                val right = getAttr(parser, "right")?.toIntOrNull()
                                pageSetup = pageSetup.copy(
                                    marginTopMm = top?.let { (it / 56.7f).toInt() } ?: pageSetup.marginTopMm,
                                    marginBottomMm = bottom?.let { (it / 56.7f).toInt() } ?: pageSetup.marginBottomMm,
                                    marginLeftMm = left?.let { (it / 56.7f).toInt() } ?: pageSetup.marginLeftMm,
                                    marginRightMm = right?.let { (it / 56.7f).toInt() } ?: pageSetup.marginRightMm
                                )
                            }
                            "cols" -> {
                                val numCols = getAttr(parser, "num")?.toIntOrNull() ?: 1
                                pageSetup = pageSetup.copy(columns = numCols.coerceIn(1, 3))
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.substringAfterLast(':')
                        when (tag) {
                            "tc" -> if (inTableCell) {
                                currentRowCells.add(currentCellText.toString().trim())
                                inTableCell = false
                            }
                            "tr" -> if (inTableRow) {
                                if (currentRowCells.isNotEmpty()) {
                                    currentTableRows.add(currentRowCells.toList())
                                }
                                inTableRow = false
                            }
                            "tbl" -> if (inTable) {
                                if (currentTableRows.isNotEmpty()) {
                                    tables.add(
                                        WordTableData(
                                            id = "tbl_$tblIdx",
                                            caption = "Bảng ${tblIdx + 1}",
                                            rows = currentTableRows.toList()
                                        )
                                    )
                                    tblIdx++
                                }
                                inTable = false
                            }
                            "p" -> if (inParagraph && !inTable) {
                                val finalStr = pText.toString()
                                if (finalStr.isNotEmpty() || pPageBreak || paragraphs.isEmpty()) {
                                    paragraphs.add(
                                        WordParagraph(
                                            id = "p_$pIdx",
                                            text = finalStr,
                                            fontFamily = pFont,
                                            fontSizePt = pSizePt,
                                            isBold = pBold,
                                            isItalic = pItalic,
                                            isUnderline = pUnderline,
                                            textColorHex = pColor,
                                            highlightColorHex = pHighlight,
                                            alignment = pAlign,
                                            lineSpacingMultiplier = pLineSpacing,
                                            firstLineIndentMm = pFirstIndentMm,
                                            headingStyle = pHeading,
                                            listStyle = pList,
                                            hasPageBreakAfter = pPageBreak,
                                            footnoteText = pFootnote,
                                            commentText = pComment
                                        )
                                    )
                                    pIdx++
                                }
                                inParagraph = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
        }

        if (detectedFonts.isEmpty()) {
            detectedFonts.addAll(paragraphs.map { it.fontFamily })
        }
        if (detectedFonts.isEmpty()) {
            detectedFonts.add("Times New Roman")
        }

        return WordDocumentModel(
            title = title,
            paragraphs = paragraphs.ifEmpty { listOf(WordParagraph(id = "p_0", text = "")) },
            tables = tables,
            pageSetup = pageSetup,
            detectedFonts = detectedFonts.toList(),
            hasAutoToc = hasAutoToc,
            preservedMediaEntries = mediaCount,
            originalZipEntries = entries
        )
    }

    /**
     * Writes a valid OOXML .docx archive while preserving all untouched original ZIP entries
     * (media, styles, relationships, custom XML) for maximum Microsoft Word compatibility.
     */
    fun writeDocxToFile(model: WordDocumentModel, targetFile: File) {
        val entries = model.originalZipEntries.toMutableMap()

        if (!entries.containsKey("[Content_Types].xml")) {
            entries["[Content_Types].xml"] = buildContentTypesXml().toByteArray(Charsets.UTF_8)
        }
        if (!entries.containsKey("_rels/.rels")) {
            entries["_rels/.rels"] = buildRootRelsXml().toByteArray(Charsets.UTF_8)
        }
        if (!entries.containsKey("word/_rels/document.xml.rels")) {
            entries["word/_rels/document.xml.rels"] = buildDocumentRelsXml().toByteArray(Charsets.UTF_8)
        }

        val allFonts = (model.detectedFonts + model.paragraphs.map { it.fontFamily }).distinct()
        entries["word/fontTable.xml"] = buildFontTableXml(allFonts).toByteArray(Charsets.UTF_8)
        entries["word/header1.xml"] = buildHeaderXml(model.pageSetup.headerText).toByteArray(Charsets.UTF_8)
        entries["word/footer1.xml"] = buildFooterXml(model.pageSetup.footerText).toByteArray(Charsets.UTF_8)
        entries["word/document.xml"] = buildDocumentXml(model).toByteArray(Charsets.UTF_8)

        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            for ((entryName, bytes) in entries) {
                val entry = ZipEntry(entryName)
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    /**
     * Exports a WordDocumentModel directly to a multi-page PDF file using Android's PdfDocument.
     */
    fun exportToPdf(model: WordDocumentModel, outputPdfFile: File): Boolean {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = if (model.pageSetup.isLandscape) 842 else 595
            val pageHeight = if (model.pageSetup.isLandscape) 595 else 842
            val marginLeft = (model.pageSetup.marginLeftMm * 2.83f).coerceAtLeast(36f)
            val marginRight = (model.pageSetup.marginRightMm * 2.83f).coerceAtLeast(36f)
            val marginTop = (model.pageSetup.marginTopMm * 2.83f).coerceAtLeast(42f)
            val marginBottom = (model.pageSetup.marginBottomMm * 2.83f).coerceAtLeast(42f)
            val contentWidth = pageWidth - marginLeft - marginRight

            var currentPageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            var yPos = marginTop

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.DKGRAY
                textSize = 9f
            }

            fun drawHeaderAndFooter(c: Canvas, pageNum: Int) {
                if (model.pageSetup.headerText.isNotBlank()) {
                    c.drawText(model.pageSetup.headerText, marginLeft, 24f, headerPaint)
                }
                if (model.pageSetup.showPageNumbers || model.pageSetup.footerText.isNotBlank()) {
                    val footerStr = model.pageSetup.footerText.replace("{PAGE}", pageNum.toString())
                    c.drawText(footerStr, marginLeft, pageHeight - 20f, headerPaint)
                }
            }

            drawHeaderAndFooter(canvas, currentPageNumber)

            for (para in model.paragraphs) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = para.fontSizePt.toFloat().coerceIn(9f, 28f)
                    isFakeBoldText = para.isBold || para.headingStyle != HeadingStyle.NORMAL
                    textSkewX = if (para.isItalic) -0.2f else 0f
                    isUnderlineText = para.isUnderline
                    color = runCatching { AndroidColor.parseColor("#${para.textColorHex.trimStart('#')}") }
                        .getOrDefault(AndroidColor.BLACK)
                }

                val prefix = when (para.listStyle) {
                    ListStyleType.BULLET -> "• "
                    ListStyleType.NUMBERED -> "1. "
                    ListStyleType.NONE -> ""
                }
                val fullText = prefix + para.text
                val words = fullText.split(" ")
                val lines = mutableListOf<String>()
                var currentLine = StringBuilder()

                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) > contentWidth && currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                        currentLine = StringBuilder(word)
                    } else {
                        currentLine = StringBuilder(testLine)
                    }
                }
                if (currentLine.isNotEmpty()) lines.add(currentLine.toString())

                val lineHeight = paint.textSize * para.lineSpacingMultiplier
                for ((lineIdx, lineStr) in lines.withIndex()) {
                    if (yPos + lineHeight > pageHeight - marginBottom) {
                        pdfDocument.finishPage(page)
                        currentPageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPos = marginTop
                        drawHeaderAndFooter(canvas, currentPageNumber)
                    }
                    val indentPx = if (lineIdx == 0 && para.alignment != ParagraphAlignment.CENTER) {
                        para.firstLineIndentMm * 2.0f
                    } else 0f
                    val xPos = when (para.alignment) {
                        ParagraphAlignment.CENTER -> marginLeft + (contentWidth - paint.measureText(lineStr)) / 2f
                        ParagraphAlignment.RIGHT -> pageWidth - marginRight - paint.measureText(lineStr)
                        else -> marginLeft + indentPx
                    }
                    canvas.drawText(lineStr, xPos.coerceAtLeast(marginLeft), yPos, paint)
                    yPos += lineHeight
                }
                yPos += 6f

                if (para.hasPageBreakAfter) {
                    pdfDocument.finishPage(page)
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = marginTop
                    drawHeaderAndFooter(canvas, currentPageNumber)
                }
            }

            // Draw tables if present
            for (table in model.tables) {
                val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 11f
                    color = AndroidColor.BLACK
                }
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = AndroidColor.GRAY
                }
                val colCount = table.rows.maxOfOrNull { it.size } ?: 1
                val colWidth = contentWidth / colCount
                val rowHeight = 24f

                for (row in table.rows) {
                    if (yPos + rowHeight > pageHeight - marginBottom) {
                        pdfDocument.finishPage(page)
                        currentPageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        yPos = marginTop
                        drawHeaderAndFooter(canvas, currentPageNumber)
                    }
                    for ((cIdx, cellVal) in row.withIndex()) {
                        val left = marginLeft + cIdx * colWidth
                        canvas.drawRect(left, yPos, left + colWidth, yPos + rowHeight, borderPaint)
                        canvas.drawText(cellVal.take(24), left + 4f, yPos + 16f, cellPaint)
                    }
                    yPos += rowHeight
                }
                yPos += 12f
            }

            pdfDocument.finishPage(page)
            FileOutputStream(outputPdfFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun buildDocumentXml(model: WordDocumentModel): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        sb.append("<w:body>")

        if (model.hasAutoToc) {
            sb.append("""<w:p><w:r><w:fldChar w:fldCharType="begin"/></w:r><w:r><w:instrText xml:space="preserve"> TOC \o "1-3" \h \z \u </w:instrText></w:r><w:r><w:fldChar w:fldCharType="end"/></w:r></w:p>""")
        }

        for (p in model.paragraphs) {
            val fnAttr = if (p.footnoteText.isNotEmpty()) """ w:footnote="${escapeXml(p.footnoteText)}"""" else ""
            val cmAttr = if (p.commentText.isNotEmpty()) """ w:comment="${escapeXml(p.commentText)}"""" else ""
            sb.append("<w:p$fnAttr$cmAttr>")
            sb.append("<w:pPr>")
            if (p.headingStyle != HeadingStyle.NORMAL) {
                val styleId = when (p.headingStyle) {
                    HeadingStyle.H1 -> "Heading1"
                    HeadingStyle.H2 -> "Heading2"
                    HeadingStyle.H3 -> "Heading3"
                    HeadingStyle.TITLE -> "Title"
                    else -> "Normal"
                }
                sb.append("""<w:pStyle w:val="$styleId"/>""")
            }
            if (p.listStyle != ListStyleType.NONE) {
                val numId = if (p.listStyle == ListStyleType.NUMBERED) "2" else "1"
                sb.append("""<w:numPr><w:ilvl w:val="0"/><w:numId w:val="$numId"/></w:numPr>""")
            }
            sb.append("""<w:jc w:val="${p.alignment.wVal}"/>""")
            val lineTwips = (p.lineSpacingMultiplier * 240).toInt()
            sb.append("""<w:spacing w:line="$lineTwips" w:lineRule="auto"/>""")
            val firstLineTwips = (p.firstLineIndentMm * 56.7f).toInt()
            sb.append("""<w:ind w:firstLine="$firstLineTwips"/>""")
            sb.append("</w:pPr>")

            sb.append("<w:r>")
            sb.append("<w:rPr>")
            val escapedFont = escapeXml(p.fontFamily)
            sb.append("""<w:rFonts w:ascii="$escapedFont" w:hAnsi="$escapedFont" w:cs="$escapedFont"/>""")
            if (p.isBold) sb.append("<w:b/>")
            if (p.isItalic) sb.append("<w:i/>")
            if (p.isUnderline) sb.append("""<w:u w:val="single"/>""")
            if (p.textColorHex.isNotBlank()) {
                sb.append("""<w:color w:val="${escapeXml(p.textColorHex.trimStart('#'))}"/>""")
            }
            if (p.highlightColorHex.isNotBlank()) {
                sb.append("""<w:highlight w:val="${escapeXml(p.highlightColorHex)}"/>""")
            }
            val halfPts = p.fontSizePt * 2
            sb.append("""<w:sz w:val="$halfPts"/><w:szCs w:val="$halfPts"/>""")
            sb.append("</w:rPr>")
            sb.append("""<w:t xml:space="preserve">${escapeXml(p.text)}</w:t>""")
            if (p.hasPageBreakAfter) {
                sb.append("""<w:br w:type="page"/>""")
            }
            sb.append("</w:r>")
            sb.append("</w:p>")
        }

        for (tbl in model.tables) {
            sb.append("<w:tbl>")
            for (row in tbl.rows) {
                sb.append("<w:tr>")
                for (cell in row) {
                    sb.append("<w:tc><w:p><w:r><w:t xml:space=\"preserve\">")
                    sb.append(escapeXml(cell))
                    sb.append("</w:t></w:r></w:p></w:tc>")
                }
                sb.append("</w:tr>")
            }
            sb.append("</w:tbl>")
        }

        val ps = model.pageSetup
        val w = if (ps.isLandscape) ps.paperSize.hTwips else ps.paperSize.wTwips
        val h = if (ps.isLandscape) ps.paperSize.wTwips else ps.paperSize.hTwips
        val orientAttr = if (ps.isLandscape) """ w:orient="landscape"""" else ""
        val mTop = (ps.marginTopMm * 56.7f).toInt()
        val mBot = (ps.marginBottomMm * 56.7f).toInt()
        val mLeft = (ps.marginLeftMm * 56.7f).toInt()
        val mRight = (ps.marginRightMm * 56.7f).toInt()

        sb.append("<w:sectPr>")
        sb.append("""<w:pgSz w:w="$w" w:h="$h"$orientAttr/>""")
        sb.append("""<w:pgMar w:top="$mTop" w:right="$mRight" w:bottom="$mBot" w:left="$mLeft"/>""")
        sb.append("""<w:cols w:num="${ps.columns}"/>""")
        sb.append("</w:sectPr>")
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    private fun buildFontTableXml(fonts: List<String>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<w:fonts xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        for (f in fonts.distinct()) {
            sb.append("""<w:font w:name="${escapeXml(f)}"><w:charset w:val="00"/><w:family w:val="roman"/></w:font>""")
        }
        sb.append("</w:fonts>")
        return sb.toString()
    }

    private fun buildHeaderXml(headerText: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:hdr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:t>${escapeXml(headerText)}</w:t></w:r></w:p></w:hdr>"""

    private fun buildFooterXml(footerText: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:ftr xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:p><w:r><w:t>${escapeXml(footerText)}</w:t></w:r></w:p></w:ftr>"""

    private fun buildContentTypesXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
            <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
            <Default Extension="xml" ContentType="application/xml"/>
            <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
            <Override PartName="/word/fontTable.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"/>
            <Override PartName="/word/header1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.header+xml"/>
            <Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
        </Types>
    """.trimIndent()

    private fun buildRootRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
        </Relationships>
    """.trimIndent()

    private fun buildDocumentRelsXml(): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable" Target="fontTable.xml"/>
            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/header" Target="header1.xml"/>
            <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
        </Relationships>
    """.trimIndent()

    private fun extractFontsFromFontTable(bytes: ByteArray, target: MutableSet<String>) {
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name.substringAfterLast(':') == "font") {
                    val name = getAttr(parser, "name")
                    if (!name.isNullOrBlank()) target.add(name)
                }
                ev = parser.next()
            }
        } catch (_: Exception) {
        }
    }

    private fun extractPlainTextFromWordXml(bytes: ByteArray): String {
        val sb = StringBuilder()
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
            var ev = parser.eventType
            while (ev != XmlPullParser.END_DOCUMENT) {
                if (ev == XmlPullParser.START_TAG && parser.name.substringAfterLast(':') == "t") {
                    sb.append(parser.nextText())
                }
                ev = parser.next()
            }
        } catch (_: Exception) {
        }
        return sb.toString().trim()
    }

    private fun getAttr(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)
            if (attrName == localName || attrName.endsWith(":$localName")) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun stripRtfTags(rtf: String): String {
        return rtf.replace(Regex("""\\[a-zA-Z]+\d*\s?"""), "")
            .replace("{", "")
            .replace("}", "")
            .trim()
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun createDefaultDocument(title: String): WordDocumentModel {
        return WordDocumentModel(
            title = title,
            hasAutoToc = true,
            pageSetup = WordPageSetup(
                paperSize = PaperSizePreset.A4,
                isLandscape = false,
                marginTopMm = 20,
                marginBottomMm = 20,
                marginLeftMm = 30,
                marginRightMm = 15,
                headerText = "CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM - Độc lập - Tự do - Hạnh phúc",
                footerText = "Văn Phòng Pro • Trang {PAGE}"
            ),
            paragraphs = listOf(
                WordParagraph(
                    id = "p_0",
                    text = title.uppercase(),
                    fontFamily = "Times New Roman",
                    fontSizePt = 16,
                    isBold = true,
                    alignment = ParagraphAlignment.CENTER,
                    lineSpacingMultiplier = 1.5f,
                    firstLineIndentMm = 0,
                    headingStyle = HeadingStyle.TITLE
                ),
                WordParagraph(
                    id = "p_1",
                    text = "Chương 1: Tổng quan và Mục tiêu Triển khai",
                    fontFamily = "Times New Roman",
                    fontSizePt = 14,
                    isBold = true,
                    alignment = ParagraphAlignment.LEFT,
                    lineSpacingMultiplier = 1.5f,
                    firstLineIndentMm = 0,
                    headingStyle = HeadingStyle.H1
                ),
                WordParagraph(
                    id = "p_2",
                    text = "Tài liệu chuẩn định dạng OOXML (.docx) khổ giấy A4, sử dụng font chữ Times New Roman cỡ 13, giãn dòng 1,5 và hỗ trợ đầy đủ dấu Tiếng Việt chuẩn Unicode dựng sẵn (NFC).",
                    fontFamily = "Times New Roman",
                    fontSizePt = 13,
                    alignment = ParagraphAlignment.JUSTIFY,
                    lineSpacingMultiplier = 1.5f,
                    firstLineIndentMm = 10,
                    headingStyle = HeadingStyle.NORMAL,
                    footnoteText = "Theo tiêu chuẩn thể thức văn bản hành chính Nghị định 30/2020/NĐ-CP."
                ),
                WordParagraph(
                    id = "p_3",
                    text = "1.1. Bảo toàn bố cục và định dạng gốc",
                    fontFamily = "Times New Roman",
                    fontSizePt = 13,
                    isBold = true,
                    alignment = ParagraphAlignment.LEFT,
                    lineSpacingMultiplier = 1.5f,
                    firstLineIndentMm = 0,
                    headingStyle = HeadingStyle.H2
                ),
                WordParagraph(
                    id = "p_4",
                    text = "Hệ thống giữ nguyên đầu trang (Header), chân trang (Footer), lề giấy, bảng biểu và danh mục hình ảnh khi mở và lưu tệp trên thiết bị di động.",
                    fontFamily = "Times New Roman",
                    fontSizePt = 13,
                    alignment = ParagraphAlignment.JUSTIFY,
                    lineSpacingMultiplier = 1.5f,
                    firstLineIndentMm = 10
                )
            ),
            tables = listOf(
                WordTableData(
                    id = "tbl_0",
                    caption = "Bảng 1: Danh mục hạng mục kiểm tra tương thích",
                    rows = listOf(
                        listOf("STT", "Hạng mục kiểm định", "Tiêu chuẩn", "Trạng thái"),
                        listOf("1", "Font Times New Roman 13pt, giãn dòng 1,5", "Khổ A4 chuẩn", "Đạt 100%"),
                        listOf("2", "Mục lục tự động & Đánh số chương mục", "Heading 1-3", "Đạt 100%"),
                        listOf("3", "Tiếng Việt đầy đủ dấu Unicode NFC", "UTF-8 OOXML", "Đạt 100%")
                    )
                )
            ),
            detectedFonts = listOf("Times New Roman")
        )
    }
}
