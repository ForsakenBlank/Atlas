package com.forsakenblank.atlas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.forsakenblank.atlas.platform.platform
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.launch

// only shows while reminders are on but the system is not letting them through
@Composable
fun NotificationsBlockedRow(onOpenSettings: () -> Unit) {
    val reminders = platform().reminders
    var allowed by remember { mutableStateOf(reminders.allowed()) }
    // checked again on coming back, in case they were just switched on
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = reminders.allowed() }
    if (allowed || !LocalSettings.current.remindersOn) return
    ListItem(
        leadingContent = { Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        headlineContent = { Text("Notifications are blocked") },
        supportingContent = {
            Text("Atlas can't show reminders until notifications are allowed. Tap here, then switch on notifications for Atlas.")
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.clickable(onClick = onOpenSettings),
    )
}

// posts a reminder straight away, asking for permission first when the system still needs it
@Composable
fun rememberTestReminder(): () -> Unit {
    val reminders = platform().reminders
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    fun explainBlocked() {
        scope.launch {
            val result = snackbar.showSnackbar("Notifications are blocked for Atlas", actionLabel = "Settings")
            if (result == SnackbarResult.ActionPerformed) reminders.openSettings()
        }
    }

    fun send() {
        reminders.sendTest()
        scope.launch { snackbar.showSnackbar("Test reminder sent") }
    }

    val askPermission = reminders.rememberPermissionRequest { granted ->
        if (granted && reminders.allowed()) send() else explainBlocked()
    }

    return {
        when {
            reminders.allowed() -> send()
            reminders.canAsk() -> askPermission()
            else -> explainBlocked()
        }
    }
}
