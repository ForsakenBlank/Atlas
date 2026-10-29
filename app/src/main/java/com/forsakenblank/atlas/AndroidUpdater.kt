package com.forsakenblank.atlas

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.forsakenblank.atlas.update.Release
import com.forsakenblank.atlas.update.Releases
import com.forsakenblank.atlas.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// downloads the apk and hands it to android's installer, which asks you to confirm. every build is signed
// with the same key, so it installs over this one and keeps everything
class AndroidUpdater(private val app: AtlasApplication) : Updater {

    override val assetName = "atlas.apk"

    override val canDowngrade = false

    override val downgradeNote =
        "Android can not install an older version over a newer one. To go back: Settings → Backup and data → Back up now, uninstall Atlas, install the older atlas.apk from GitHub, then restore."

    override suspend fun install(release: Release, progress: (String) -> Unit): String? {
        val url = release.assets[assetName] ?: return "${release.tag} has no $assetName"
        // android 8 and up asks once whether atlas may install apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !app.packageManager.canRequestPackageInstalls()) {
            app.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return "Allow Atlas to install apps on the screen that just opened, then press the button again"
        }
        val dir = File(app.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val apk = File(dir, "atlas-${release.version}.apk")
        withContext(Dispatchers.IO) { Releases.download(url, apk, progress) }
        progress("Opening the installer…")
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.updates", apk)
        app.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return null
    }

    override fun openPage(url: String) {
        runCatching { app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
