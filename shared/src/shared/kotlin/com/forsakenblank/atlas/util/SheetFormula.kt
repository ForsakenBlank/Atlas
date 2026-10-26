package com.forsakenblank.atlas.util

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

enum class SheetError(val label: String) {
    DIV_ZERO("#DIV/0!"),
    REF("#REF!"),
    NAME("#NAME?"),
    VALUE("#VALUE!"),
    CYCLE("#CYCLE!"),
    NUM("#NUM!"),
    PARSE("#ERROR!"), // a formula that could not be read, like =1+
}

sealed interface CellValue {
    data object Empty : CellValue
    data class Number(val value: Double) : CellValue
    data class Text(val value: String) : CellValue
    data class Bool(val value: Boolean) : CellValue
    data class Error(val error: SheetError) : CellValue
}

data class CellRef(val row: Int, val col: Int, val rowAbsolute: Boolean = false, val colAbsolute: Boolean = false) {
    override fun toString(): String =
        (if (colAbsolute) "$" else "") + columnName(col) + (if (rowAbsolute) "$" else "") + (row + 1)
}

// 0 is A, 25 is Z, 26 is AA
fun columnName(index: Int): String {
    val name = StringBuilder()
    var n = index + 1
    while (n > 0) {
        name.append('A' + (n - 1) % 26)
        n = (n - 1) / 26
    }
    return name.reverse().toString()
}

fun cellName(row: Int, col: Int): String = columnName(col) + (row + 1)

// reads "B3", "b3" or "$B$3", rows and columns count from 0
fun parseCellRef(text: String): CellRef? {
    var i = 0
    val colAbsolute = text.startsWith('$')
    if (colAbsolute) i++
    var col = 0
    val lettersStart = i
    while (i < text.length && text[i].uppercaseChar() in 'A'..'Z') {
        col = col * 26 + (text[i].uppercaseChar() - 'A' + 1)
        i++
    }
    if (i - lettersStart !in 1..3) return null
    val rowAbsolute = text.getOrNull(i) == '$'
    if (rowAbsolute) i++
    val digitsStart = i
    var row = 0
    while (i < text.length && text[i] in '0'..'9') {
        row = row * 10 + (text[i] - '0')
        i++
    }
    if (i != text.length || i - digitsStart !in 1..7 || row < 1) return null
    return CellRef(row - 1, col - 1, rowAbsolute = rowAbsolute, colAbsolute = colAbsolute)
}

private val simpleNumberPattern = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")
private val groupedNumberPattern = Regex("""[+-]?\d{1,3}(,\d{3})+(\.\d*)?""")

// what someone typed into a cell read as a number, so "12", "1,250.50" and "15%" all count
fun parseCellNumber(text: String): Double? {
    var clean = text.trim()
    val percent = clean.endsWith('%')
    if (percent) clean = clean.dropLast(1).trimEnd()
    val number = when {
        simpleNumberPattern.matches(clean) -> clean.toDoubleOrNull()
        groupedNumberPattern.matches(clean) -> clean.replace(",", "").toDoubleOrNull()
        else -> null
    } ?: return null
    if (!number.isFinite()) return null
    return if (percent) number / 100 else number
}

// a cell that is not a formula, a leading ' keeps something like '007 as text
fun literalValue(raw: String): CellValue {
    if (raw.isBlank()) return CellValue.Empty
    if (raw.startsWith('\'')) return CellValue.Text(raw.substring(1))
    if (raw.trim().equals("TRUE", ignoreCase = true)) return CellValue.Bool(true)
    if (raw.trim().equals("FALSE", ignoreCase = true)) return CellValue.Bool(false)
    return parseCellNumber(raw)?.let { CellValue.Number(it) } ?: CellValue.Text(raw)
}

fun isFormula(raw: String): Boolean = raw.length > 1 && raw.startsWith('=')

