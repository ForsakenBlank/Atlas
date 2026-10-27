package com.forsakenblank.atlas.desktop

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.forsakenblank.atlas.AtlasApp
import com.forsakenblank.atlas.platform.buildAtlasDatabase
import com.forsakenblank.atlas.platform.settingsDataStore
import com.forsakenblank.atlas.ui.AtlasContent
import java.awt.Dimension
import java.awt.Toolkit
import java.io.File
import java.io.RandomAccessFile
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main() {
    setWindowClass()
    val dataDir = dataDir()
    if (!holdSingleInstanceLock(dataDir)) {
        JOptionPane.showMessageDialog(null, "Atlas is already open.", "Atlas", JOptionPane.INFORMATION_MESSAGE)
        exitProcess(0)
    }

    val platform = DesktopPlatform(dataDir)
    val app = AtlasApp(platform, buildAtlasDatabase(dataDir), settingsDataStore(dataDir))
    platform.reminders.onNewDay = { app.rescheduleReminders() }
    app.start()

    // first time on a scaled hyprland monitor the interface and the window both start at the monitor's size
    val firstScale = if (File(dataDir, "window.json").exists()) null else Hyprland.suggestedScale()
    firstScale?.let { scale -> app.updateSettings { it.copy(uiScale = scale) } }
    val saved = WindowMemory.load(dataDir).let { window ->
        if (firstScale == null) window else window.copy(width = window.width * firstScale, height = window.height * firstScale)
    }

    application {
        val state = rememberWindowState(
            size = DpSize(saved.width.dp, saved.height.dp),
            position = if (saved.x != null && saved.y != null) WindowPosition(saved.x.dp, saved.y.dp) else WindowPosition.PlatformDefault,
        )

        Window(
            onCloseRequest = {
                WindowMemory.save(dataDir, state)
                exitApplication()
            },
            state = state,
            title = "Atlas",
            icon = painterResource("atlas.png"),
            // escape and the mouse back button work like the phone's back gesture
            onPreviewKeyEvent = { event ->
                event.type == KeyEventType.KeyDown && event.key == Key.Escape && platform.back.handle()
            },
        ) {
            window.minimumSize = Dimension(360, 520)
            platform.back.watchMouse(window)
            AtlasContent(app)
        }

        if (isWindows()) WindowsTray(platform, onOpen = { state.isMinimized = false }, onQuit = {
            WindowMemory.save(dataDir, state)
            exitApplication()
        })
    }
}

// on linux every window gets a class name, hyprland rules and app launchers match against it
private fun setWindowClass() {
    if (isWindows()) return
    runCatching {
        val toolkit = Toolkit.getDefaultToolkit()
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        field.isAccessible = true
        field.set(toolkit, "atlas")
    }
}

// the same places Luminescent uses, so nothing ends up next to the program itself
fun dataDir(): File {
    val dir = if (isWindows()) {
        File(System.getenv("LOCALAPPDATA") ?: (System.getProperty("user.home") + "\\AppData\\Local"), "Atlas")
    } else {
        val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: (System.getProperty("user.home") + "/.local/share")
        File(xdg, "atlas")
    }
    dir.mkdirs()
    return dir
}

// two copies writing the same database would trip over each other
private var instanceLock: Any? = null

private fun holdSingleInstanceLock(dataDir: File): Boolean {
    val channel = RandomAccessFile(File(dataDir, ".lock"), "rw").channel
    val lock = runCatching { channel.tryLock() }.getOrNull() ?: return false
    instanceLock = lock
    return true
}

fun isWindows(): Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
