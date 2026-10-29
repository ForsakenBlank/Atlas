package com.forsakenblank.atlas.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import com.forsakenblank.atlas.util.DAY_MS

// picks a day as an epoch day, maxDay stops anything later being chosen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(day: Long, onDismiss: () -> Unit, maxDay: Long? = null, onPick: (Long) -> Unit) {
    val limit = object : SelectableDates {
        override fun isSelectableDate(utcTimeMillis: Long) = maxDay == null || utcTimeMillis < (maxDay + 1) * DAY_MS
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = day * DAY_MS, selectableDates = limit)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(Math.floorDiv(it, DAY_MS)) } }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}
