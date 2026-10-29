package com.forsakenblank.atlas.desktop

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import com.forsakenblank.atlas.platform.DeviceKind
import com.forsakenblank.atlas.platform.FileReader
import com.forsakenblank.atlas.platform.FileWriter
import com.forsakenblank.atlas.platform.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.InetAddress

class DesktopPlatform(dataDir: File) : Platform {

    override val kind = DeviceKind.PC

    override val deviceName: String = computerName()

    override val hasWallpaperColours = false

    override fun elapsedMillis(): Long = System.nanoTime() / 1_000_000

    override fun vibrate(millis: Long) = Unit

    // a pc has no share sheet, the clipboard is the next best thing
    override fun share(subject: String?, text: String): String {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
        return "Copied to the clipboard"
    }

    override val reminders = DesktopReminders(File(dataDir, "atlas.png").also { copyIcon(it) })

    override val updater = DesktopUpdater(dataDir)

    val back = BackDispatcher()

    private val io = CoroutineScope(Dispatchers.IO)

    @Composable
    override fun wallpaperColours(dark: Boolean): ColorScheme? = null

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        val latest = rememberUpdatedState(onBack)
        DisposableEffect(enabled) {
            if (!enabled) return@DisposableEffect onDispose { }
            val handler: () -> Unit = { latest.value() }
            back.push(handler)
            onDispose { back.remove(handler) }
        }
    }

    // nothing to do, a pc screen does not lock while you look at it the way a phone does
    @Composable
    override fun KeepScreenOn(on: Boolean) = Unit

    @Composable
    override fun rememberSaveFile(mimeType: String, onPicked: (FileWriter?) -> Unit): (String) -> Unit {
        val latest = rememberUpdatedState(onPicked)
        return { name ->
            io.launch {
                val file = FileDialogs.save(name)
                withContext(Dispatchers.Main) { latest.value(file?.let(::writerFor)) }
            }
        }
    }

    @Composable
    override fun rememberOpenFile(onPicked: (FileReader?) -> Unit): () -> Unit {
        val latest = rememberUpdatedState(onPicked)
        return {
            io.launch {
                val file = FileDialogs.open()
                withContext(Dispatchers.Main) { latest.value(file?.let(::readerFor)) }
            }
        }
    }
}

private fun writerFor(file: File): FileWriter = { text -> withContext(Dispatchers.IO) { file.writeText(text) } }

private fun readerFor(file: File): FileReader = { withContext(Dispatchers.IO) { file.readText() } }

// notify-send wants a file for its icon, so the one inside the jar is copied out once
private fun copyIcon(target: File) {
    if (target.exists()) return
    runCatching {
        DesktopPlatform::class.java.getResourceAsStream("/atlas.png")?.use { input -> target.outputStream().use { input.copyTo(it) } }
    }
}

private fun computerName(): String {
    System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }?.let { return it }
    runCatching { File("/etc/hostname").readText().trim() }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
    return runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "PC"
}
