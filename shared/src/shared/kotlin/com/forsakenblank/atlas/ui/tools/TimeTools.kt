@file:OptIn(ExperimentalMaterial3Api::class)

package com.forsakenblank.atlas.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.forsakenblank.atlas.ui.common.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.util.formatStopwatch
import com.forsakenblank.atlas.util.parseWholeInput
import com.forsakenblank.atlas.util.workingDaysBetween
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.forsakenblank.atlas.platform.platform

private enum class DateSlot { START, END, BASE, LOOKUP }

private enum class DateStep(val label: String) { DAYS("Days"), WEEKS("Weeks"), MONTHS("Months") }

private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

@Composable
internal fun DatesTool() {
    val today = remember { LocalDate.now() }
    var start by rememberSaveable { mutableStateOf(today) }
    var end by rememberSaveable { mutableStateOf(today.plusDays(30)) }
    var includeEnd by rememberSaveable { mutableStateOf(false) }
    var base by rememberSaveable { mutableStateOf(today) }
    var amountText by rememberSaveable { mutableStateOf("14") }
    var step by rememberSaveable { mutableStateOf(DateStep.DAYS) }
    var subtract by rememberSaveable { mutableStateOf(false) }
    var lookup by rememberSaveable { mutableStateOf(today) }
    var picking by remember { mutableStateOf<DateSlot?>(null) }

    DateCard("Days between two dates") {
        DateButton("From", start) { picking = DateSlot.START }
        DateButton("To", end) { picking = DateSlot.END }
        SwitchRow("Count the end date too", includeEnd) { includeEnd = it }
        val backwards = start.isAfter(end)
        val first = if (backwards) end else start
        val last = (if (backwards) start else end).plusDays(if (includeEnd) 1L else 0L)
        val days = ChronoUnit.DAYS.between(first, last)
        ResultRow("Days", "%,d".format(days), emphasis = true)
        if (days >= 7) ResultRow("In weeks", weeksAndDays(days))
        ResultRow("Working days, Monday to Friday", "%,d".format(workingDaysBetween(first, last)))
        if (backwards) Hint("The end date is before the start date, so this counts the gap between them.")
    }

    DateCard("Add or take away time") {
        DateButton("Starting", base) { picking = DateSlot.BASE }
        ChoiceRow(
            options = listOf(false, true),
            selected = subtract,
            label = { if (it) "Take away" else "Add" },
            onSelect = { subtract = it },
        )
        NumberField(amountText, { amountText = it }, "How many", Modifier.fillMaxWidth(), wholeNumber = true)
        ChoiceRow(
            options = DateStep.entries,
            selected = step,
            label = { it.label },
            onSelect = { step = it },
        )
        val amount = parseWholeInput(amountText)?.let { if (subtract) -it else it }
        // runCatching because a silly amount can run past the last year java.time knows about
        val result = amount?.let {
            runCatching {
                when (step) {
                    DateStep.DAYS -> base.plusDays(it)
                    DateStep.WEEKS -> base.plusWeeks(it)
                    DateStep.MONTHS -> base.plusMonths(it)
                }
            }.getOrNull()
        }
        if (result != null) {
            Text(result.format(longDate), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Hint(fromToday(result, today))
        } else if (amountText.isNotBlank()) {
            Text("-", style = MaterialTheme.typography.titleLarge)
        }
    }

    DateCard("Day of the week") {
        DateButton("Date", lookup) { picking = DateSlot.LOOKUP }
        Text(
            lookup.format(DateTimeFormatter.ofPattern("EEEE")),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Hint(fromToday(lookup, today))
    }

    picking?.let { slot ->
        val initial = when (slot) {
            DateSlot.START -> start
            DateSlot.END -> end
            DateSlot.BASE -> base
            DateSlot.LOOKUP -> lookup
        }
        DateDialog(initial = initial, onDismiss = { picking = null }) { picked ->
            when (slot) {
                DateSlot.START -> start = picked
                DateSlot.END -> end = picked
                DateSlot.BASE -> base = picked
                DateSlot.LOOKUP -> lookup = picked
            }
            picking = null
        }
    }
}

@Composable
private fun DateCard(title: String, content: @Composable () -> Unit) {
    AtlasCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun DateButton(label: String, date: LocalDate, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Event, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label  ${date.format(shortDate)}")
    }
}

// the date picker works in utc millis, so dates go in and out at utc midnight
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

private fun weeksAndDays(days: Long): String {
    val weeks = days / 7
    val rest = days % 7
    val weekText = if (weeks == 1L) "1 week" else "%,d weeks".format(weeks)
    return when (rest) {
        0L -> weekText
        1L -> "$weekText and 1 day"
        else -> "$weekText and $rest days"
    }
}

private fun fromToday(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date)
    return when {
        days == 0L -> "That's today."
        days == 1L -> "That's tomorrow."
        days == -1L -> "That was yesterday."
        days > 0 -> "That's in %,d days.".format(days)
        else -> "That was %,d days ago.".format(abs(days))
    }
}

@Composable
internal fun CounterTool() {
    val buzz = rememberHaptic()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var count by rememberSaveable { mutableIntStateOf(0) }

    Text(
        "%,d".format(count),
        style = MaterialTheme.typography.displayLarge.copy(fontSize = 96.sp, lineHeight = 104.sp, fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    )
    Button(
        onClick = {
            count++
            buzz()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
    ) {
        Icon(Icons.Outlined.Add, contentDescription = "Add one", modifier = Modifier.size(64.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = {
                if (count > 0) count--
                buzz()
            },
            enabled = count > 0,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "Take one off", modifier = Modifier.size(32.dp))
        }
        OutlinedButton(
            onClick = {
                val before = count
                count = 0
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar("Counter reset", actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) count = before
                }
            },
            enabled = count != 0,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reset")
        }
    }
}

@Composable
internal fun StopwatchTool() {
    val buzz = rememberHaptic()
    val clock = platform()
    var running by rememberSaveable { mutableStateOf(false) }
    // time from earlier runs, plus when the current run started
    var banked by rememberSaveable { mutableLongStateOf(0L) }
    var startedAt by rememberSaveable { mutableLongStateOf(0L) }
    var laps by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var now by remember { mutableLongStateOf(clock.elapsedMillis()) }

    LaunchedEffect(running) {
        while (running) {
            now = clock.elapsedMillis()
            delay(30)
        }
    }
    val elapsed = if (running) banked + (now - startedAt).coerceAtLeast(0) else banked

    fun start() {
        startedAt = clock.elapsedMillis()
        now = startedAt
        running = true
        buzz()
    }

    fun pause() {
        banked += clock.elapsedMillis() - startedAt
        running = false
        buzz()
    }

    fun lap() {
        laps = laps + (banked + clock.elapsedMillis() - startedAt)
        buzz()
    }

    fun reset() {
        running = false
        banked = 0L
        laps = emptyList()
    }

    Text(
        formatStopwatch(elapsed),
        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            running -> {
                OutlinedButton(onClick = { lap() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Outlined.Flag, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Lap")
                }
                Button(onClick = { pause() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Outlined.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Pause")
                }
            }
            elapsed > 0 -> {
                OutlinedButton(onClick = { reset() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Reset")
                }
                Button(onClick = { start() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Resume")
                }
            }
            else -> {
                Button(onClick = { start() }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start")
                }
            }
        }
    }

    if (laps.isNotEmpty()) {
        val splits = laps.mapIndexed { index, total -> total - (if (index == 0) 0L else laps[index - 1]) }
        // with three or more laps the quickest and slowest stand out
        val quickest = if (laps.size >= 3) splits.min() else null
        val slowest = if (laps.size >= 3) splits.max() else null
        ResultCard(title = "Laps") {
            LapRow("Lap", "Split", "Total", Color.Unspecified, header = true)
            laps.indices.reversed().forEach { index ->
                val split = splits[index]
                val colour = when (split) {
                    quickest -> MaterialTheme.colorScheme.primary
                    slowest -> MaterialTheme.colorScheme.error
                    else -> Color.Unspecified
                }
                LapRow("${index + 1}", formatStopwatch(split), formatStopwatch(laps[index]), colour)
            }
        }
    }
}

@Composable
private fun LapRow(lap: String, split: String, total: String, colour: Color, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum")
    val textColour = if (header) MaterialTheme.colorScheme.onSurfaceVariant else colour
    Row(Modifier.fillMaxWidth()) {
        Text(lap, style = style, color = textColour, modifier = Modifier.weight(0.6f))
        Text(split, style = style, color = textColour, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(total, style = style, color = textColour, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}
