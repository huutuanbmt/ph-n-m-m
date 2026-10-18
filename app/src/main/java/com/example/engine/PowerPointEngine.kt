package com.example.engine

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
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

enum class SlideElementType(val label: String) {
    TEXT_BOX("Hộp văn bản"),
    SHAPE_RECT("Hình chữ nhật"),
    SHAPE_CIRCLE("Hình tròn / Elip"),
    TABLE("Bảng dữ liệu"),
    CHART("Biểu đồ trình chiếu"),
    IMAGE_BADGE("Khung hình ảnh")
}

enum class SlideTransitionType(val label: String) {
    NONE("Không hiệu ứng"),
    FADE("Mờ dần (Fade)"),
    PUSH("Đẩy ngang (Push)"),
    WIPE("Quét ngang (Wipe)"),
    ZOOM("Phóng tâm (Zoom)")
}

data class SlideElement(
    val id: String,
    val type: SlideElementType = SlideElementType.TEXT_BOX,
    val text: String,
    val xPercent: Float = 10f, // 0..100
    val yPercent: Float = 15f, // 0..100
    val widthPercent: Float = 80f,
    val heightPercent: Float = 22f,
    val rotationDeg: Float = 0f,
    val zIndex: Int = 1,
    val groupId: String = "",
    val fontFamily: String = "Calibri",
    val fontSizePt: Int = 22,
    val isBold: Boolean = false,
    val textColorHex: String = "FFFFFF",
    val fillColorHex: String = "1E3A8A"
)

data class PptSlide(
    val id: String,
    val slideNumber: Int,
    val layoutName: String = "Title & Content",
    val backgroundColorHex: String = "0F2547",
    val transition: SlideTransitionType = SlideTransitionType.FADE,
    val speakerNotes: String = "",
    val elements: List<SlideElement> = emptyList()
)

data class PowerPointModel(
    val title: String,
    val slides: List<PptSlide>,
    val themeName: String = "Office Executive Navy",
    val aspectRatio: String = "16:9",
    val hasPreservedAnimations: Boolean = false,
    val hasSlideMasterPreserved: Boolean = true,
    val detectedFonts: List<String> = listOf("Calibri"),
    val originalZipEntries: Map<String, ByteArray> = emptyMap()
)

object PowerPointEngine {

    fun openPresentation(file: File, fallbackTitle: String = file.nameWithoutExtension): PowerPointModel {
        if (!file.exists()) return createDefaultPresentation(fallbackTitle)

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
            return createDefaultPresentation(fallbackTitle)
        }

        val slidePaths = entries.keys
            .filter { it.startsWith("ppt/slides/slide") && it.endsWith(".xml") }
            .sortedBy {
                it.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 999
            }

        if (slidePaths.isEmpty()) return createDefaultPresentation(fallbackTitle)

        val slides = mutableListOf<PptSlide>()
        val detectedFonts = mutableSetOf<String>()
        var hasAnimations = false

