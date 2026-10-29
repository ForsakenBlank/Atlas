package com.forsakenblank.atlas.platform

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.forsakenblank.atlas.util.Reminder

enum class DeviceKind(val label: String) { PHONE("Phone"), PC("PC") }

// a file the person picked, written or read once
typealias FileWriter = suspend (String) -> Unit
typealias FileReader = suspend () -> String

// everything the shared code needs from the phone or the pc, each side fills this in its own way
interface Platform {
    val kind: DeviceKind

    // shown to the other device while syncing, like "Pixel 8" or the pc's name
    val deviceName: String

    // material you colours, only on android 12 and up
    val hasWallpaperColours: Boolean

    // a clock that never jumps when the time is changed, for stopwatches
    fun elapsedMillis(): Long

    fun vibrate(millis: Long)

    // hands text to other apps, returns a message to show when it was copied instead
    fun share(subject: String?, text: String): String?

    val reminders: ReminderHost

    @Composable
    fun wallpaperColours(dark: Boolean): ColorScheme?

    @Composable
    fun BackHandler(enabled: Boolean, onBack: () -> Unit)

    @Composable
    fun KeepScreenOn(on: Boolean)

    // each returns a launcher, the callback gets null when the person backs out
    @Composable
    fun rememberSaveFile(mimeType: String, onPicked: (FileWriter?) -> Unit): (suggestedName: String) -> Unit

    @Composable
    fun rememberOpenFile(onPicked: (FileReader?) -> Unit): () -> Unit
}

interface ReminderHost {
    // false while the system is blocking notifications for atlas
    fun allowed(): Boolean

    // true when a permission prompt could still unblock them
    fun canAsk(): Boolean

    fun openSettings()

    fun sendTest()

    // swaps whatever was planned before for this list
    suspend fun schedule(planned: List<Reminder>, enabled: Boolean)

    @Composable
    fun rememberPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit
}
