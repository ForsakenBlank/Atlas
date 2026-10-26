package com.forsakenblank.atlas.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.rememberTrayState

// windows shows reminders as tray notifications, so atlas keeps a small icon there while open
@Composable
fun ApplicationScope.WindowsTray(platform: DesktopPlatform, onOpen: () -> Unit, onQuit: () -> Unit) {
    val tray = rememberTrayState()
    LaunchedEffect(tray) {
        platform.reminders.showOnWindows = { title, text -> tray.sendNotification(Notification(title, text)) }
    }
    Tray(
        icon = painterResource("atlas.png"),
        state = tray,
        tooltip = "Atlas",
        onAction = onOpen,
        menu = {
            Item("Open Atlas", onClick = onOpen)
            Item("Quit", onClick = onQuit)
        },
    )
}