        for ((idx, path) in slidePaths.withIndex()) {
            val bytes = entries[path] ?: continue
            val elements = mutableListOf<SlideElement>()
            var bgHex = "0F2547"
            var transition = SlideTransitionType.FADE
            var elIdx = 0

            try {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
                var ev = parser.eventType
                var inSp = false
                var spText = StringBuilder()
                var xPct = 10f
                var yPct = 18f + (elIdx * 22f)
                var wPct = 80f
                var hPct = 20f
                var rot = 0f
                var font = "Calibri"
                var fontSize = 20
                var isBold = false
                var textColor = "FFFFFF"
                var fillColor = "1E3A8A"
                var elType = SlideElementType.TEXT_BOX

                while (ev != XmlPullParser.END_DOCUMENT) {
                    val tag = parser.name?.substringAfterLast(':') ?: ""
                    when (ev) {
                        XmlPullParser.START_TAG -> {
                            when (tag) {
                                "sld" -> {
                                    bgHex = parser.getAttributeValue(null, "vppBg") ?: "0F2547"
                                    val trAttr = parser.getAttributeValue(null, "vppTrans") ?: "FADE"
                                    transition = runCatching { SlideTransitionType.valueOf(trAttr) }
                                        .getOrDefault(SlideTransitionType.FADE)
                                }
                                "timing", "anim" -> hasAnimations = true
                                "sp" -> {
                                    inSp = true
                                    spText = StringBuilder()
                                    xPct = parser.getAttributeValue(null, "vppX")?.toFloatOrNull() ?: 10f
                                    yPct = parser.getAttributeValue(null, "vppY")?.toFloatOrNull() ?: (16f + elIdx * 24f).coerceAtMost(75f)
                                    wPct = parser.getAttributeValue(null, "vppW")?.toFloatOrNull() ?: 80f
                                    hPct = parser.getAttributeValue(null, "vppH")?.toFloatOrNull() ?: 20f
                                    rot = parser.getAttributeValue(null, "vppRot")?.toFloatOrNull() ?: 0f
                                    font = parser.getAttributeValue(null, "vppFont") ?: "Calibri"
                                    fontSize = parser.getAttributeValue(null, "vppSize")?.toIntOrNull() ?: if (elIdx == 0) 26 else 18
                                    isBold = parser.getAttributeValue(null, "vppBold") == "1" || elIdx == 0
                                    textColor = parser.getAttributeValue(null, "vppColor") ?: "FFFFFF"
                                    fillColor = parser.getAttributeValue(null, "vppFill") ?: "1E3A8A"
                                    val typeAttr = parser.getAttributeValue(null, "vppType") ?: "TEXT_BOX"
                                    elType = runCatching { SlideElementType.valueOf(typeAttr) }
                                        .getOrDefault(SlideElementType.TEXT_BOX)
                                    detectedFonts.add(font)
                                }
                                "latin" -> {
                                    val tf = parser.getAttributeValue(null, "typeface")
                                    if (!tf.isNullOrBlank() && !tf.startsWith("+")) {
                                        font = tf
                                        detectedFonts.add(tf)
                                    }
                                }
                                "t" -> if (inSp) {
                                    val txt = parser.nextText()
                                    if (spText.isNotEmpty()) spText.append(" ")
                                    spText.append(txt)
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (tag == "sp" && inSp) {
                                val finalTxt = spText.toString().trim()
                                if (finalTxt.isNotEmpty()) {
                                    elements.add(
                                        SlideElement(
                                            id = "s${idx + 1}_el_$elIdx",
                                            type = elType,
                                            text = finalTxt,
                                            xPercent = xPct,
                                            yPercent = yPct,
                                            widthPercent = wPct,
                                            heightPercent = hPct,
                                            rotationDeg = rot,
                                            zIndex = elIdx + 1,
                                            fontFamily = font,
                                            fontSizePt = fontSize,
                                            isBold = isBold,
                                            textColorHex = textColor,
                                            fillColorHex = fillColor
                                        )
                                    )
                                    elIdx++
                                }
                                inSp = false
                            }
                        }
                    }
                    ev = parser.next()
                }
            } catch (_: Exception) {
            }

            val notesBytes = entries["ppt/notesSlides/notesSlide${idx + 1}.xml"]
            val notesStr = if (notesBytes != null) extractTextFromXml(notesBytes) else ""

            slides.add(
                PptSlide(
                    id = "slide_${idx + 1}",
                    slideNumber = idx + 1,
                    backgroundColorHex = bgHex,
                    transition = transition,
                    speakerNotes = notesStr,
                    elements = elements
                )
            )
        }

        return PowerPointModel(
            title = fallbackTitle,
            slides = slides,
            hasPreservedAnimations = hasAnimations,
            hasSlideMasterPreserved = entries.keys.any { it.startsWith("ppt/slideMasters/") },
            detectedFonts = detectedFonts.ifEmpty { setOf("Calibri") }.toList(),
            originalZipEntries = entries
        )
    }

    fun writePptxToFile(model: PowerPointModel, targetFile: File) {
        val entries = model.originalZipEntries.toMutableMap()
        val slides = model.slides

        entries["[Content_Types].xml"] = buildPptxContentTypes(slides.size).toByteArray(Charsets.UTF_8)
        entries["_rels/.rels"] = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
            </Relationships>
        """.trimIndent().toByteArray(Charsets.UTF_8)
        entries["ppt/presentation.xml"] = buildPresentationXml(slides.size).toByteArray(Charsets.UTF_8)
        entries["ppt/_rels/presentation.xml.rels"] = buildPresentationRelsXml(slides.size).toByteArray(Charsets.UTF_8)

        for ((idx, slide) in slides.withIndex()) {
            entries["ppt/slides/slide${idx + 1}.xml"] = buildSlideXml(slide).toByteArray(Charsets.UTF_8)
            entries["ppt/notesSlides/notesSlide${idx + 1}.xml"] = buildNotesXml(slide.speakerNotes).toByteArray(Charsets.UTF_8)
        }

        ZipOutputStream(FileOutputStream(targetFile)).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    fun exportPresentationToPdf(model: PowerPointModel, outputPdfFile: File): Boolean {
        return try {
            val pdfDoc = PdfDocument()
            val pageW = 842
            val pageH = 474
            for ((idx, slide) in model.slides.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, idx + 1).create()
                val page = pdfDoc.startPage(pageInfo)
                val canvas = page.canvas

                val bgColor = runCatching { AndroidColor.parseColor("#${slide.backgroundColorHex.trimStart('#')}") }
                    .getOrDefault(AndroidColor.rgb(15, 37, 71))
                canvas.drawColor(bgColor)

                for (el in slide.elements.sortedBy { it.zIndex }) {
                    val left = (el.xPercent / 100f) * pageW
                    val top = (el.yPercent / 100f) * pageH
                    val right = left + (el.widthPercent / 100f) * pageW
                    val bottom = top + (el.heightPercent / 100f) * pageH

                    val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = runCatching { AndroidColor.parseColor("#${el.fillColorHex.trimStart('#')}") }
                            .getOrDefault(AndroidColor.rgb(30, 58, 138))
                    }
                    canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, boxPaint)

                    val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = el.fontSizePt.toFloat().coerceIn(12f, 36f)
                        isFakeBoldText = el.isBold
                        color = runCatching { AndroidColor.parseColor("#${el.textColorHex.trimStart('#')}") }
                            .getOrDefault(AndroidColor.WHITE)
                    }
                    canvas.drawText(el.text.take(65), left + 16f, top + txtPaint.textSize + 14f, txtPaint)
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

    private fun buildPptxContentTypes(slideCount: Int): String {
        val overrides = (1..slideCount).joinToString("\n") {
            """<Override PartName="/ppt/slides/slide$it.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>
                $overrides
            </Types>
        """.trimIndent()
    }

    private fun buildPresentationXml(slideCount: Int): String {
        val ids = (1..slideCount).joinToString("") {
            """<p:sldId id="${255 + it}" r:id="rId$it"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <p:presentation xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <p:sldIdLst>$ids</p:sldIdLst>
            </p:presentation>
        """.trimIndent()
    }

    private fun buildPresentationRelsXml(slideCount: Int): String {
        val rels = (1..slideCount).joinToString("") {
            """<Relationship Id="rId$it" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide$it.xml"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>
        """.trimIndent()
    }

    private fun buildSlideXml(slide: PptSlide): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" vppBg="${slide.backgroundColorHex}" vppTrans="${slide.transition.name}">""")
        sb.append("<p:cSld><p:spTree>")
        for (el in slide.elements.sortedBy { it.zIndex }) {
            sb.append(
                """<p:sp vppX="${el.xPercent}" vppY="${el.yPercent}" vppW="${el.widthPercent}" vppH="${el.heightPercent}" vppRot="${el.rotationDeg}" vppFont="${escapeXml(el.fontFamily)}" vppSize="${el.fontSizePt}" vppBold="${if (el.isBold) "1" else "0"}" vppColor="${el.textColorHex}" vppFill="${el.fillColorHex}" vppType="${el.type.name}">"""
            )
            sb.append("""<p:txBody><a:p><a:r><a:rPr><a:latin typeface="${escapeXml(el.fontFamily)}"/></a:rPr><a:t>${escapeXml(el.text)}</a:t></a:r></a:p></p:txBody>""")
            sb.append("</p:sp>")
        }
        sb.append("</p:spTree></p:cSld></p:sld>")
        return sb.toString()
    }

