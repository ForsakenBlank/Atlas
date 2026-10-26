package com.forsakenblank.atlas

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.AtlasDatabase
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.BackupManager
import com.forsakenblank.atlas.data.SettingsStore
import com.forsakenblank.atlas.platform.Platform
import com.forsakenblank.atlas.ui.focus.FocusTimer
import com.forsakenblank.atlas.util.REMINDER_DAYS
import com.forsakenblank.atlas.util.planReminders
import com.forsakenblank.atlas.util.startMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

val LocalAtlas = staticCompositionLocalOf<AtlasApp> { error("AtlasApp was not provided") }

// one per running app, the phone and the pc each make theirs on start up
class AtlasApp(
    val platform: Platform,
    val database: AtlasDatabase,
    settingsData: DataStore<Preferences>,
) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val repository = AtlasRepository(database)
    val settingsStore = SettingsStore(settingsData)
    val backups = BackupManager(repository)

    // null until the settings file has been read once
    val settings: StateFlow<AppSettings?> = settingsStore.settings.stateIn(appScope, SharingStarted.Eagerly, null)

    // lives here so the timer keeps going when you leave the focus screen
    val focusTimer by lazy { FocusTimer(this) }

    private val reminderLock = Mutex()

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        appScope.launch { settingsStore.update(transform) }
    }

    fun start() {
        appScope.launch {
            val days = settingsStore.settings.first().trashDays
            repository.purgeOldTrash(days)
        }
        watchReminders()
    }

    // works out every reminder for the next few days and hands them to the phone or pc to show
    suspend fun rescheduleReminders() {
        reminderLock.withLock {
            val settings = settingsStore.settings.first()
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            // two days past the window, for reminders set a day before the event
            val events = repository.eventsBetween(today.startMillis(), today.plusDays(REMINDER_DAYS + 2L).startMillis()).first()
            val tasks = repository.tasks().first()
            val countdowns = repository.countdowns().first()
            val planned = planReminders(System.currentTimeMillis(), zone, settings, events, tasks, countdowns)
            platform.reminders.schedule(planned, settings.remindersOn)
        }
    }

    // plans reminders again a moment after anything they depend on changes
    @OptIn(FlowPreview::class)
    private fun watchReminders() {
        appScope.launch {
            combine(
                settingsStore.settings,
                repository.eventsBetween(0, Long.MAX_VALUE),
                repository.tasks(),
                repository.countdowns(),
            ) { settings, events, tasks, countdowns -> listOf(settings, events, tasks, countdowns) }
                .distinctUntilChanged()
                .debounce(1_000)
                .collect { runCatching { rescheduleReminders() } }
        }
    }
}
