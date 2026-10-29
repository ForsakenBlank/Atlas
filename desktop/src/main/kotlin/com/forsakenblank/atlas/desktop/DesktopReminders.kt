package com.forsakenblank.atlas.desktop

import androidx.compose.runtime.Composable
import com.forsakenblank.atlas.platform.ReminderHost
import com.forsakenblank.atlas.util.Reminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

// reminders fire while atlas is open on the pc, there is no alarm service to wake it up like a phone has
class DesktopReminders(private val icon: File) : ReminderHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var jobs = emptyList<Job>()

    // set by main so the next day gets planned just after midnight
    var onNewDay: suspend () -> Unit = {}

    // windows has no notify-send, the tray icon shows them instead
    var showOnWindows: (String, String) -> Unit = { _, _ -> }

    override fun allowed(): Boolean = isWindows() || hasNotifySend

    override fun canAsk(): Boolean = false

    override fun openSettings() = Unit

    override fun sendTest() = show("Test reminder", "Reminders are working. This is how they will look.")

    // AtlasApp only calls this while holding its reminder lock, so two plans never overlap
    override suspend fun schedule(planned: List<Reminder>, enabled: Boolean) {
        jobs.forEach { it.cancel() }
        if (!enabled) {
            jobs = emptyList()
            return
        }
        val now = System.currentTimeMillis()
        val upcoming = planned.filter { it.at > now }.map { reminder ->
            scope.launch {
                delay(reminder.at - System.currentTimeMillis())
                show(reminder.title, reminder.text)
            }
        }
        val zone = ZoneId.systemDefault()
        val nextDay = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(5).toInstant().toEpochMilli()
        val refresh = scope.launch {
            delay(nextDay - System.currentTimeMillis())
            onNewDay()
        }
        jobs = upcoming + refresh
    }

    @Composable
    override fun rememberPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = { onResult(allowed()) }

    private fun show(title: String, text: String) {
        if (isWindows()) {
            showOnWindows(title, text)
            return
        }
        // mako, dunst, swaync and every other linux notification daemon listen to this
        runCatching {
            ProcessBuilder("notify-send", "--app-name=Atlas", "--icon=${icon.absolutePath}", title, text)
                .redirectErrorStream(true)
                .start()
        }
    }

    private val hasNotifySend: Boolean by lazy {
        System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it, "notify-send").canExecute() }
    }
}