// numbers inside joined text, up to 15 significant digits like other spreadsheets
fun plainNumber(value: Double): String {
    if (value == 0.0) return "0"
    val rounded = BigDecimal(value).round(MathContext(15, RoundingMode.HALF_UP)).stripTrailingZeros()
    return if (abs(value) >= 1e15 || abs(value) < 1e-9) rounded.toString() else rounded.toPlainString()
}

// the values of every cell in a sheet, anything outside it reads as empty
class SheetValues internal constructor(
    val rows: Int,
    val columns: Int,
    private val values: Array<CellValue?>,
) {
    operator fun get(row: Int, col: Int): CellValue =
        if (row in 0 until rows && col in 0 until columns) values[row * columns + col] ?: CellValue.Empty else CellValue.Empty

    operator fun get(name: String): CellValue = parseCellRef(name)?.let { get(it.row, it.col) } ?: CellValue.Empty

    companion object {
        val EMPTY = SheetValues(0, 0, emptyArray())
    }
}

// works out every cell. formulas are put in dependency order first so a long chain of
// cells never recurses deeply, and anything caught in a loop shows #CYCLE!.
// the date from TODAY() is text like 2026-10-07, since cells have no date format
fun evaluateSheet(
    cells: Map<String, String>,
    rows: Int,
    columns: Int,
    today: LocalDate = LocalDate.now(),
): SheetValues {
    val engine = Engine(rows, columns, today)
    for ((key, raw) in cells) {
        val ref = parseCellRef(key) ?: continue
        if (ref.row < rows && ref.col < columns && raw.isNotBlank()) engine.raw[ref.row * columns + ref.col] = raw
    }
    engine.run()
    return SheetValues(rows, columns, engine.values)
}

// one formula on its own, mostly handy for tests and previews
fun evaluateFormula(formula: String, cells: Map<String, String> = emptyMap(), today: LocalDate = LocalDate.now()): CellValue {
    var rows = 1
    var columns = 1
    for (key in cells.keys) {
        val ref = parseCellRef(key) ?: continue
        rows = max(rows, ref.row + 1)
        columns = max(columns, ref.col + 1)
    }
    // the formula sits in a spare column to the right of everything else
    val spare = cellName(0, columns)
    return evaluateSheet(cells + (spare to formula), rows, columns + 1, today)[spare]
}

// rewrites the refs in a formula after rows or columns move. a positive count inserts that many
// before index `at`, a negative one deletes from `at`. refs to deleted cells become #REF!
fun shiftFormula(raw: String, onRows: Boolean, at: Int, count: Int): String {
    if (!isFormula(raw) || count == 0) return raw
    val body = raw.substring(1)
    val tokens = runCatching { tokenize(body) }.getOrElse { return raw }
    val out = StringBuilder("=")
    var copied = 0
    var i = 0

    fun move(index: Int): Int? = when {
        index < at -> index
        count > 0 -> index + count
        index < at - count -> null
        else -> index + count
    }

    fun replace(start: Int, end: Int, text: String) {
        out.append(body, copied, start).append(text)
        copied = end
    }

    while (i < tokens.size) {
        val token = tokens[i]
        val ref = if (token.kind == Kind.NAME && tokens.getOrNull(i + 1)?.kind != Kind.OPEN) parseCellRef(token.text) else null
        if (ref == null) {
            i++
            continue
        }
        val endToken = tokens.getOrNull(i + 2)?.takeIf { tokens[i + 1].kind == Kind.COLON && it.kind == Kind.NAME }
        val endRef = endToken?.let { parseCellRef(it.text) }
        if (endToken != null && endRef != null) {
            // a range shrinks when part of it goes and only breaks when all of it does
            val first = if (onRows) min(ref.row, endRef.row) else min(ref.col, endRef.col)
            val last = if (onRows) max(ref.row, endRef.row) else max(ref.col, endRef.col)
            val newFirst: Int
            val newLast: Int
            if (count > 0) {
                newFirst = if (first >= at) first + count else first
                newLast = if (last >= at) last + count else last
            } else {
                val gone = -count
                newFirst = when {
                    first < at -> first
                    first < at + gone -> at
                    else -> first - gone
                }
                newLast = when {
                    last < at -> last
                    last < at + gone -> at - 1
                    else -> last - gone
                }
            }
            when {
                newLast < newFirst -> replace(token.start, endToken.end, SheetError.REF.label)
                newFirst != first || newLast != last -> {
                    val a = if (onRows) ref.copy(row = newFirst) else ref.copy(col = newFirst)
                    val b = if (onRows) endRef.copy(row = newLast) else endRef.copy(col = newLast)
                    replace(token.start, endToken.end, "$a:$b")
                }
            }
            i += 3
        } else {
            val moved = move(if (onRows) ref.row else ref.col)
            when {
                moved == null -> replace(token.start, token.end, SheetError.REF.label)
                moved != (if (onRows) ref.row else ref.col) -> {
                    replace(token.start, token.end, (if (onRows) ref.copy(row = moved) else ref.copy(col = moved)).toString())
                }
            }
            i++
        }
    }
    out.append(body, copied, body.length)
    return out.toString()
}

