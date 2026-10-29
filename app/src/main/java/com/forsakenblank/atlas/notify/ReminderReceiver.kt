package com.forsakenblank.atlas.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forsakenblank.atlas.AtlasApplication
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> {
                val key = intent.getStringExtra(EXTRA_KEY) ?: return
                postReminder(context, key.hashCode(), intent.getStringExtra(EXTRA_TITLE).orEmpty(), intent.getStringExtra(EXTRA_TEXT).orEmpty())
            }
            // a new day, a restart, a clock or time zone change, or an update all plan again
            ACTION_REFRESH, Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val atlas = (context.applicationContext as? AtlasApplication)?.atlas ?: return
                val pending = goAsync()
                atlas.appScope.launch {
                    runCatching { atlas.rescheduleReminders() }
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.forsakenblank.atlas.action.REMIND"
        const val ACTION_REFRESH = "com.forsakenblank.atlas.action.REFRESH_REMINDERS"
        const val EXTRA_KEY = "key"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}
