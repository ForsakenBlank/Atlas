package com.forsakenblank.atlas

import com.forsakenblank.atlas.util.CellFormat
import com.forsakenblank.atlas.util.CellValue
import com.forsakenblank.atlas.util.ColumnStyle
import com.forsakenblank.atlas.util.Sheet
import com.forsakenblank.atlas.util.SheetError
import com.forsakenblank.atlas.util.cellName
import com.forsakenblank.atlas.util.cellText
import com.forsakenblank.atlas.util.columnName
import com.forsakenblank.atlas.util.evaluateFormula
import com.forsakenblank.atlas.util.evaluateSheet
import com.forsakenblank.atlas.util.parseCellRef
import com.forsakenblank.atlas.util.shiftFormula
import com.forsakenblank.atlas.util.toCsv
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetFormulaTest {

    private val delta = 1e-9
    private val today = LocalDate.of(2026, 10, 7)

    private fun calc(formula: String, cells: Map<String, String> = emptyMap()): CellValue = evaluateFormula(formula, cells, today)

    private fun number(formula: String, cells: Map<String, String> = emptyMap()): Double {
        val value = calc(formula, cells)
        assertTrue("$formula gave $value", value is CellValue.Number)
        return (value as CellValue.Number).value
    }

    private fun error(formula: String, cells: Map<String, String> = emptyMap()): SheetError? =
        (calc(formula, cells) as? CellValue.Error)?.error

    private fun text(formula: String, cells: Map<String, String> = emptyMap()): String? =
        (calc(formula, cells) as? CellValue.Text)?.value

    @Test
    fun columnNamesRoundTrip() {
        assertEquals("A", columnName(0))
        assertEquals("Z", columnName(25))
        assertEquals("AA", columnName(26))
        assertEquals("AZ", columnName(51))
        assertEquals("ZZ", columnName(701))
        assertEquals("AAA", columnName(702))
        for (i in 0 until 800) assertEquals(i, parseCellRef(columnName(i) + "1")!!.col)
        assertEquals("C7", cellName(6, 2))
    }

    @Test
    fun refsAreCaseInsensitiveAndMayBeAbsolute() {
        val b3 = parseCellRef("b3")!!
        assertEquals(2, b3.row)
        assertEquals(1, b3.col)
        val absolute = parseCellRef("\$B\$3")!!
        assertTrue(absolute.rowAbsolute && absolute.colAbsolute)
        assertEquals("\$B\$3", absolute.toString())
        assertNull(parseCellRef("A0"))
        assertNull(parseCellRef("A"))
        assertNull(parseCellRef("ABCD1"))
        assertNull(parseCellRef("1A"))
    }

    @Test
    fun arithmeticAndPrecedence() {
        assertEquals(7.0, number("=1+2*3"), delta)
        assertEquals(9.0, number("=(1+2)*3"), delta)
        assertEquals(2.5, number("=10/4"), delta)
        assertEquals(1.0, number("=7-3-3"), delta)
        assertEquals(8.0, number("=2^3"), delta)
        assertEquals(512.0, number("=2^3^2"), delta)
        assertEquals(-4.0, number("=-2^2"), delta)
        assertEquals(0.5, number("=2^-1"), delta)
        assertEquals(-3.0, number("=-(1+2)"), delta)
        assertEquals(3.0, number("=--3"), delta)
        assertEquals(0.5, number("=50%"), delta)
        assertEquals(20.0, number("=200*10%"), delta)
        assertEquals(1500.0, number("=1.5e3"), delta)
        assertEquals(0.25, number("=.25"), delta)
        assertEquals(6.0, number("=  1 + 2 + 3  "), delta)
    }

    @Test
    fun numbersTypedAsTextCount() {
        val cells = mapOf("A1" to "10", "A2" to " 2.5 ", "A3" to "1,250", "A4" to "15%", "A5" to "hello", "A6" to "'7")
        assertEquals(1262.5, number("=A1+A2+A3", cells), delta)
        assertEquals(0.15, number("=A4", cells), delta)
        assertEquals(SheetError.VALUE, error("=A5+1", cells))
        // a leading ' keeps it as text but it still looks like a number
        assertEquals(8.0, number("=A6+1", cells), delta)
        assertEquals(4.0, number("=\"3\"+1"), delta)
    }

    @Test
    fun cellRefsAndRanges() {
        val cells = mapOf("A1" to "1", "A2" to "2", "A3" to "3", "B1" to "10", "B2" to "=A2*10", "b3" to "=\$a\$3*10")
        assertEquals(66.0, number("=SUM(A1:B3)", cells), delta)
        assertEquals(66.0, number("=sum(B3:A1)", cells), delta)
        assertEquals(20.0, number("=b2", cells), delta)
        assertEquals(30.0, number("=B3", cells), delta)
        assertEquals(SheetError.VALUE, error("=A1:A3", cells))
    }

    @Test
    fun emptyCellsAreZeroInMathsButSkippedByAverageAndCount() {
        val cells = mapOf("A1" to "4", "A3" to "8")
        assertEquals(4.0, number("=A1+A2", cells), delta)
        assertEquals(0.0, number("=A2", cells), delta)
        assertEquals(6.0, number("=AVERAGE(A1:A3)", cells), delta)
        assertEquals(2.0, number("=COUNT(A1:A3)", cells), delta)
        assertEquals(SheetError.DIV_ZERO, error("=AVERAGE(A2:A2)", cells))
        assertEquals(4.0, number("=MIN(A1:A3)", cells), delta)
        assertEquals(8.0, number("=MAX(A1:A3)", cells), delta)
        assertEquals(0.0, number("=MAX(C1:C5)", cells), delta)
    }

    @Test
    fun aggregateFunctions() {
        val cells = mapOf("A1" to "3", "A2" to "x", "A3" to "=1/0", "A4" to "5", "A5" to "=TRUE")
        assertEquals(8.0, number("=SUM(A1,A2:A2,A4)", cells), delta)
        assertEquals(SheetError.VALUE, error("=SUM(A1,\"x\")", cells))
        assertEquals(SheetError.DIV_ZERO, error("=SUM(A1:A5)", cells))
        assertEquals(2.0, number("=COUNT(A1:A5)", cells), delta)
        assertEquals(5.0, number("=COUNTA(A1:A5)", cells), delta)
        assertEquals(1.0, number("=COUNTA(A3)", cells), delta)
        assertEquals(0.0, number("=COUNT(A3)", cells), delta)
        assertEquals(4.0, number("=AVERAGE(A1,A4)", cells), delta)
        assertEquals(3.0, number("=MIN(A4,A1,9)", cells), delta)
        assertEquals(10.0, number("=SUM(1,2,3,4)"), delta)
    }

    @Test
    fun roundAbsSqrt() {
        assertEquals(3.0, number("=ROUND(2.5)"), delta)
        assertEquals(-3.0, number("=ROUND(-2.5)"), delta)
        assertEquals(2.68, number("=ROUND(2.675, 2)"), delta)
        assertEquals(1200.0, number("=ROUND(1234, -2)"), delta)
        assertEquals(4.5, number("=ABS(-4.5)"), delta)
        assertEquals(3.0, number("=SQRT(9)"), delta)
        assertEquals(SheetError.NUM, error("=SQRT(-1)"))
        assertEquals(SheetError.VALUE, error("=ROUND()"))
    }

    @Test
    fun logicAndComparisons() {
        assertEquals(CellValue.Bool(true), calc("=1<2"))
        assertEquals(CellValue.Bool(true), calc("=2>=2"))
        assertEquals(CellValue.Bool(true), calc("=1<>2"))
        assertEquals(CellValue.Bool(true), calc("=\"abc\"=\"ABC\""))
        assertEquals(CellValue.Bool(true), calc("=0.1+0.2=0.3"))
        assertEquals(CellValue.Bool(true), calc("=\"apple\"<\"banana\""))
        assertEquals(CellValue.Bool(true), calc("=A1=0"))
        assertEquals(CellValue.Bool(true), calc("=A1=\"\""))
        assertEquals("big", text("=IF(5>3, \"big\", \"small\")"))
        assertEquals("small", text("=IF(A1, \"big\", \"small\")"))
        assertEquals(CellValue.Bool(false), calc("=IF(FALSE, 1)"))
        // the branch that is not taken is never worked out
        assertEquals(1.0, number("=IF(TRUE, 1, 1/0)"), delta)
        assertEquals(CellValue.Bool(true), calc("=AND(TRUE, 1, 2>1)"))
        assertEquals(CellValue.Bool(false), calc("=AND(TRUE, FALSE)"))
        assertEquals(CellValue.Bool(true), calc("=OR(FALSE, 0, 1)"))
        assertEquals(CellValue.Bool(false), calc("=NOT(TRUE)"))
        assertEquals(CellValue.Bool(true), calc("=AND(A1:B2)", mapOf("A1" to "1", "B2" to "TRUE", "A2" to "note")))
    }

    @Test
    fun textFunctions() {
        val cells = mapOf("A1" to "Bread", "B1" to "2.5")
        assertEquals("Bread costs 2.5", text("=A1&\" costs \"&B1", cells))
        assertEquals("Bread2.5", text("=CONCAT(A1:B1)", cells))
        assertEquals("ab1TRUE", text("=CONCAT(\"a\", \"b\", 1, TRUE)"))
        assertEquals(5.0, number("=LEN(A1)", cells), delta)
        assertEquals("BREAD", text("=UPPER(A1)", cells))
        assertEquals("bread", text("=lower(A1)", cells))
        assertEquals("say \"hi\"", text("=\"say \"\"hi\"\"\""))
        assertEquals("0.3", text("=0.1+0.2&\"\""))
        assertEquals("2026-10-07", text("=TODAY()"))
    }

    @Test
    fun errorsShowAndSpread() {
        val cells = mapOf("A1" to "=1/0", "A2" to "=A1+1", "A3" to "=SUM(A1:A2)", "A4" to "=A2&\"x\"")
        val values = evaluateSheet(cells, rows = 4, columns = 1, today = today)
        for (name in listOf("A1", "A2", "A3", "A4")) assertEquals(CellValue.Error(SheetError.DIV_ZERO), values[name])
        assertEquals(SheetError.NAME, error("=FOO(1)"))
        assertEquals(SheetError.NAME, error("=banana+1"))
        assertEquals(SheetError.PARSE, error("=1+"))
        assertEquals(SheetError.PARSE, error("=(1+2"))
        assertEquals(SheetError.PARSE, error("=\"open"))
        assertEquals(SheetError.REF, error("=#REF!+1"))
        assertEquals(SheetError.VALUE, error("=\"a\"*2"))
        assertEquals(SheetError.DIV_ZERO, error("=0^-1"))
    }

    @Test
    fun circularRefsAreCaught() {
        val cells = mapOf(
            "A1" to "=B1+1",
            "B1" to "=C1+1",
            "C1" to "=A1+1",
            "D1" to "=D1",
            "A2" to "=A1*2", // reads from the loop
            "B2" to "=COUNTA(A1:C1)",
            "C2" to "5",
            "D2" to "=C2+1", // has nothing to do with it
            "E1" to "=SUM(E1:E3)",
        )
        val values = evaluateSheet(cells, rows = 3, columns = 5, today = today)
        val cycle = CellValue.Error(SheetError.CYCLE)
        for (name in listOf("A1", "B1", "C1", "D1", "A2", "E1")) assertEquals(name, cycle, values[name])
        assertEquals(CellValue.Number(3.0), values["B2"])
        assertEquals(CellValue.Number(6.0), values["D2"])
    }

    @Test
    fun bigSheetsRecalculateQuickly() {
        val rows = 200
        val columns = 26
        val cells = HashMap<String, String>()
        for (c in 0 until columns) {
            cells[cellName(0, c)] = "${c + 1}"
            for (r in 1 until rows - 1) cells[cellName(r, c)] = "=${cellName(r - 1, c)}+1"
            cells[cellName(rows - 1, c)] = "=SUM(${cellName(0, c)}:${cellName(rows - 2, c)})"
        }
        // warm up once so the timing is not mostly class loading
        evaluateSheet(cells, rows, columns, today)
        val started = System.nanoTime()
        val values = evaluateSheet(cells, rows, columns, today)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $millis ms", millis < 500)
        // column A is 1, 2, ... 199 and the last row adds them up
        assertEquals(CellValue.Number(199.0 * 200 / 2), values[cellName(rows - 1, 0)])
        assertEquals(CellValue.Number(26.0 + 198), values[cellName(rows - 2, 25)])
    }

    @Test
    fun longChainsDoNotOverflowTheStack() {
        val rows = 5000
        val cells = HashMap<String, String>()
        cells["A1"] = "1"
        for (r in 1 until rows) cells["A${r + 1}"] = "=A$r+1"
        var result: CellValue? = null
        // a small stack like a phone's background threads
        val thread = Thread(null, { result = evaluateSheet(cells, rows, 1, today)["A$rows"] }, "sheet", 256 * 1024)
        thread.start()
        thread.join()
        assertEquals(CellValue.Number(rows.toDouble()), result)
    }

    @Test
    fun insertingRowsShiftsRefs() {
        assertEquals("=A1+A4", shiftFormula("=A1+A3", onRows = true, at = 1, count = 1))
        assertEquals("=SUM(A1:A6)", shiftFormula("=SUM(A1:A5)", onRows = true, at = 2, count = 1))
        assertEquals("=SUM(A2:A6)", shiftFormula("=SUM(A1:A5)", onRows = true, at = 0, count = 1))
        assertEquals("=SUM(A1:A5)", shiftFormula("=SUM(A1:A5)", onRows = true, at = 5, count = 1))
        assertEquals("=\$B\$5*2", shiftFormula("=\$B\$3*2", onRows = true, at = 0, count = 2))
        // text in quotes and function names are left alone
        assertEquals("=CONCAT(\"A3\", A4)", shiftFormula("=CONCAT(\"A3\", A3)", onRows = true, at = 0, count = 1))
        assertEquals("plain A3", shiftFormula("plain A3", onRows = true, at = 0, count = 1))
    }

    @Test
    fun deletingRowsAndColumnsShiftsRefs() {
        assertEquals("=#REF!+A2", shiftFormula("=A2+A3", onRows = true, at = 1, count = -1))
        assertEquals("=SUM(A1:A4)", shiftFormula("=SUM(A1:A5)", onRows = true, at = 2, count = -1))
        assertEquals("=SUM(A1:A3)", shiftFormula("=SUM(A1:A5)", onRows = true, at = 3, count = -2))
        assertEquals("=SUM(A1:A2)", shiftFormula("=SUM(A2:A3)", onRows = true, at = 0, count = -1))
        assertEquals("=SUM(#REF!)", shiftFormula("=SUM(A2:A3)", onRows = true, at = 1, count = -2))
        assertEquals("=A1+B1", shiftFormula("=A1+C1", onRows = false, at = 1, count = -1))
        assertEquals("=#REF!", shiftFormula("=b1", onRows = false, at = 1, count = -1))
        assertEquals("=SUM(A1:C1)", shiftFormula("=SUM(A1:B1)", onRows = false, at = 1, count = 1))
    }

    @Test
    fun sheetInsertAndDeleteMoveCells() {
        val sheet = Sheet(rows = 3, columns = 2, cells = mapOf("A1" to "1", "A2" to "2", "A3" to "=SUM(A1:A2)", "B2" to "=A2*2"))
            .withStyle(1, ColumnStyle(width = 140, format = CellFormat.CURRENCY))
        val inserted = sheet.insertRows(1)
        assertEquals(4, inserted.rows)
        assertEquals("=SUM(A1:A3)", inserted.raw(3, 0))
        assertEquals("=A3*2", inserted.raw(2, 1))
        assertEquals("", inserted.raw(1, 0))
        assertEquals(CellValue.Number(3.0), inserted.evaluate(today)["A4"])

        val deleted = sheet.deleteRows(1)
        assertEquals(2, deleted.rows)
        assertEquals("=SUM(A1:A1)", deleted.raw(1, 0))
        assertEquals(null, deleted.cells["B2"])

        val noColumnA = sheet.deleteColumns(0)
        assertEquals(1, noColumnA.columns)
        assertEquals("=#REF!*2", noColumnA.raw(1, 0))
        assertEquals(CellValue.Error(SheetError.REF), noColumnA.evaluate(today)["A2"])
        assertEquals(CellFormat.CURRENCY, noColumnA.style(0).format)

        val newColumn = sheet.insertColumns(0)
        assertEquals(3, newColumn.columns)
        assertEquals("=B2*2", newColumn.raw(1, 2))
        assertEquals(140, newColumn.style(2).width)

        // the last row or column can not go
        assertEquals(1, Sheet(rows = 1).deleteRows(0).rows)
    }

    @Test
    fun savedSheetsReadBack() {
        val sheet = Sheet(rows = 4, columns = 3, cells = mapOf("A1" to "Rent", "B1" to "=950*12"))
            .withStyle(1, ColumnStyle(format = CellFormat.CURRENCY))
        assertEquals(sheet, Sheet.fromJson(sheet.toJson()))
        assertEquals(Sheet(), Sheet.fromJson("{}"))
        assertNull(Sheet.fromJson("not a sheet"))
    }

    @Test
    fun cellsShowTheirFormat() {
        assertEquals("1,234.50", cellText("=1234.5", CellValue.Number(1234.5), CellFormat.DECIMALS, "£"))
        assertEquals("£1,234.50", cellText("1234.5", CellValue.Number(1234.5), CellFormat.CURRENCY, "£"))
        assertEquals("12.5%", cellText("=0.125", CellValue.Number(0.125), CellFormat.PERCENT, "£"))
        assertEquals("0.3333333333", cellText("=1/3", CellValue.Number(1.0 / 3), CellFormat.AUTO, "£"))
        assertEquals("1,250", cellText("1,250", CellValue.Number(1250.0), CellFormat.AUTO, "£"))
        assertEquals("#DIV/0!", cellText("=1/0", CellValue.Error(SheetError.DIV_ZERO), CellFormat.CURRENCY, "£"))
    }

    @Test
    fun csvQuotesWhatItNeedsTo() {
        val sheet = Sheet(
            rows = 5,
            columns = 4,
            cells = mapOf("A1" to "Item", "B1" to "Cost", "A2" to "Milk, semi", "B2" to "1.2", "A3" to "Say \"hi\"", "B3" to "=B2*2"),
        ).withStyle(1, ColumnStyle(format = CellFormat.CURRENCY))
        val csv = sheet.toCsv(sheet.evaluate(today), "£")
        assertEquals("Item,Cost\n\"Milk, semi\",£1.20\n\"Say \"\"hi\"\"\",£2.40", csv)
        assertEquals("", Sheet().toCsv(Sheet().evaluate(today), "£"))
    }
}
