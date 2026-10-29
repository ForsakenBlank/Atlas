package com.forsakenblank.atlas

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forsakenblank.atlas.data.AppIcon
import com.forsakenblank.atlas.ui.AtlasContent
import com.forsakenblank.atlas.ui.theme.isDarkMode
import com.forsakenblank.atlas.ui.theme.themeFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = (application as AtlasApplication).atlas
        // a rotation recreates the activity with the same intent, it was handled the first time
        if (savedInstanceState == null) handle(intent)
        setContent {
            AtlasContent(app)

            // the phone only bits below wait for settings like the shared content does
            val settings by app.settings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent

            val dark = current.themeFor(current.isDarkMode()).dark
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(current.hideInRecents) {
                if (current.hideInRecents) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            LaunchedEffect(current.appIcon) { applyAppIcon(this@MainActivity, current.appIcon) }

            // android 13 and up needs a yes before reminders can show, asked once after the welcome screen
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // the answer needs no handling here, each reminder checks it before it shows
                val askForNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(current.onboarded, current.remindersOn, current.askedNotifications) {
                    if (current.onboarded && current.remindersOn && !current.askedNotifications) {
                        // saved before asking, so turning the phone while the prompt is up does not ask twice
                        app.updateSettings { s -> s.copy(askedNotifications = true) }
                        askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.forsakenblank.atlas.NEW_NOTE"
        const val ACTION_TASKS = "com.forsakenblank.atlas.TASKS"
        const val ACTION_FOCUS = "com.forsakenblank.atlas.FOCUS"
        const val ACTION_JOURNAL = "com.forsakenblank.atlas.JOURNAL"
        const val ACTION_HABITS = "com.forsakenblank.atlas.HABITS"
        const val ACTION_SYNC = "com.forsakenblank.atlas.SYNC"
    }
}

// shortcuts and widget buttons open atlas with one of these actions
private fun MainActivity.handle(intent: Intent?) {
    val launch = when (intent?.action) {
        MainActivity.ACTION_NEW_NOTE -> Launch.NEW_NOTE
        MainActivity.ACTION_TASKS -> Launch.TASKS
        MainActivity.ACTION_FOCUS -> Launch.FOCUS
        MainActivity.ACTION_JOURNAL -> Launch.JOURNAL
        MainActivity.ACTION_HABITS -> Launch.HABITS
        MainActivity.ACTION_SYNC -> Launch.SYNC
        else -> return
    }
    (application as AtlasApplication).atlas.launches.tryEmit(launch)
}

// each icon is an activity alias in the manifest, only the chosen one stays enabled
fun applyAppIcon(context: Context, icon: AppIcon) {
    val pm = context.packageManager
    val prefix = AtlasApplication::class.java.name.substringBeforeLast('.')
    AppIcon.entries.forEach { option ->
        val component = ComponentName(context.packageName, "$prefix.${option.alias}")
        val wanted = if (option == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val now = pm.getComponentEnabledSetting(component)
        // the manifest default only has the dark icon switched on
        val effective = if (now == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            if (option == AppIcon.DARK) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            now
        }
        if (effective != wanted) {
            runCatching { pm.setComponentEnabledSetting(component, wanted, PackageManager.DONT_KILL_APP) }
        }
    }
}