// reading formulas

private enum class Kind { NUMBER, STRING, NAME, ERROR, OP, OPEN, CLOSE, COMMA, COLON }

private class Token(val kind: Kind, val text: String, val start: Int, val end: Int)

// thrown inside the engine and turned into an error value at the cell, no stack trace needed
private class Fail(val error: SheetError) : RuntimeException(null, null, false, false)

private const val OPERATOR_CHARS = "+-*/^&%=<>"

private fun tokenize(src: String): List<Token> {
    val tokens = ArrayList<Token>()
    var i = 0
    while (i < src.length) {
        val ch = src[i]
        val start = i
        when {
            ch.isWhitespace() -> i++
            ch.isDigit() || (ch == '.' && src.getOrNull(i + 1)?.isDigit() == true) -> {
                while (i < src.length && src[i].isDigit()) i++
                if (i < src.length && src[i] == '.') {
                    i++
                    while (i < src.length && src[i].isDigit()) i++
                }
                if (i < src.length && (src[i] == 'e' || src[i] == 'E')) {
                    var j = i + 1
                    if (j < src.length && (src[j] == '+' || src[j] == '-')) j++
                    if (j < src.length && src[j].isDigit()) {
                        i = j
                        while (i < src.length && src[i].isDigit()) i++
                    }
                }
                tokens += Token(Kind.NUMBER, src.substring(start, i), start, i)
            }
            ch == '"' -> {
                val text = StringBuilder()
                i++
                while (true) {
                    if (i >= src.length) throw Fail(SheetError.PARSE)
                    if (src[i] == '"') {
                        // two quotes in a row are one quote inside the text
                        if (src.getOrNull(i + 1) == '"') {
                            text.append('"')
                            i += 2
                            continue
                        }
                        i++
                        break
                    }
                    text.append(src[i++])
                }
                tokens += Token(Kind.STRING, text.toString(), start, i)
            }
            ch.isLetter() || ch == '$' || ch == '_' -> {
                while (i < src.length && (src[i].isLetterOrDigit() || src[i] == '$' || src[i] == '_' || src[i] == '.')) i++
                tokens += Token(Kind.NAME, src.substring(start, i), start, i)
            }
            ch == '#' -> {
                val error = SheetError.entries.firstOrNull { src.regionMatches(i, it.label, 0, it.label.length, ignoreCase = true) }
                    ?: throw Fail(SheetError.PARSE)
                i += error.label.length
                tokens += Token(Kind.ERROR, error.label, start, i)
            }
            ch in OPERATOR_CHARS -> {
                val pair = src.substring(i, min(i + 2, src.length))
                val op = if (pair == "<=" || pair == ">=" || pair == "<>") pair else ch.toString()
                i += op.length
                tokens += Token(Kind.OP, op, start, i)
            }
            ch == '(' -> tokens += Token(Kind.OPEN, "(", start, ++i)
            ch == ')' -> tokens += Token(Kind.CLOSE, ")", start, ++i)
            ch == ',' || ch == ';' -> tokens += Token(Kind.COMMA, ",", start, ++i)
            ch == ':' -> tokens += Token(Kind.COLON, ":", start, ++i)
            else -> throw Fail(SheetError.PARSE)
        }
    }
    return tokens
}

