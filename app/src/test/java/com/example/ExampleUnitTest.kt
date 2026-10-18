package com.example

import com.example.engine.ExcelCell
import com.example.engine.ExcelEngine
import com.example.engine.ExcelSheet
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun verifyExcelFormulaFunctionsEvaluation() {
        val cells = mapOf(
            "0_0" to ExcelCell(0, 0, "A100"),
            "0_1" to ExcelCell(0, 1, "10"),
            "1_0" to ExcelCell(1, 0, "B200"),
            "1_1" to ExcelCell(1, 1, "20"),
            "2_0" to ExcelCell(2, 0, "C300"),
            "2_1" to ExcelCell(2, 1, "30")
        )
        val sheet = ExcelSheet(id = "s1", name = "Sheet1", cells = cells)
        val sheets = listOf(sheet)

        assertEquals("60", ExcelEngine.evaluateFormula("=SUM(B1:B3)", sheet, sheets).value)
        assertEquals("20", ExcelEngine.evaluateFormula("=AVERAGE(B1:B3)", sheet, sheets).value)
        assertEquals("3", ExcelEngine.evaluateFormula("=COUNT(B1:B3)", sheet, sheets).value)
        assertEquals("Đạt", ExcelEngine.evaluateFormula("=IF(B3>=25,\"Đạt\",\"Chưa đạt\")", sheet, sheets).value)
        assertEquals("50", ExcelEngine.evaluateFormula("=SUMIF(B1:B3,\">=20\",B1:B3)", sheet, sheets).value)
        assertEquals("20", ExcelEngine.evaluateFormula("=VLOOKUP(\"B200\",A1:B3,2,0)", sheet, sheets).value)
        assertEquals("30", ExcelEngine.evaluateFormula("=XLOOKUP(\"C300\",A1:A3,B1:B3)", sheet, sheets).value)
        assertEquals("2", ExcelEngine.evaluateFormula("=MATCH(\"B200\",A1:A3,0)", sheet, sheets).value)
        assertEquals("B2", ExcelEngine.evaluateFormula("=LEFT(A2,2)", sheet, sheets).value)
        assertEquals("12.35", ExcelEngine.evaluateFormula("=ROUND(12.346,2)", sheet, sheets).value)
    }
}
