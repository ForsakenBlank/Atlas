package com.forsakenblank.atlas.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val DEFAULT_COLUMN_WIDTH = 96

@Serializable
enum class CellFormat(val label: String) {
    AUTO("Auto"),
    DECIMALS("2 decimals"),
    CURRENCY("Currency"),
    PERCENT("Percent"),
}

@Serializable
data class ColumnStyle(
    val width: Int = DEFAULT_COLUMN_WIDTH, // dp
    val format: CellFormat = CellFormat.AUTO,
)

// a whole spreadsheet as it is saved, every cell keeps exactly what was typed into it
@Serializable
data class Sheet(
    val rows: Int = 20,
    val columns: Int = 6,
    val cells: Map<String, String> = emptyMap(), // keyed like "B3"
    val styles: Map<Int, ColumnStyle> = emptyMap(), // keyed by column index, only columns that were changed
) {
    fun raw(row: Int, col: Int): String = cells[cellName(row, col)].orEmpty()

    fun style(col: Int): ColumnStyle = styles[col] ?: ColumnStyle()

    fun withCell(row: Int, col: Int, raw: String): Sheet {
        val key = cellName(row, col)
        return copy(cells = if (raw.isBlank()) cells - key else cells + (key to raw))
    }

    fun withStyle(col: Int, style: ColumnStyle): Sheet =
        copy(styles = if (style == ColumnStyle()) styles - col else styles + (col to style))

    fun evaluate(today: LocalDate = LocalDate.now()): SheetValues = evaluateSheet(cells, rows, columns, today)

    fun insertRows(at: Int, count: Int = 1): Sheet = reshape(onRows = true, at = at, count = count)

    fun deleteRows(at: Int, count: Int = 1): Sheet = reshape(onRows = true, at = at, count = -count)

    fun insertColumns(at: Int, count: Int = 1): Sheet = reshape(onRows = false, at = at, count = count)

    fun deleteColumns(at: Int, count: Int = 1): Sheet = reshape(onRows = false, at = at, count = -count)

    // moves cells out of the way or closes the gap, and fixes every formula to match
    private fun reshape(onRows: Boolean, at: Int, count: Int): Sheet {
        val size = if (onRows) rows else columns
        val limit = if (onRows) MAX_ROWS else MAX_COLUMNS
        if (count == 0 || size + count !in 1..limit || at !in 0..size || at - count > size) return this

        fun move(index: Int): Int? = when {
            index < at -> index
            count > 0 -> index + count
            index < at - count -> null
            else -> index + count
        }

        val moved = HashMap<String, String>(cells.size * 2)
        for ((key, raw) in cells) {
            val ref = parseCellRef(key) ?: continue
            val index = move(if (onRows) ref.row else ref.col) ?: continue
            val name = if (onRows) cellName(index, ref.col) else cellName(ref.row, index)
            moved[name] = shiftFormula(raw, onRows, at, count)
        }
        val movedStyles = if (onRows) styles else styles.mapNotNull { (col, style) -> move(col)?.let { it to style } }.toMap()
        return copy(
            rows = if (onRows) rows + count else rows,
            columns = if (onRows) columns else columns + count,
            cells = moved,
            styles = movedStyles,
        )
    }

    fun toJson(): String = sheetJson.encodeToString(serializer(), this)

    companion object {
        const val MAX_ROWS = 1000
        const val MAX_COLUMNS = 52

        // null when the text is not a sheet at all
        fun fromJson(text: String): Sheet? =
            runCatching { sheetJson.decodeFromString(serializer(), text) }.getOrNull()
                ?.let { it.copy(rows = it.rows.coerceIn(1, MAX_ROWS), columns = it.columns.coerceIn(1, MAX_COLUMNS)) }
    }
}

private val sheetJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// what a cell shows in the grid. a typed value in an auto column shows as typed,
// formula results and formatted columns are tidied up
fun cellText(raw: String, value: CellValue, format: CellFormat, currencySymbol: String): String = when (value) {
    CellValue.Empty -> ""
    is CellValue.Text -> value.value
    is CellValue.Bool -> if (value.value) "TRUE" else "FALSE"
    is CellValue.Error -> value.error.label
    is CellValue.Number -> when (format) {
        CellFormat.AUTO -> if (isFormula(raw)) autoNumber(value.value) else raw.trim()
        CellFormat.DECIMALS -> formatMoney(value.value, "")
        CellFormat.CURRENCY -> formatMoney(value.value, currencySymbol)
        CellFormat.PERCENT -> niceNumber(value.value * 100, 2) + "%"
    }
}

// up to 10 significant digits, very big or very small numbers switch to 1.5E+12 style
fun autoNumber(value: Double): String {
    if (value == 0.0) return "0"
    val rounded = BigDecimal(value).round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
    return if (abs(value) >= 1e12 || abs(value) < 1e-6) rounded.toString() else rounded.toPlainString()
}

// the sheet as comma separated text, the way the cells look, with empty edges trimmed off
fun Sheet.toCsv(values: SheetValues, currencySymbol: String): String {
    val table = List(rows) { r -> List(columns) { c -> cellText(raw(r, c), values[r, c], style(c).format, currencySymbol) } }
    val lastRow = table.indexOfLast { row -> row.any { it.isNotEmpty() } }
    val lastCol = (0 until columns).lastOrNull { c -> table.any { it[c].isNotEmpty() } } ?: -1
    if (lastRow < 0 || lastCol < 0) return ""
    return table.take(lastRow + 1).joinToString("\n") { row -> row.take(lastCol + 1).joinToString(",") { csvField(it) } }
}

fun csvField(text: String): String =
    if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' } || text != text.trim()) {
        "\"" + text.replace("\"", "\"\"") + "\""
    } else {
        text
    }