private sealed interface Expr {
    class Num(val value: Double) : Expr
    class Str(val value: String) : Expr
    class Bool(val value: Boolean) : Expr
    class Err(val error: SheetError) : Expr
    class Ref(val row: Int, val col: Int) : Expr
    class Range(val top: Int, val left: Int, val bottom: Int, val right: Int) : Expr
    class Name(val name: String) : Expr
    class Negate(val operand: Expr) : Expr
    class Percent(val operand: Expr) : Expr
    class Binary(val op: String, val left: Expr, val right: Expr) : Expr
    class Call(val name: String, val args: List<Expr>) : Expr
}

// lowest to highest: comparisons, &, + -, * /, unary minus, ^, then a trailing %.
// unlike Excel, -2^2 is -4 and 2^3^2 is 512, the way it works in maths class
private class Parser(private val tokens: List<Token>) {
    private var pos = 0

    fun parse(): Expr {
        if (tokens.isEmpty()) throw Fail(SheetError.PARSE)
        val expr = comparison()
        if (pos < tokens.size) throw Fail(SheetError.PARSE)
        return expr
    }

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun nextIsOp(vararg ops: String): Boolean = peek()?.let { it.kind == Kind.OP && it.text in ops } == true

    private fun comparison(): Expr {
        var left = concat()
        while (nextIsOp("=", "<>", "<", ">", "<=", ">=")) {
            val op = tokens[pos++].text
            left = Expr.Binary(op, left, concat())
        }
        return left
    }

    private fun concat(): Expr {
        var left = additive()
        while (nextIsOp("&")) {
            pos++
            left = Expr.Binary("&", left, additive())
        }
        return left
    }

    private fun additive(): Expr {
        var left = term()
        while (nextIsOp("+", "-")) {
            val op = tokens[pos++].text
            left = Expr.Binary(op, left, term())
        }
        return left
    }

    private fun term(): Expr {
        var left = unary()
        while (nextIsOp("*", "/")) {
            val op = tokens[pos++].text
            left = Expr.Binary(op, left, unary())
        }
        return left
    }

    private fun unary(): Expr = when {
        nextIsOp("-") -> {
            pos++
            Expr.Negate(unary())
        }
        nextIsOp("+") -> {
            pos++
            unary()
        }
        else -> power()
    }

    private fun power(): Expr {
        val base = postfix()
        if (nextIsOp("^")) {
            pos++
            return Expr.Binary("^", base, unary())
        }
        return base
    }

    private fun postfix(): Expr {
        var expr = primary()
        while (nextIsOp("%")) {
            pos++
            expr = Expr.Percent(expr)
        }
        return expr
    }

    private fun primary(): Expr {
        val token = peek() ?: throw Fail(SheetError.PARSE)
        pos++
        return when (token.kind) {
            Kind.NUMBER -> Expr.Num(token.text.toDoubleOrNull() ?: throw Fail(SheetError.PARSE))
            Kind.STRING -> Expr.Str(token.text)
            Kind.ERROR -> Expr.Err(SheetError.entries.first { it.label == token.text })
            Kind.NAME -> name(token)
            Kind.OPEN -> {
                val inner = comparison()
                if (peek()?.kind != Kind.CLOSE) throw Fail(SheetError.PARSE)
                pos++
                inner
            }
            else -> throw Fail(SheetError.PARSE)
        }
    }

