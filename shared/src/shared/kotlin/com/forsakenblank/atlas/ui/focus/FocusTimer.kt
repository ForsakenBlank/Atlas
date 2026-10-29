package com.forsakenblank.atlas.ui.focus

import com.forsakenblank.atlas.AtlasApp
import com.forsakenblank.atlas.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusPhase(val label: String) {
    FOCUS("Focus"),
    SHORT_BREAK("Short break"),
    LONG_BREAK("Long break"),
}

data class FocusState(
    val phase: FocusPhase,
    val running: Boolean,
    val endsAt: Long, // wall clock time the phase ends, only set while running
    val remainingMillis: Long, // also kept fresh by the ticker while running
    val totalMillis: Long, // full length of the current phase
    val sessionsDone: Int, // focus sessions finished in this cycle
    val phaseStartedAt: Long?, // null until the phase is first started
) {
    val started: Boolean get() = phaseStartedAt != null

    // 1 at the start of a phase and 0 when it is over
    val fractionLeft: Float
        get() = if (totalMillis <= 0) 0f else (remainingMillis.toFloat() / totalMillis).coerceIn(0f, 1f)
}

class FocusTimer(private val app: AtlasApp) {

    private val _state = MutableStateFlow(idleState(FocusPhase.FOCUS, 0, currentSettings()))
    val state: StateFlow<FocusState> = _state.asStateFlow()

    // short messages for the focus screen, dropped when nobody is listening
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var ticker: Job? = null

    init {
        // new lengths from settings apply until the phase has been started
        app.appScope.launch {
            app.settings.filterNotNull().collect { settings ->
                _state.update { if (it.started) it else idleState(it.phase, it.sessionsDone, settings) }
            }
        }
    }

    fun start() {
        val now = System.currentTimeMillis()
        _state.update {
            if (it.running) {
                it
            } else {
                it.copy(
                    running = true,
                    endsAt = now + it.remainingMillis,
                    phaseStartedAt = it.phaseStartedAt ?: now,
                )
            }
        }
        tick()
    }

    fun pause() {
        val now = System.currentTimeMillis()
        _state.update {
            if (!it.running) it else it.copy(running = false, endsAt = 0, remainingMillis = (it.endsAt - now).coerceAtLeast(0))
        }
    }

    fun resume() = start()

    fun toggle() {
        if (_state.value.running) pause() else start()
    }

    // stops and goes back to the full length of the current phase
    fun reset() {
        _state.update { idleState(it.phase, it.sessionsDone, currentSettings()) }
    }

    // ends the phase now, a skipped focus session is not counted or logged
    fun skip() {
        val current = _state.value
        val settings = currentSettings()
        val autoStart = current.running && settings.autoStartNext
        when (current.phase) {
            FocusPhase.FOCUS -> moveTo(FocusPhase.SHORT_BREAK, current.sessionsDone, autoStart, settings)
            FocusPhase.SHORT_BREAK -> moveTo(FocusPhase.FOCUS, current.sessionsDone, autoStart, settings)
            FocusPhase.LONG_BREAK -> moveTo(FocusPhase.FOCUS, 0, autoStart, settings)
        }
    }

    fun select(phase: FocusPhase) {
        _state.update { if (it.running) it else idleState(phase, it.sessionsDone, currentSettings()) }
    }

    // works from the stored end time, so a late tick never makes the timer drift
    private fun tick() {
        if (ticker?.isActive == true) return
        // plain Main always dispatches, so the job is stored before the loop first runs
        ticker = app.appScope.launch(Dispatchers.Main) {
            while (true) {
                val current = _state.value
                if (!current.running) break
                val left = current.endsAt - System.currentTimeMillis()
                if (left <= 0) {
                    finishPhase(current)
                } else {
                    _state.value = current.copy(remainingMillis = left)
                    delay(left.coerceAtMost(250))
                }
            }
        }
    }

    private fun finishPhase(ended: FocusState) {
        val settings = currentSettings()
        buzz(settings)
        when (ended.phase) {
            FocusPhase.FOCUS -> {
                val done = ended.sessionsDone + 1
                val next = if (done >= settings.sessionsBeforeLongBreak.coerceAtLeast(1)) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK
                moveTo(next, done, settings.autoStartNext, settings)
                saveSession(ended, settings.focusTrackerId)
            }
            FocusPhase.SHORT_BREAK -> {
                moveTo(FocusPhase.FOCUS, ended.sessionsDone, settings.autoStartNext, settings)
                _events.tryEmit("Break over")
            }
            FocusPhase.LONG_BREAK -> {
                moveTo(FocusPhase.FOCUS, 0, settings.autoStartNext, settings)
                _events.tryEmit("Break over")
            }
        }
    }

    private fun moveTo(phase: FocusPhase, sessionsDone: Int, autoStart: Boolean, settings: AppSettings) {
        val next = idleState(phase, sessionsDone, settings)
        if (autoStart) {
            val now = System.currentTimeMillis()
            _state.value = next.copy(running = true, endsAt = now + next.totalMillis, phaseStartedAt = now)
            tick()
        } else {
            _state.value = next
        }
    }

    private fun saveSession(ended: FocusState, trackerId: Long?) {
        if (trackerId == null) {
            _events.tryEmit("Focus session done, time for a break")
            return
        }
        // logs the planned length so pauses do not count as focus time
        val seconds = ended.totalMillis / 1000
        val startedAt = ended.phaseStartedAt ?: (System.currentTimeMillis() - ended.totalMillis)
        app.appScope.launch {
            // the tracker may have been deleted since it was picked
            val name = runCatching {
                val item = app.repository.item(trackerId)?.takeIf { it.deletedAt == null }
                if (item != null) app.repository.logDuration(trackerId, startedAt, seconds)
                item?.name
            }.getOrNull()
            _events.tryEmit(
                if (name != null) "Focus session saved to $name"
                else "Focus session done, but its tracker could not be found"
            )
        }
    }

    private fun buzz(settings: AppSettings) {
        if (!settings.haptics) return
        runCatching { app.platform.vibrate(400) }
    }

    private fun currentSettings(): AppSettings = app.settings.value ?: AppSettings()

    private fun idleState(phase: FocusPhase, sessionsDone: Int, settings: AppSettings): FocusState {
        val length = lengthOf(phase, settings)
        return FocusState(
            phase = phase,
            running = false,
            endsAt = 0,
            remainingMillis = length,
            totalMillis = length,
            sessionsDone = sessionsDone,
            phaseStartedAt = null,
        )
    }

    private fun lengthOf(phase: FocusPhase, settings: AppSettings): Long {
        val minutes = when (phase) {
            FocusPhase.FOCUS -> settings.focusMinutes
            FocusPhase.SHORT_BREAK -> settings.shortBreakMinutes
            FocusPhase.LONG_BREAK -> settings.longBreakMinutes
        }
        return minutes.coerceAtLeast(1) * 60_000L
    }
}
