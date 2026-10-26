package com.forsakenblank.atlas.notify

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import com.forsakenblank.atlas.AtlasApplication
import com.forsakenblank.atlas.platform.ReminderHost
import com.forsakenblank.atlas.util.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

// keeps one alarm per planned reminder, plus one just after midnight to plan the next day
class AndroidReminders(private val app: AtlasApplication) : ReminderHost {

    private val alarms by lazy { app.getSystemService(AlarmManager::class.java) }
    private val prefs by lazy { app.getSharedPreferences("reminders", Context.MODE_PRIVATE) }

    override fun allowed(): Boolean = remindersAllowed(app)

    override fun canAsk(): Boolean = needsNotificationPermission(app)

    override fun openSettings() = openNotificationSettings(app)

    override fun sendTest() = sendTestReminder(app)

    override suspend fun schedule(planned: List<Reminder>, enabled: Boolean) = withContext(Dispatchers.IO) {
        val keys = planned.map { it.key }.toSet()
        prefs.getStringSet(SCHEDULED, emptySet()).orEmpty().filter { it !in keys }.forEach { cancel(it) }
        planned.forEach { schedule(it) }
        prefs.edit().putStringSet(SCHEDULED, keys).apply()

        if (enabled) {
            val zone = ZoneId.systemDefault()
            val nextDay = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(5).toInstant().toEpochMilli()
            alarms.setAndAllowWhileIdle(AlarmManager.RTC, nextDay, refreshIntent())
        } else {
            alarms.cancel(refreshIntent())
        }
    }

    @Composable
    override fun rememberPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
        return {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onResult(allowed())
        }
    }

    // the data part makes each reminder its own pending intent, the extras ride along
    private fun intentFor(key: String): Intent =
        Intent(app, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .setData(Uri.parse("atlas://reminder/$key"))

    private fun schedule(reminder: Reminder) {
        val intent = intentFor(reminder.key)
            .putExtra(ReminderReceiver.EXTRA_KEY, reminder.key)
            .putExtra(ReminderReceiver.EXTRA_TITLE, reminder.title)
            .putExtra(ReminderReceiver.EXTRA_TEXT, reminder.text)
        val pending = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending)
    }

    private fun cancel(key: String) {
        val pending = PendingIntent.getBroadcast(app, 0, intentFor(key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) ?: return
        alarms.cancel(pending)
        pending.cancel()
    }

    private fun refreshIntent(): PendingIntent = PendingIntent.getBroadcast(
        app,
        0,
        Intent(app, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REFRESH),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val SCHEDULED = "scheduled"
    }
}