    private fun buildNotesXml(notes: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><p:notes xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><a:t>${escapeXml(notes)}</a:t></p:notes>"""

    private fun extractTextFromXml(bytes: ByteArray): String {
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

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun createDefaultPresentation(title: String): PowerPointModel {
        val slides = listOf(
            PptSlide(
                id = "slide_1",
                slideNumber = 1,
                layoutName = "Slide Tiêu đề Chính",
                backgroundColorHex = "0F2547",
                transition = SlideTransitionType.FADE,
                speakerNotes = "Chào mừng hội đồng thẩm định. Giới thiệu tổng quan chiến lược chuyển đổi số và mục tiêu Quý IV.",
                elements = listOf(
                    SlideElement(
                        id = "s1_e1",
                        type = SlideElementType.TEXT_BOX,
                        text = title.uppercase(),
                        xPercent = 8f,
                        yPercent = 18f,
                        widthPercent = 84f,
                        heightPercent = 28f,
                        zIndex = 1,
                        fontFamily = "Calibri",
                        fontSizePt = 28,
                        isBold = true,
                        textColorHex = "FFFFFF",
                        fillColorHex = "1E3A8A"
                    ),
                    SlideElement(
                        id = "s1_e2",
                        type = SlideElementType.SHAPE_RECT,
                        text = "Báo cáo Chiến lược & Triển khai Hệ thống Văn Phòng Pro 4-in-1",
                        xPercent = 8f,
                        yPercent = 54f,
                        widthPercent = 84f,
                        heightPercent = 24f,
                        zIndex = 2,
                        fontFamily = "Calibri",
                        fontSizePt = 18,
                        isBold = false,
                        textColorHex = "FDE68A",
                        fillColorHex = "172554"
                    )
                )
            ),
            PptSlide(
                id = "slide_2",
                slideNumber = 2,
                layoutName = "Nội dung & Biểu đồ",
                backgroundColorHex = "1E293B",
                transition = SlideTransitionType.PUSH,
                speakerNotes = "Trình bày số liệu tăng trưởng hiệu suất xử lý tài liệu ngoại tuyến và bảo toàn định dạng OOXML.",
                elements = listOf(
                    SlideElement(
                        id = "s2_e1",
                        type = SlideElementType.TEXT_BOX,
                        text = "1. Hiệu năng & Tương thích Tài liệu Máy tính",
                        xPercent = 6f,
                        yPercent = 10f,
                        widthPercent = 88f,
                        heightPercent = 20f,
                        zIndex = 1,
                        fontFamily = "Calibri",
                        fontSizePt = 22,
                        isBold = true,
                        textColorHex = "FFFFFF",
                        fillColorHex = "0F766E"
                    ),
                    SlideElement(
                        id = "s2_e2",
                        type = SlideElementType.CHART,
                        text = "Biểu đồ: Tương thích DOCX (99%) • XLSX (98%) • PPTX (97%) • PDF (100%)",
                        xPercent = 6f,
                        yPercent = 36f,
                        widthPercent = 88f,
                        heightPercent = 48f,
                        zIndex = 2,
                        fontFamily = "Calibri",
                        fontSizePt = 16,
                        isBold = false,
                        textColorHex = "E2E8F0",
                        fillColorHex = "334155"
                    )
                )
            )
        )
        return PowerPointModel(title = title, slides = slides)
    }
}
