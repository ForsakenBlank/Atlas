package com.forsakenblank.atlas.ui.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.DayPickerDialog
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// what tapping a tracker does, shared by home and the track tab
@Composable
fun rememberTrackerTap(): (TrackerSummary) -> Unit {
    val repo = atlasApp().repository
    val settings = LocalSettings.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf<TrackerSummary?>(null) }

    asking?.let { summary ->
        LogValueDialog(
            summary = summary,
            onDismiss = { asking = null },
            onLog = { value, note ->
                asking = null
                val hit = summary.tapHitsGoal(value)
                scope.launch {
                    val id = repo.logValue(summary.id, value, note)
                    announce(snackbar, settings, repo, summary, hit, id)
                }
            },
        )
    }

    return { summary ->
        when (summary.kind) {
            TrackerKind.NUMBER, TrackerKind.RATING -> asking = summary
            else -> {
                val hit = summary.tapHitsGoal()
                scope.launch {
                    val id = repo.tap(summary.id)
                    if (id != null) announce(snackbar, settings, repo, summary, hit, id)
                }
            }
        }
    }
}

private suspend fun announce(
    snackbar: SnackbarHostState,
    settings: AppSettings,
    repo: AtlasRepository,
    summary: TrackerSummary,
    hitGoal: Boolean,
    logId: Long,
) {
    val message = if (hitGoal && settings.celebrateGoals) {
        "Goal hit for ${summary.item.name}, nice one"
    } else {
        summary.tapMessage() ?: return
    }
    snackbar.currentSnackbarData?.dismiss()
    if (settings.showUndo) {
        val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) repo.deleteLog(logId)
    } else {
        snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
    }
}

@Composable
fun LogValueDialog(summary: TrackerSummary, onDismiss: () -> Unit, onLog: (Double, String?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(0) }
    val rating = summary.kind == TrackerKind.RATING
    val value = if (rating) stars.takeIf { it > 0 }?.toDouble() else text.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(summary.item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (rating) {
                    Text("How was today?", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { n ->
                            IconButton(onClick = { stars = n }) {
                                Icon(
                                    if (n <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "$n stars",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(12) },
                        label = { Text(summary.tracker.unit?.let { "Amount in $it" } ?: "Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    summary.todayValue?.let {
                        Text(
                            "Today so far: ${formatNumber(it)}${summary.tracker.unit?.let { u -> " $u" }.orEmpty()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = { value?.let { onLog(it, note.trim().ifEmpty { null }) } }) { Text("Log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// logs something that happened on an earlier day, what it asks for depends on the kind of tracker
@Composable
fun PastLogDialog(
    kind: TrackerKind,
    name: String,
    unit: String?,
    onDismiss: () -> Unit,
    onSave: (day: LocalDate, count: Int, value: Double, seconds: Long?, note: String?) -> Unit,
) {
    val today = LocalDate.now()
    var day by remember { mutableStateOf(today.minusDays(1)) }
    var picking by remember { mutableStateOf(false) }
    var count by remember { mutableStateOf("1") }
    var amount by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    var stars by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf("") }

    val value = when (kind) {
        TrackerKind.RATING -> stars.takeIf { it > 0 }?.toDouble()
        TrackerKind.NUMBER -> amount.replace(',', '.').toDoubleOrNull()
        else -> 1.0
    }
    val seconds = minutes.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { (it * 60).toLong() }
    val countValue = count.toIntOrNull()?.takeIf { it in 1..100 }
    val ready = when (kind) {
        TrackerKind.TIMER -> seconds != null
        TrackerKind.COUNTER -> countValue != null
        else -> value != null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log in the past") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(day.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy")))
                }
                when (kind) {
                    TrackerKind.COUNTER -> OutlinedTextField(
                        value = count,
                        onValueChange = { count = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("How many times") },
                        singleLine = true,
                        isError = countValue == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TrackerKind.NUMBER -> OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.take(12) },
                        label = { Text(unit?.let { "Amount in $it" } ?: "Amount") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TrackerKind.TIMER -> OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.take(8) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TrackerKind.RATING -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..5).forEach { n ->
                            IconButton(onClick = { stars = n }) {
                                Icon(
                                    if (n <= stars) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = "$n stars",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                    TrackerKind.YES_NO -> Text("Marks this day as done.", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = { onSave(day, countValue ?: 1, value ?: 1.0, seconds, note.trim().ifEmpty { null }) },
            ) { Text("Log") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        DayPickerDialog(day.toEpochDay(), onDismiss = { picking = false }, maxDay = today.toEpochDay()) {
            day = LocalDate.ofEpochDay(it)
            picking = false
        }
    }
}
