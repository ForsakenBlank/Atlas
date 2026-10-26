package com.forsakenblank.atlas.ui.track

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.formatDuration
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackerButton(
    summary: TrackerSummary,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
    selected: Boolean = false,
) {
    val buzz = rememberHaptic()
    val showStreak = LocalSettings.current.showStreaksOnCards && summary.streak > 0
    val accent = summary.item.color.toItemColor(MaterialTheme.colorScheme.secondary)
    val container = if (summary.goalMet || summary.running) {
        accent.copy(alpha = 0.28f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    Card(
        modifier = modifier.combinedClickable(
            onClick = {
                if (!selecting) buzz()
                onTap()
            },
            onLongClick = {
                buzz()
                onLongPress()
            },
        ),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(summary), contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    summary.item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (selecting) {
                    Icon(
                        if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = if (selected) "Selected" else "Not selected",
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                } else if (showStreak) {
                    Icon(
                        Icons.Outlined.LocalFireDepartment,
                        contentDescription = "Streak",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("${summary.streak}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (summary.running) {
                LiveTimer(since = summary.tracker.runningSince ?: 0L, color = accent)
            } else {
                Text(
                    summary.todayLabel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            summary.progress?.let { p ->
                LinearProgressIndicator(
                    progress = { p },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = accent.copy(alpha = 0.18f),
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun LiveTimer(since: Long, color: Color) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Text(
        formatDuration(((now - since) / 1000).coerceAtLeast(0)),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

fun kindIcon(summary: TrackerSummary) = when (summary.kind) {
    TrackerKind.COUNTER -> Icons.Outlined.AddCircleOutline
    TrackerKind.YES_NO -> if (summary.doneToday) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked
    TrackerKind.TIMER -> if (summary.running) Icons.Filled.Stop else Icons.Filled.PlayArrow
    TrackerKind.NUMBER -> Icons.Outlined.Straighten
    TrackerKind.RATING -> if (summary.doneToday) Icons.Filled.Star else Icons.Outlined.StarOutline
}