    private fun name(token: Token): Expr {
        if (peek()?.kind == Kind.OPEN) {
            pos++
            val args = mutableListOf<Expr>()
            if (peek()?.kind == Kind.CLOSE) {
                pos++
            } else {
                while (true) {
                    args += comparison()
                    when (peek()?.kind) {
                        Kind.COMMA -> pos++
                        Kind.CLOSE -> {
                            pos++
                            break
                        }
                        else -> throw Fail(SheetError.PARSE)
                    }
                }
            }
            return Expr.Call(token.text.uppercase(), args)
        }
        val ref = parseCellRef(token.text)
        if (ref != null) {
            if (peek()?.kind != Kind.COLON) return Expr.Ref(ref.row, ref.col)
            pos++
            val endToken = peek()?.takeIf { it.kind == Kind.NAME } ?: throw Fail(SheetError.PARSE)
            val end = parseCellRef(endToken.text) ?: throw Fail(SheetError.PARSE)
            pos++
            return Expr.Range(min(ref.row, end.row), min(ref.col, end.col), max(ref.row, end.row), max(ref.col, end.col))
        }
        return when (token.text.uppercase()) {
            "TRUE" -> Expr.Bool(true)
            "FALSE" -> Expr.Bool(false)
            else -> Expr.Name(token.text)
        }
    }
}

private fun parseFormula(raw: String): Expr =
    try {
        Parser(tokenize(raw.substring(1))).parse()
    } catch (fail: Fail) {
        Expr.Err(fail.error)
    }

// working out values

private class Engine(private val rows: Int, private val columns: Int, private val today: LocalDate) {
    val raw = arrayOfNulls<String>(rows * columns)
    val values = arrayOfNulls<CellValue>(rows * columns)
    private val exprs = arrayOfNulls<Expr>(rows * columns)
    private val busy = BooleanArray(rows * columns)

    fun run() {
        val formulas = ArrayList<Int>()
        for (pos in raw.indices) {
            val text = raw[pos] ?: continue
            if (isFormula(text)) {
                exprs[pos] = parseFormula(text)
                formulas += pos
            } else {
                values[pos] = literalValue(text)
            }
        }
        if (formulas.isEmpty()) return

        // which formulas each formula reads, plain values need no ordering
        val isFormulaCell = BooleanArray(raw.size)
        formulas.forEach { isFormulaCell[it] = true }
        val reads = HashMap<Int, IntArray>(formulas.size * 2)
        val readers = HashMap<Int, MutableList<Int>>()
        val waiting = HashMap<Int, Int>(formulas.size * 2)
        for (pos in formulas) {
            val found = LinkedHashSet<Int>()
            collect(exprs[pos]!!, formulas, isFormulaCell, found)
            reads[pos] = found.toIntArray()
            waiting[pos] = found.size
            found.forEach { readers.getOrPut(it) { ArrayList() }.add(pos) }
        }

        // kahn's algorithm, a formula is ready once everything it reads is done
        val order = ArrayList<Int>(formulas.size)
        val queue = ArrayDeque<Int>()
        formulas.filter { waiting[it] == 0 }.forEach { queue.add(it) }
        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            order += pos
            readers[pos]?.forEach { reader ->
                val left = waiting.getValue(reader) - 1
                waiting[reader] = left
                if (left == 0) queue.add(reader)
            }
        }

        // the rest are in a loop or read from one. peeling off cells nothing else in the
        // rest reads leaves only the loops themselves
        val stuck = formulas.filter { waiting.getValue(it) > 0 }.toHashSet()
        if (stuck.isNotEmpty()) {
            val readCount = HashMap<Int, Int>()
            for (pos in stuck) readCount[pos] = readers[pos]?.count { it in stuck } ?: 0
            val peeled = ArrayList<Int>()
            val peelQueue = ArrayDeque(stuck.filter { readCount[it] == 0 })
            while (peelQueue.isNotEmpty()) {
                val pos = peelQueue.removeFirst()
                peeled += pos
                for (read in reads.getValue(pos)) {
                    if (read !in stuck) continue
                    val left = readCount.getValue(read) - 1
                    readCount[read] = left
                    if (left == 0) peelQueue.add(read)
                }
            }
            val peeledSet = peeled.toHashSet()
            stuck.filter { it !in peeledSet }.forEach { values[it] = CellValue.Error(SheetError.CYCLE) }
            order.addAll(peeled.asReversed())
        }

