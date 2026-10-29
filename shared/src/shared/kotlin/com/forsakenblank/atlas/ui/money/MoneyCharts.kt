package com.forsakenblank.atlas.ui.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.MoneyAccount
import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.util.MonthTotal
import com.forsakenblank.atlas.util.balanceSeries
import com.forsakenblank.atlas.util.categoryTotals
import com.forsakenblank.atlas.util.currentBalance
import com.forsakenblank.atlas.util.monthlyTotals
import com.forsakenblank.atlas.util.pounds
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// how far back the charts look, months of 0 means everything
private enum class Range(val label: String, val months: Int) {
    THREE("3 months", 3),
    SIX("6 months", 6),
    YEAR("1 year", 12),
    ALL("All time", 0),
}

@Composable
fun MoneyCharts(accounts: List<MoneyAccount>, entries: List<MoneyEntry>) {
    var range by rememberSaveable { mutableStateOf(Range.SIX) }
    val today = LocalDate.now()
    val earliest = entries.minOfOrNull { it.day } ?: today.toEpochDay()

    // all time still shows at least 3 months and at most 5 years of bars
    val months = if (range.months > 0) range.months else {
        val first = LocalDate.ofEpochDay(earliest)
        val span = (today.year - first.year) * 12 + today.monthValue - first.monthValue + 1
        span.coerceIn(3, 60)
    }
    val fromDay = if (range.months > 0) today.minusMonths(range.months.toLong() - 1).withDayOfMonth(1).toEpochDay() else earliest

    val totals = remember(entries, months) { monthlyTotals(entries, months, today) }
    // past records leave the balance alone, so today's number is the anchor and the line is worked out backwards from it
    val current = currentBalance(accounts.sumOf { it.openingPence }, entries)
    val series = remember(entries, current, fromDay) { balanceSeries(current, entries, fromDay, today.toEpochDay()) }
    val categories = remember(entries, fromDay) { categoryTotals(entries, fromDay) }
    val earnings = remember(entries, fromDay) { categoryTotals(entries, fromDay, earning = true) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Range.entries.forEach { r ->
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) })
            }
        }

        ChartCard("Earned and spent") {
            if (totals.all { it.earned == 0L && it.spent == 0L }) {
                Text("Nothing logged in this time yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                BarChart(totals)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(Earned, "Earned")
                    Legend(Spent, "Spent")
                }
            }
        }

        ChartCard("Total balance") {
            if (series.size < 2) {
                Text("Add some entries to see this build up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LineChart(series.map { it.second })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(LocalDate.ofEpochDay(series.first().first).let { "${it.dayOfMonth} ${it.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${it.year}" }, style = MaterialTheme.typography.labelSmall)
                    Text(pounds(series.last().second), style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        CategoryCard("Spending by category", categories, Spent, "No spending in this time.")
        CategoryCard("Earnings by category", earnings, Earned, "No earnings in this time.")
        Box(Modifier.height(72.dp))
    }
}

@Composable
private fun CategoryCard(title: String, rows: List<Pair<String, Long>>, color: androidx.compose.ui.graphics.Color, empty: String) {
    ChartCard(title) {
        if (rows.isEmpty()) {
            Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val biggest = rows.first().second.toFloat()
            val sum = rows.sumOf { it.second }
            Text("Total ${pounds(sum)}", style = MaterialTheme.typography.labelLarge)
            rows.take(8).forEach { (name, pence) ->
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${pounds(pence)} · ${(pence * 100 / sum)}%", style = MaterialTheme.typography.labelMedium)
                    }
                    Box(
                        Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Box(Modifier.fillMaxWidth(pence / biggest).fillMaxHeight().background(color))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) {
    AtlasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Box(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun BarChart(totals: List<MonthTotal>) {
    val top = totals.maxOf { maxOf(it.earned, it.spent) }.coerceAtLeast(1L).toFloat()
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            totals.forEach { m ->
                Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                    Box(Modifier.weight(1f).fillMaxHeight((m.earned / top).coerceAtLeast(0.01f)).background(Earned, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                    Box(Modifier.weight(1f).fillMaxHeight((m.spent / top).coerceAtLeast(0.01f)).background(Spent, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            totals.forEach { m ->
                Text(
                    m.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(if (totals.size > 8) 1 else 3),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = label,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun LineChart(values: List<Long>) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val low = minOf(values.min(), 0L)
    val high = maxOf(values.max(), 0L).let { if (it == low) low + 1 else it }
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        fun y(v: Long) = size.height - (v - low).toFloat() / (high - low) * size.height
        // a faint line at zero so a dip below it is easy to see
        drawLine(grid, Offset(0f, y(0)), Offset(size.width, y(0)), strokeWidth = 1.dp.toPx())
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = if (values.size == 1) 0f else i.toFloat() / (values.size - 1) * size.width
            if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v))
        }
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
