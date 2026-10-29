@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.forsakenblank.atlas.ui.focus

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import com.forsakenblank.atlas.ui.common.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.forsakenblank.atlas.platform.KeepScreenOn
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.settings.SettingsCategory
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.formatTime
import kotlinx.coroutines.launch

@Composable
fun FocusScreen(navigator: AtlasNavigator) {
    val app = atlasApp()
    val timer = app.focusTimer
    val state by timer.state.collectAsStateWithLifecycle()
    val settings = LocalSettings.current
    val snackbar = LocalSnackbar.current
    val buzz = rememberHaptic()
    val trackers by remember(app) { app.repository.trackers() }.collectAsStateWithLifecycle(initialValue = null)

    // timer messages only show while this screen is in front
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(timer, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            timer.events.collect { message ->
                snackbar.currentSnackbarData?.dismiss()
                launch { snackbar.showSnackbar(message) }
            }
        }
    }

    KeepScreenOn(settings.keepScreenOn && state.running)

    val ringSpec: AnimationSpec<Float> = if (settings.reduceMotion) {
        snap()
    } else {
        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.0001f)
    }
    val progress by animateFloatAsState(targetValue = state.fractionLeft, animationSpec = ringSpec, label = "ring")

    val trackerId = settings.focusTrackerId
    val trackerName = trackerId?.let { id -> trackers?.firstOrNull { it.item.id == id }?.item?.name }
    // wait for the tracker list before saying nothing is logged, so the card does not flicker
    val trackersReady = trackerId == null || trackers != null

    val status = when {
        state.running -> "Ends at ${formatTime(state.endsAt, settings.use24Hour)}"
        state.started -> "Paused"
        else -> "Ready when you are"
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            PhaseChips(
                current = state.phase,
                enabled = !state.running,
                onSelect = { phase ->
                    if (phase != state.phase) {
                        buzz()
                        timer.select(phase)
                    }
                },
            )

            TimerRing(
                progress = progress,
                time = formatClock(state.remainingMillis),
                label = state.phase.label,
                status = status,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            Controls(
                running = state.running,
                started = state.started,
                reduceMotion = settings.reduceMotion,
                onReset = {
                    buzz()
                    timer.reset()
                },
                onToggle = {
                    buzz()
                    timer.toggle()
                },
                onSkip = {
                    buzz()
                    timer.skip()
                },
            )

            SessionDots(
                done = state.sessionsDone,
                total = settings.sessionsBeforeLongBreak.coerceIn(1, 12),
            )

            if (trackersReady) {
                LoggingCard(
                    trackerName = trackerName,
                    onChoose = { navigator.openSettingsCategory(SettingsCategory.FOCUS) },
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun PhaseChips(current: FocusPhase, enabled: Boolean, onSelect: (FocusPhase) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FocusPhase.entries.forEach { phase ->
            FilterChip(
                selected = phase == current,
                onClick = { onSelect(phase) },
                label = { Text(phase.label) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun TimerRing(progress: Float, time: String, label: String, status: String, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val arc = MaterialTheme.colorScheme.primary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            // the time left shrinks clockwise from the top, like a clock hand clearing the ring
            if (progress > 0f) {
                drawArc(
                    color = arc,
                    startAngle = -90f + 360f * (1f - progress),
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val big = if (time.length > 5) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge
            Text(
                time,
                style = big.copy(fontWeight = FontWeight.Light, fontFeatureSettings = "tnum"),
                maxLines = 1,
                softWrap = false,
            )
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Controls(
    running: Boolean,
    started: Boolean,
    reduceMotion: Boolean,
    onReset: () -> Unit,
    onToggle: () -> Unit,
    onSkip: () -> Unit,
) {
    // the big button is round while stopped and softens to a rounded square while running
    val cornerSpec: AnimationSpec<Dp> = if (reduceMotion) snap() else spring(stiffness = Spring.StiffnessMediumLow)
    val corner by animateDpAsState(targetValue = if (running) 28.dp else 48.dp, animationSpec = cornerSpec, label = "corner")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        FilledTonalIconButton(onClick = onReset, enabled = started, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Outlined.Replay, contentDescription = "Reset")
        }
        FilledIconButton(
            onClick = onToggle,
            shape = RoundedCornerShape(corner),
            modifier = Modifier.size(96.dp),
        ) {
            Icon(
                if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = when {
                    running -> "Pause"
                    started -> "Resume"
                    else -> "Start"
                },
                modifier = Modifier.size(44.dp),
            )
        }
        FilledTonalIconButton(onClick = onSkip, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Outlined.SkipNext, contentDescription = "Skip")
        }
    }
}

@Composable
private fun SessionDots(done: Int, total: Int) {
    val filled = done.coerceIn(0, total)
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(total) { index ->
                val isDone = index < filled
                Box(
                    Modifier
                        .size(12.dp)
                        .background(if (isDone) primary else Color.Transparent, CircleShape)
                        .border(1.5.dp, if (isDone) primary else outline, CircleShape),
                )
            }
        }
        val sessions = if (total == 1) "session" else "sessions"
        Text(
            when {
                filled == 0 -> "Long break after $total $sessions"
                filled >= total -> "All $total $sessions done, enjoy the long break"
                else -> "$filled of $total $sessions done"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoggingCard(trackerName: String?, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    AtlasCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        ) {
            Icon(
                Icons.Outlined.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (trackerName != null) "Logging to $trackerName" else "Focus time is not being logged",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (trackerName != null) "Finished sessions are saved there" else "Pick a timer tracker to keep a record",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onChoose) {
                Text(if (trackerName != null) "Change" else "Choose a tracker")
            }
        }
    }
}

// mm:ss, or h:mm:ss once a phase is an hour or longer
private fun formatClock(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1000
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