        for (pos in order) {
            if (values[pos] == null) values[pos] = evaluateCell(pos)
        }
    }

    private fun collect(expr: Expr, formulas: List<Int>, isFormulaCell: BooleanArray, into: MutableSet<Int>) {
        when (expr) {
            is Expr.Ref -> if (expr.row < rows && expr.col < columns) {
                val pos = expr.row * columns + expr.col
                if (isFormulaCell[pos]) into += pos
            }
            is Expr.Range -> {
                val bottom = min(expr.bottom, rows - 1)
                val right = min(expr.right, columns - 1)
                if (bottom < expr.top || right < expr.left) return
                val size = (bottom - expr.top + 1).toLong() * (right - expr.left + 1)
                if (size > formulas.size) {
                    for (pos in formulas) {
                        val r = pos / columns
                        val c = pos % columns
                        if (r in expr.top..bottom && c in expr.left..right) into += pos
                    }
                } else {
                    for (r in expr.top..bottom) for (c in expr.left..right) {
                        val pos = r * columns + c
                        if (isFormulaCell[pos]) into += pos
                    }
                }
            }
            is Expr.Negate -> collect(expr.operand, formulas, isFormulaCell, into)
            is Expr.Percent -> collect(expr.operand, formulas, isFormulaCell, into)
            is Expr.Binary -> {
                collect(expr.left, formulas, isFormulaCell, into)
                collect(expr.right, formulas, isFormulaCell, into)
            }
            is Expr.Call -> expr.args.forEach { collect(it, formulas, isFormulaCell, into) }
            else -> Unit
        }
    }

    private fun evaluateCell(pos: Int): CellValue {
        val expr = exprs[pos] ?: return values[pos] ?: CellValue.Empty
        busy[pos] = true
        val value = try {
            when (val result = eval(expr)) {
                // a formula pointing at an empty cell shows 0, like other spreadsheets
                CellValue.Empty -> CellValue.Number(0.0)
                else -> result
            }
        } catch (fail: Fail) {
            CellValue.Error(fail.error)
        }
        busy[pos] = false
        return value
    }

    private fun cell(row: Int, col: Int): CellValue {
        if (row >= rows || col >= columns) return CellValue.Empty
        val pos = row * columns + col
        values[pos]?.let { return it }
        if (exprs[pos] == null) return CellValue.Empty
        // only reached if the ordering missed something, kept safe all the same
        if (busy[pos]) throw Fail(SheetError.CYCLE)
        return evaluateCell(pos).also { values[pos] = it }
    }

    private fun eval(expr: Expr): CellValue = when (expr) {
        is Expr.Num -> CellValue.Number(expr.value)
        is Expr.Str -> CellValue.Text(expr.value)
        is Expr.Bool -> CellValue.Bool(expr.value)
        is Expr.Err -> throw Fail(expr.error)
        is Expr.Ref -> cell(expr.row, expr.col).also { if (it is CellValue.Error) throw Fail(it.error) }
        is Expr.Range -> throw Fail(SheetError.VALUE) // a range only makes sense inside a function
        is Expr.Name -> throw Fail(SheetError.NAME)
        is Expr.Negate -> number(-num(eval(expr.operand)))
        is Expr.Percent -> number(num(eval(expr.operand)) / 100)
        is Expr.Binary -> binary(expr)
        is Expr.Call -> call(expr)
    }

    private fun number(value: Double): CellValue {
        if (!value.isFinite()) throw Fail(SheetError.NUM)
        // -0 would show as "-0"
        return CellValue.Number(if (value == 0.0) 0.0 else value)
    }

    private fun binary(expr: Expr.Binary): CellValue {
        val left = eval(expr.left)
        val right = eval(expr.right)
        return when (expr.op) {
            "+" -> number(num(left) + num(right))
            "-" -> number(num(left) - num(right))
            "*" -> number(num(left) * num(right))
            "/" -> {
                val top = num(left)
                val bottom = num(right)
                if (bottom == 0.0) throw Fail(SheetError.DIV_ZERO)
                number(top / bottom)
            }
            "^" -> {
                val base = num(left)
                val exponent = num(right)
                if (base == 0.0 && exponent < 0) throw Fail(SheetError.DIV_ZERO)
                number(base.pow(exponent))
            }
            "&" -> CellValue.Text(text(left) + text(right))
            else -> {
                val order = compare(left, right)
                CellValue.Bool(
                    when (expr.op) {
                        "=" -> order == 0
                        "<>" -> order != 0
                        "<" -> order < 0
                        ">" -> order > 0
                        "<=" -> order <= 0
                        else -> order >= 0
                    }
                )
            }
        }
    }

    // numbers sort before text and text before TRUE and FALSE, text ignores case
    private fun compare(a: CellValue, b: CellValue): Int {
        val left = if (a == CellValue.Empty) blankLike(b) else a
        val right = if (b == CellValue.Empty) blankLike(a) else b
        if (left is CellValue.Number && right is CellValue.Number) return compareNumbers(left.value, right.value)
        if (left is CellValue.Number && right is CellValue.Text) {
            return parseCellNumber(right.value)?.let { compareNumbers(left.value, it) } ?: -1
        }
        if (left is CellValue.Text && right is CellValue.Number) {
            return parseCellNumber(left.value)?.let { compareNumbers(it, right.value) } ?: 1
        }
        if (left is CellValue.Text && right is CellValue.Text) return left.value.compareTo(right.value, ignoreCase = true)
        if (left is CellValue.Bool && right is CellValue.Bool) return left.value.compareTo(right.value)
        return rank(left).compareTo(rank(right))
    }

    private fun blankLike(other: CellValue): CellValue = when (other) {
        is CellValue.Text -> CellValue.Text("")
        is CellValue.Bool -> CellValue.Bool(false)
        else -> CellValue.Number(0.0)
    }

    private fun rank(value: CellValue): Int = when (value) {
        is CellValue.Number -> 0
        is CellValue.Text -> 1
        else -> 2
    }

    // close enough counts as equal, so 0.1 + 0.2 = 0.3 is TRUE
    private fun compareNumbers(a: Double, b: Double): Int =
        if (abs(a - b) <= 1e-12 * max(1.0, max(abs(a), abs(b)))) 0 else a.compareTo(b)

    private fun num(value: CellValue): Double = when (value) {
        CellValue.Empty -> 0.0
        is CellValue.Number -> value.value
        is CellValue.Bool -> if (value.value) 1.0 else 0.0
        is CellValue.Text -> parseCellNumber(value.value) ?: throw Fail(SheetError.VALUE)
        is CellValue.Error -> throw Fail(value.error)
    }

    private fun text(value: CellValue): String = when (value) {
        CellValue.Empty -> ""
        is CellValue.Number -> plainNumber(value.value)
        is CellValue.Bool -> if (value.value) "TRUE" else "FALSE"
        is CellValue.Text -> value.value
        is CellValue.Error -> throw Fail(value.error)
    }

    private fun bool(value: CellValue): Boolean = when (value) {
        CellValue.Empty -> false
        is CellValue.Number -> value.value != 0.0
        is CellValue.Bool -> value.value
        is CellValue.Text -> when {
            value.value.equals("TRUE", ignoreCase = true) -> true
            value.value.equals("FALSE", ignoreCase = true) -> false
            else -> parseCellNumber(value.value)?.let { it != 0.0 } ?: throw Fail(SheetError.VALUE)
        }
        is CellValue.Error -> throw Fail(value.error)
    }

    // every argument with ranges spread out, inRange lets functions skip stray text quietly.
    // errors come through as values so COUNT can ignore them and COUNTA can count them
    private inline fun eachValue(args: List<Expr>, action: (value: CellValue, inRange: Boolean) -> Unit) {
        for (arg in args) {
            when (arg) {
                is Expr.Range -> for (r in arg.top..min(arg.bottom, rows - 1)) {
                    for (c in arg.left..min(arg.right, columns - 1)) action(cell(r, c), true)
                }
                is Expr.Ref -> action(cell(arg.row, arg.col), false)
                else -> action(
                    try {
                        eval(arg)
                    } catch (fail: Fail) {
                        CellValue.Error(fail.error)
                    },
                    false,
                )
            }
        }
    }

    // empty cells are skipped, so AVERAGE(A1:A5) only averages the filled ones
    private fun numbers(args: List<Expr>): List<Double> {
        val found = ArrayList<Double>()
        eachValue(args) { value, inRange ->
            when (value) {
                CellValue.Empty -> Unit
                is CellValue.Number -> found += value.value
                is CellValue.Error -> throw Fail(value.error)
                is CellValue.Text -> {
                    val parsed = parseCellNumber(value.value)
                    if (parsed != null) found += parsed else if (!inRange) throw Fail(SheetError.VALUE)
                }
                is CellValue.Bool -> if (!inRange) found += if (value.value) 1.0 else 0.0
            }
        }
        return found
    }

    private fun booleans(args: List<Expr>): List<Boolean> {
        val found = ArrayList<Boolean>()
        eachValue(args) { value, inRange ->
            when (value) {
                CellValue.Empty -> Unit
                is CellValue.Text -> if (!inRange) found += bool(value)
                else -> found += bool(value)
            }
        }
        if (found.isEmpty()) throw Fail(SheetError.VALUE)
        return found
    }

    private fun call(expr: Expr.Call): CellValue {
        val args = expr.args

        fun arity(range: IntRange) {
            if (args.size !in range) throw Fail(SheetError.VALUE)
        }

        fun single(): CellValue {
            arity(1..1)
            return eval(args[0])
        }

        return when (expr.name) {
            "SUM" -> number(numbers(args).sum())
            "AVERAGE" -> {
                val found = numbers(args)
                if (found.isEmpty()) throw Fail(SheetError.DIV_ZERO)
                number(found.sum() / found.size)
            }
            "MIN" -> number(numbers(args).minOrNull() ?: 0.0)
            "MAX" -> number(numbers(args).maxOrNull() ?: 0.0)
            "COUNT" -> {
                var count = 0
                eachValue(args) { value, _ ->
                    if (value is CellValue.Number || (value is CellValue.Text && parseCellNumber(value.value) != null)) count++
                }
                CellValue.Number(count.toDouble())
            }
            "COUNTA" -> {
                var count = 0
                eachValue(args) { value, _ -> if (value != CellValue.Empty) count++ }
                CellValue.Number(count.toDouble())
            }
            "ROUND" -> {
                arity(1..2)
                val value = num(eval(args[0]))
                val digits = if (args.size == 2) num(eval(args[1])).toInt().coerceIn(-15, 15) else 0
                // halves round away from zero, so 2.5 becomes 3 and -2.5 becomes -3
                number(BigDecimal.valueOf(value).setScale(digits, RoundingMode.HALF_UP).toDouble())
            }
            "ABS" -> number(abs(num(single())))
            "SQRT" -> {
                val value = num(single())
                if (value < 0) throw Fail(SheetError.NUM)
                number(sqrt(value))
            }
            "IF" -> {
                arity(2..3)
                // only the branch that is picked gets worked out
                when {
                    bool(eval(args[0])) -> eval(args[1])
                    args.size == 3 -> eval(args[2])
                    else -> CellValue.Bool(false)
                }
            }
            "AND" -> CellValue.Bool(booleans(args).all { it })
            "OR" -> CellValue.Bool(booleans(args).any { it })
            "NOT" -> CellValue.Bool(!bool(single()))
            "CONCAT", "CONCATENATE" -> {
                val joined = StringBuilder()
                eachValue(args) { value, _ -> joined.append(text(value)) }
                CellValue.Text(joined.toString())
            }
            "LEN" -> CellValue.Number(text(single()).length.toDouble())
            "UPPER" -> CellValue.Text(text(single()).uppercase())
            "LOWER" -> CellValue.Text(text(single()).lowercase())
            "TODAY" -> {
                arity(0..0)
                CellValue.Text(today.toString())
            }
            else -> throw Fail(SheetError.NAME)
        }
    }
}
