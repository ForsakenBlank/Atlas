package com.forsakenblank.atlas.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.forsakenblank.atlas.MainActivity
import com.forsakenblank.atlas.R

const val REMINDER_CHANNEL = "reminders"

private const val TEST_REMINDER_ID = 1

fun createReminderChannel(context: Context) {
    val channel = NotificationChannel(REMINDER_CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = "Birthdays, tasks and events"
    }
    context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
}

fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

// false when Atlas is not allowed to notify, or the reminders channel was switched off
fun remindersAllowed(context: Context): Boolean {
    if (needsNotificationPermission(context)) return false
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
    val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(REMINDER_CHANNEL)
    return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
}

fun postReminder(context: Context, id: Int, title: String, text: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val open = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(title)
        .setContentText(text)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        .setAutoCancel(true)
        .build()
    // the permission can still be taken away between the check and here
    runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
}

fun sendTestReminder(context: Context) {
    postReminder(context, TEST_REMINDER_ID, "Test reminder", "Reminders are working. This is how they will look.")
}

fun openNotificationSettings(context: Context) {
    val notifications = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(notifications) }.onFailure {
        // some phones have no page just for notifications, app info links to it
        val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        runCatching { context.startActivity(appInfo) }
    }
}
