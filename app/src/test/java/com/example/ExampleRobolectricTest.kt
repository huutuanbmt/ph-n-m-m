package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.ExcelEngine
import com.example.engine.FileSafetyUtils
import com.example.engine.FontManagerEngine
import com.example.engine.PaperSizePreset
import com.example.engine.PdfAnnotationItem
import com.example.engine.PdfAnnotationType
import com.example.engine.PdfEngine
import com.example.engine.PdfStructureKind
import com.example.engine.PowerPointEngine
import com.example.engine.WordEngine
import com.example.engine.WordParagraph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun verifyAppNameAndResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Văn Phòng Pro", appName)
    }

    @Test
    fun verifyWordDocx9StepRoundTripAndVietnamesePreservation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "robo_word_test").apply { mkdirs() }
        val origFile = File(dir, "orig_a4.docx")
        val copyFile = File(dir, "edited_copy_a4.docx")

        // Step 1-2: Create & open original DOCX (Times New Roman 13pt, A4, 1.5 spacing, TOC, Table)
        val initialModel = WordEngine.createDefaultDocument("Công văn Hành chính Chuẩn A4")
        WordEngine.writeDocxToFile(initialModel, origFile)
        assertTrue(FileSafetyUtils.verifyFileIntegrity(origFile, "docx"))

        val opened = WordEngine.openDocument(origFile, "Công văn Hành chính Chuẩn A4")
        assertEquals(PaperSizePreset.A4, opened.pageSetup.paperSize)
        assertTrue(opened.hasAutoToc)
        assertTrue(opened.tables.isNotEmpty())

        // Step 3-4: Edit content with Vietnamese diacritics and save atomically as copy
        val vnParagraph = WordParagraph(
            id = "p_vn_test",
            text = "Kiểm thử Tiếng Việt chuẩn Unicode: Nguyễn Hữu Tuấn - 0943429595 - Độc lập, Tự do, Hạnh phúc.",
            fontFamily = "Times New Roman",
            fontSizePt = 13,
            lineSpacingMultiplier = 1.5f
        )
        val modifiedModel = opened.copy(paragraphs = opened.paragraphs + vnParagraph)
        val saveRes = FileSafetyUtils.atomicSaveFile(copyFile, "docx") { temp ->
            WordEngine.writeDocxToFile(modifiedModel, temp)
        }
        assertTrue(saveRes.success)
        assertTrue(saveRes.sha256.isNotEmpty())

        // Step 5-7: Reopen saved copy and compare structure, font, page setup, and Vietnamese text
        val reopened = WordEngine.openDocument(copyFile, modifiedModel.title)
        assertEquals(modifiedModel.paragraphs.size, reopened.paragraphs.size)
        assertEquals("Times New Roman", reopened.paragraphs.last().fontFamily)
        assertEquals(13, reopened.paragraphs.last().fontSizePt)
        assertTrue(reopened.paragraphs.last().text.contains("Nguyễn Hữu Tuấn - 0943429595"))
        assertEquals(opened.tables.first().rows.size, reopened.tables.first().rows.size)
    }

    @Test
    fun verifyExcelXlsxFormulasAndMultiSheetRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "robo_excel_test").apply { mkdirs() }
        val xlsxFile = File(dir, "financial_test.xlsx")

        val wb = ExcelEngine.createDefaultWorkbook("Báo cáo Tài chính")
        assertEquals(2, wb.sheets.size)
        // Initial SUM(C2:C5) = 5 + 12 + 3 + 20 = 40
        assertEquals("40", wb.sheets[0].getCell(5, 2).displayValue)

        // Modify cell C2 from 5 to 15 -> SUM(C2:C5) should become 50
        val sheet0 = wb.sheets[0]
        val newCells = sheet0.cells.toMutableMap()
        newCells["1_2"] = sheet0.getCell(1, 2).copy(rawValue = "15")
        val updatedWb = ExcelEngine.recalculateWorkbook(
            wb.copy(sheets = listOf(sheet0.copy(cells = newCells), wb.sheets[1]))
        )
        assertEquals("50", updatedWb.sheets[0].getCell(5, 2).displayValue)

        // Save to disk and reopen
        ExcelEngine.writeXlsxToFile(updatedWb, xlsxFile)
        assertTrue(FileSafetyUtils.verifyFileIntegrity(xlsxFile, "xlsx"))
        val reopened = ExcelEngine.openWorkbook(xlsxFile, "Báo cáo Tài chính")
        assertEquals(2, reopened.sheets.size)
        assertEquals("50", reopened.sheets[0].getCell(5, 2).displayValue)
    }

    @Test
    fun verifyPowerPointPptxRoundTrip() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "robo_ppt_test").apply { mkdirs() }
        val pptxFile = File(dir, "slides_test.pptx")

        val model = PowerPointEngine.createDefaultPresentation("Bài Trình chiếu Chiến lược")
        PowerPointEngine.writePptxToFile(model, pptxFile)
        assertTrue(FileSafetyUtils.verifyFileIntegrity(pptxFile, "pptx"))

        val reopened = PowerPointEngine.openPresentation(pptxFile, "Bài Trình chiếu Chiến lược")
        assertEquals(model.slides.size, reopened.slides.size)
        assertTrue(reopened.slides.first().elements.isNotEmpty())
    }

    @Test
    fun verifyPdfStructureDetectionAndAnnotationExport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "robo_pdf_test").apply { mkdirs() }
        val textPdf = File(dir, "true_text.pdf")
        val signedPdf = File(dir, "signed.pdf")
        val scannedPdf = File(dir, "scanned.pdf")
        val exportedPdf = File(dir, "exported_annotated.pdf")

        PdfEngine.createSamplePdfFile(textPdf, "PDF Văn bản thật", PdfStructureKind.TRUE_TEXT)
        PdfEngine.createSamplePdfFile(signedPdf, "PDF Đã ký số", PdfStructureKind.DIGITALLY_SIGNED)
        PdfEngine.createSamplePdfFile(scannedPdf, "PDF Ảnh quét", PdfStructureKind.SCANNED_IMAGE)

        val stateText = PdfEngine.inspectAndOpenPdf(textPdf)
        val stateSigned = PdfEngine.inspectAndOpenPdf(signedPdf)
        val stateScanned = PdfEngine.inspectAndOpenPdf(scannedPdf)

        assertEquals(PdfStructureKind.TRUE_TEXT, stateText.structureKind)
        assertEquals(PdfStructureKind.DIGITALLY_SIGNED, stateSigned.structureKind)
        assertEquals(PdfStructureKind.SCANNED_IMAGE, stateScanned.structureKind)

        val annotatedState = stateText.copy(
            annotations = listOf(
                PdfAnnotationItem(
                    id = "ann_1",
                    pageIndex = 0,
                    type = PdfAnnotationType.STICKY_NOTE,
                    text = "Ghi chú kiểm thử PDF"
                )
            )
        )
        val ok = PdfEngine.savePdfWithAnnotations(textPdf, annotatedState, exportedPdf)
        assertTrue(ok)
        assertTrue(FileSafetyUtils.verifyFileIntegrity(exportedPdf, "pdf"))
        assertNotEquals("", FileSafetyUtils.computeSha256(exportedPdf))
    }

    @Test
    fun verifyFontManagerSubstitutionsAndVietnameseSupport() {
        val builtIn = FontManagerEngine.defaultBuiltInFonts()
        val report = FontManagerEngine.analyzeDocumentFonts(
            listOf("Times New Roman", "Calibri", "Arial"),
            builtIn
        )
        assertTrue(report.missingFonts.isEmpty())
        assertTrue(report.vietnameseReady)
    }
}
