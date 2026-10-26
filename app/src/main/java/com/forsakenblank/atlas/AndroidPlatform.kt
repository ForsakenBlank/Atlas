package com.forsakenblank.atlas

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.forsakenblank.atlas.notify.AndroidReminders
import com.forsakenblank.atlas.platform.DeviceKind
import com.forsakenblank.atlas.platform.FileReader
import com.forsakenblank.atlas.platform.FileWriter
import com.forsakenblank.atlas.platform.Platform
import com.forsakenblank.atlas.platform.ReminderHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidPlatform(private val app: AtlasApplication) : Platform {

    override val kind = DeviceKind.PHONE

    override val deviceName: String = Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)
        ?.takeIf { it.isNotBlank() }
        ?: Build.MODEL

    override val hasWallpaperColours = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()

    override fun vibrate(millis: Long) {
        app.getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun share(subject: String?, text: String): String? {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        app.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return null
    }

    override val reminders: ReminderHost = AndroidReminders(app)

    @Composable
    override fun wallpaperColours(dark: Boolean): ColorScheme? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val context = LocalContext.current
        return if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
        androidx.activity.compose.BackHandler(enabled, onBack)
    }

    @Composable
    override fun KeepScreenOn(on: Boolean) {
        val view = LocalView.current
        DisposableEffect(view, on) {
            view.keepScreenOn = on
            onDispose { view.keepScreenOn = false }
        }
    }

    @Composable
    override fun rememberSaveFile(mimeType: String, onPicked: (FileWriter?) -> Unit): (String) -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
            onPicked(uri?.let { writerFor(it) })
        }
        return { name -> launcher.launch(name) }
    }

    @Composable
    override fun rememberOpenFile(onPicked: (FileReader?) -> Unit): () -> Unit {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            onPicked(uri?.let { readerFor(it) })
        }
        return { launcher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) }
    }

    private fun writerFor(uri: Uri): FileWriter = { text ->
        withContext(Dispatchers.IO) {
            app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
                ?: error("Could not open the file")
        }
    }

    private fun readerFor(uri: Uri): FileReader = {
        withContext(Dispatchers.IO) {
            app.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: error("Could not open the file")
        }
    }
}
