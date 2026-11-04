package com.forsakenblank.atlas.util

// a small markdown reader and the edits the note toolbar makes, no compose in here so it can be tested
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ListItem(val marker: String, val text: String, val indent: Int) : MdBlock
    data class TaskItem(val text: String, val checked: Boolean, val indent: Int, val line: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Rule : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
}

private val headingLine = Regex("""^(#{1,6})\s+(.*)$""")
private val taskLine = Regex("""^(\s*)[-*+]\s+\[([ xX])]\s*(.*)$""")
private val bulletLine = Regex("""^(\s*)[-*+]\s+(.*)$""")
private val numberLine = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
private val quoteLine = Regex("""^>\s?(.*)$""")
private val ruleLine = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
private val tableRule = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)+\|?\s*$""")

private fun tableCells(line: String): List<String> =
    line.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

fun parseMarkdown(text: String): List<MdBlock> {
    val lines = text.split("\n")
    val out = mutableListOf<MdBlock>()
    val paragraph = mutableListOf<String>()
    fun flush() {
        if (paragraph.isNotEmpty()) out += MdBlock.Paragraph(paragraph.joinToString("\n"))
        paragraph.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val code = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) code += lines[i++]
                out += MdBlock.Code(code.joinToString("\n"))
            }
            line.isBlank() -> flush()
            ruleLine.matches(line) -> {
                flush()
                out += MdBlock.Rule
            }
            line.contains('|') && i + 1 < lines.size && tableRule.matches(lines[i + 1]) -> {
                flush()
                val header = tableCells(line)
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains('|')) rows += tableCells(lines[i++])
                i--
                out += MdBlock.Table(header, rows)
            }
            else -> {
                val heading = headingLine.matchEntire(line)
                val task = taskLine.matchEntire(line)
                val bullet = bulletLine.matchEntire(line)
                val number = numberLine.matchEntire(line)
                val quote = quoteLine.matchEntire(line)
                if (heading == null && task == null && bullet == null && number == null && quote == null) {
                    paragraph += line
                } else {
                    flush()
                    out += when {
                        heading != null -> MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2])
                        task != null -> MdBlock.TaskItem(task.groupValues[3], task.groupValues[2] != " ", task.groupValues[1].length / 2, i)
                        bullet != null -> MdBlock.ListItem("•", bullet.groupValues[2], bullet.groupValues[1].length / 2)
                        number != null -> MdBlock.ListItem("${number.groupValues[2]}.", number.groupValues[3], number.groupValues[1].length / 2)
                        else -> MdBlock.Quote(quote!!.groupValues[1])
                    }
                }
            }
        }
        i++
    }
    flush()
    return out
}

// the text after an edit and where the selection ends up
data class MdEdit(val text: String, val start: Int, val end: Int)

// wraps the selection in a marker, or takes the marker off when it is already there
fun wrapSelection(text: String, start: Int, end: Int, open: String, close: String = open): MdEdit {
    val inside = text.substring(start, end)
    if (start >= open.length && end + close.length <= text.length &&
        text.substring(start - open.length, start) == open && text.substring(end, end + close.length) == close
    ) {
        return MdEdit(text.removeRange(end, end + close.length).removeRange(start - open.length, start), start - open.length, end - open.length)
    }
    return MdEdit(text.substring(0, start) + open + inside + close + text.substring(end), start + open.length, end + open.length)
}

private fun lineRange(text: String, start: Int, end: Int): Pair<Int, Int> {
    val from = if (start == 0) 0 else text.lastIndexOf('\n', start - 1) + 1
    val to = text.indexOf('\n', end).let { if (it < 0) text.length else it }
    return from to to
}

private fun replaceLines(text: String, start: Int, end: Int, change: (List<String>) -> List<String>): MdEdit {
    val (from, to) = lineRange(text, start, end)
    val changed = change(text.substring(from, to).split("\n")).joinToString("\n")
    val after = from + changed.length
    return MdEdit(text.substring(0, from) + changed + text.substring(to), if (start == end) after else from, after)
}

// bullets, quotes and the like: on for every chosen line, or off when they all have it
fun toggleLinePrefix(text: String, start: Int, end: Int, prefix: String): MdEdit = replaceLines(text, start, end) { lines ->
    if (lines.all { it.startsWith(prefix) }) lines.map { it.removePrefix(prefix) } else lines.map { prefix + it }
}

fun numberLines(text: String, start: Int, end: Int): MdEdit = replaceLines(text, start, end) { lines ->
    if (lines.all { numberLine.matches(it) }) {
        lines.map { it.replace(Regex("""^\s*\d+[.)]\s+"""), "") }
    } else {
        lines.mapIndexed { i, line -> "${i + 1}. $line" }
    }
}

// level 0 makes it normal text again, picking the level a line already has does the same
fun setHeading(text: String, start: Int, end: Int, level: Int): MdEdit = replaceLines(text, start, end) { lines ->
    lines.map { line ->
        val current = headingLine.matchEntire(line)
        val plain = current?.groupValues?.get(2) ?: line
        if (level == 0 || current?.groupValues?.get(1)?.length == level) plain else "#".repeat(level) + " " + plain
    }
}

fun insertBlock(text: String, at: Int, block: String): MdEdit {
    val before = if (at == 0 || text[at - 1] == '\n') "" else "\n"
    val after = if (at == text.length || text[at] == '\n') "" else "\n"
    val added = before + block + after
    return MdEdit(text.substring(0, at) + added + text.substring(at), at + added.length, at + added.length)
}

fun toggleTaskLine(text: String, line: Int): String {
    val lines = text.split("\n").toMutableList()
    val current = lines.getOrNull(line) ?: return text
    val match = taskLine.matchEntire(current) ?: return text
    val box = match.groups[2]!!.range
    lines[line] = current.replaceRange(box, if (match.groupValues[2] == " ") "x" else " ")
    return lines.joinToString("\n")
}

// after enter in a list: the next line starts the same way, enter on an empty item ends the list
fun continueList(old: String, new: String, cursor: Int): MdEdit? {
    if (new.length != old.length + 1 || cursor == 0 || new[cursor - 1] != '\n') return null
    val lineStart = if (cursor < 2) 0 else new.lastIndexOf('\n', cursor - 2) + 1
    val line = new.substring(lineStart, cursor - 1)
    val task = taskLine.matchEntire(line)
    val bullet = bulletLine.matchEntire(line)
    val number = numberLine.matchEntire(line)
    val (marker, content) = when {
        task != null -> "${task.groupValues[1]}- [ ] " to task.groupValues[3]
        bullet != null -> "${bullet.groupValues[1]}${line.trimStart().first()} " to bullet.groupValues[2]
        number != null -> "${number.groupValues[1]}${number.groupValues[2].toInt() + 1}. " to number.groupValues[3]
        else -> return null
    }
    if (content.isBlank()) return MdEdit(new.removeRange(lineStart, cursor), lineStart, lineStart)
    return MdEdit(new.substring(0, cursor) + marker + new.substring(cursor), cursor + marker.length, cursor + marker.length)
}

private val inlineMarks = listOf(
    Regex("""\*\*(.+?)\*\*"""), Regex("""__(.+?)__"""), Regex("""~~(.+?)~~"""), Regex("""==(.+?)=="""),
    Regex("""<u>(.+?)</u>"""), Regex("""`([^`]+)`"""), Regex("""\[([^\]]+)]\([^)]+\)"""),
    Regex("""(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*"""), Regex("""(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])"""),
)

// turning a markdown note into plain text, the words stay and the symbols go
fun markdownToPlain(text: String): String {
    val out = mutableListOf<String>()
    var inCode = false
    for (line in text.split("\n")) {
        if (line.trimStart().startsWith("```")) {
            inCode = !inCode
            continue
        }
        if (inCode) {
            out += line
            continue
        }
        if (ruleLine.matches(line) || tableRule.matches(line)) continue
        var plain = line
        headingLine.matchEntire(plain)?.let { plain = it.groupValues[2] }
        taskLine.matchEntire(plain)?.let { plain = it.groupValues[1] + (if (it.groupValues[2] == " ") "[ ] " else "[x] ") + it.groupValues[3] }
        quoteLine.matchEntire(plain)?.let { plain = it.groupValues[1] }
        bulletLine.matchEntire(plain)?.let { plain = it.groupValues[1] + "- " + it.groupValues[2] }
        if (plain.contains('|') && plain.trim().startsWith("|")) plain = tableCells(plain).joinToString("   ")
        inlineMarks.forEach { mark -> plain = mark.replace(plain) { it.groupValues[1] } }
        out += plain
    }
    return out.joinToString("\n")
}
