package com.forsakenblank.atlas.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.forsakenblank.atlas.util.MdBlock
import com.forsakenblank.atlas.util.parseMarkdown

private val inlinePattern = Regex(
    """\*\*(.+?)\*\*|__(.+?)__|~~(.+?)~~|==(.+?)==|<u>(.+?)</u>|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)|""" +
        """(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*|(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])""",
)

private fun AnnotatedString.Builder.appendInline(text: String, colors: ColorScheme) {
    var at = 0
    for (match in inlinePattern.findAll(text)) {
        append(text.substring(at, match.range.first))
        val g = match.groupValues
        when {
            g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(g[1], colors) }
            g[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(g[2], colors) }
            g[3].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(g[3], colors) }
            g[4].isNotEmpty() -> withStyle(SpanStyle(background = colors.tertiaryContainer, color = colors.onTertiaryContainer)) { appendInline(g[4], colors) }
            g[5].isNotEmpty() -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { appendInline(g[5], colors) }
            g[6].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = colors.surfaceContainerHighest)) { append(g[6]) }
            g[7].isNotEmpty() -> withLink(LinkAnnotation.Url(g[8])) {
                withStyle(SpanStyle(color = colors.primary, textDecoration = TextDecoration.Underline)) { append(g[7]) }
            }
            g[9].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(g[9], colors) }
            g[10].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(g[10], colors) }
        }
        at = match.range.last + 1
    }
    append(text.substring(at))
}

private fun inline(text: String, colors: ColorScheme): AnnotatedString = buildAnnotatedString { appendInline(text, colors) }

// the finished look of a markdown note
@Composable
fun MarkdownView(text: String, style: TextStyle, onToggleTask: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parseMarkdown(text).forEach { block ->
            when (block) {
                is MdBlock.Heading -> Text(
                    inline(block.text, colors),
                    style = style.copy(
                        fontSize = style.fontSize * (when (block.level) { 1 -> 1.7f; 2 -> 1.4f; 3 -> 1.2f; else -> 1.05f }),
                        lineHeight = 1.25.em,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    modifier = Modifier.padding(top = 6.dp),
                )
                is MdBlock.Paragraph -> Text(inline(block.text, colors), style = style)
                is MdBlock.ListItem -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
                    Text(block.marker, style = style, color = colors.primary, modifier = Modifier.width(24.dp))
                    Text(inline(block.text, colors), style = style, modifier = Modifier.weight(1f))
                }
                is MdBlock.TaskItem -> Row(Modifier.padding(start = (block.indent * 16).dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = block.checked, onCheckedChange = { onToggleTask(block.line) }, modifier = Modifier.height(28.dp).width(36.dp))
                    Text(
                        inline(block.text, colors),
                        style = if (block.checked) style.copy(textDecoration = TextDecoration.LineThrough, color = colors.onSurfaceVariant) else style,
                        modifier = Modifier.weight(1f),
                    )
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(colors.primary))
                    Text(inline(block.text, colors), style = style.copy(fontStyle = FontStyle.Italic), color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                }
                is MdBlock.Code -> Text(
                    block.text,
                    style = style.copy(fontFamily = FontFamily.Monospace, fontSize = style.fontSize * 0.9f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceContainerHighest, RoundedCornerShape(8.dp))
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp),
                )
                MdBlock.Rule -> HorizontalDivider(Modifier.padding(vertical = 6.dp))
                is MdBlock.Table -> Column(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .border(1.dp, colors.outlineVariant, RoundedCornerShape(6.dp)),
                ) {
                    val widths = block.header.indices
                    Row(Modifier.background(colors.surfaceContainerHighest)) {
                        widths.forEach { c -> Text(inline(block.header[c], colors), style = style.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.width(130.dp).padding(8.dp)) }
                    }
                    block.rows.forEach { row ->
                        HorizontalDivider(color = colors.outlineVariant)
                        Row { widths.forEach { c -> Text(inline(row.getOrElse(c) { "" }, colors), style = style, modifier = Modifier.width(130.dp).padding(8.dp)) } }
                    }
                }
            }
        }
    }
}

// styles the text while it is being typed, the symbols stay so the cursor never jumps, they just fade back
class MarkdownEditing(private val colors: ColorScheme) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val faded = SpanStyle(color = colors.outline)
        val styled = buildAnnotatedString {
            append(raw)
            var lineStart = 0
            raw.split("\n").forEach { line ->
                val hashes = line.takeWhile { it == '#' }.length
                if (hashes in 1..6 && line.getOrNull(hashes) == ' ') {
                    val size = when (hashes) { 1 -> 1.5f; 2 -> 1.3f; 3 -> 1.15f; else -> 1.05f }
                    addStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = size.em), lineStart, lineStart + line.length)
                    addStyle(faded, lineStart, lineStart + hashes)
                }
                if (line.startsWith("> ")) addStyle(SpanStyle(fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant), lineStart, lineStart + line.length)
                lineStart += line.length + 1
            }
            fun mark(pattern: Regex, inner: SpanStyle, width: Int) {
                pattern.findAll(raw).forEach { m ->
                    addStyle(inner, m.range.first, m.range.last + 1)
                    addStyle(faded, m.range.first, m.range.first + width)
                    addStyle(faded, m.range.last + 1 - width, m.range.last + 1)
                }
            }
            mark(Regex("""\*\*[^\n]+?\*\*"""), SpanStyle(fontWeight = FontWeight.Bold), 2)
            mark(Regex("""~~[^\n]+?~~"""), SpanStyle(textDecoration = TextDecoration.LineThrough), 2)
            mark(Regex("""==[^\n]+?=="""), SpanStyle(background = colors.tertiaryContainer), 2)
            mark(Regex("""`[^`\n]+`"""), SpanStyle(fontFamily = FontFamily.Monospace), 1)
            mark(Regex("""(?<![\w*])\*(?![\s*])[^\n*]+?\*"""), SpanStyle(fontStyle = FontStyle.Italic), 1)
            mark(Regex("""<u>[^\n]+?</u>"""), SpanStyle(textDecoration = TextDecoration.Underline), 3)
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is MarkdownEditing && other.colors == colors

    override fun hashCode() = colors.hashCode()
}
