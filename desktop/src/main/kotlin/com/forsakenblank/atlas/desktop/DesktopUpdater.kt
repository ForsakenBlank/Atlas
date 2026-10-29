package com.forsakenblank.atlas.desktop

import com.forsakenblank.atlas.update.Release
import com.forsakenblank.atlas.update.Releases
import com.forsakenblank.atlas.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

// the pc version swaps its own folder: the new one is unpacked next to it, put in its place and started.
// your data lives in its own folder (see dataDir), so nothing in it is touched
class DesktopUpdater(private val dataDir: File) : Updater {

    override val assetName = if (isWindows()) "Atlas-windows-x64.zip" else "Atlas-linux-x64.tar.gz"

    // the packaged app is a folder with bin/Atlas (linux) or Atlas.exe (windows) in it. jpackage's launcher
    // says where it was started from; run from gradle there is no such folder and nothing can be swapped
    private fun appFolder(): File? {
        val launcher = System.getProperty("jpackage.app-path")?.let(::File)
            ?: ProcessHandle.current().info().command().orElse(null)?.let(::File)?.takeIf { it.nameWithoutExtension == "Atlas" }
            ?: return null
        val dir = if (isWindows()) launcher.parentFile else launcher.parentFile?.parentFile
        return dir?.takeIf { it.isDirectory && it.parentFile?.canWrite() == true }
    }

    override suspend fun install(release: Release, progress: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        val url = release.assets[assetName] ?: return@withContext "${release.tag} has no $assetName"
        val here = appFolder()
            ?: return@withContext "This copy can not replace itself (it is not the packaged app, or its folder is read only)"
        val stage = File(here.parentFile, ".atlas-update").apply { deleteRecursively(); mkdirs() }
        val archive = File(stage, assetName)
        Releases.download(url, archive, progress)
        progress("Unpacking…")
        val out = File(stage, "new").apply { mkdirs() }
        if (isWindows()) unzip(archive, out) else untar(archive, out)
        archive.delete()
        val fresh = File(out, "Atlas")
        val launcher = if (isWindows()) File(fresh, "Atlas.exe") else File(fresh, "bin/Atlas")
        if (!launcher.exists()) return@withContext "The download did not have Atlas in it"
        progress("Restarting…")
        if (isWindows()) swapOnWindows(stage, here, fresh) else swapOnLinux(stage, here, fresh)
        null
    }

    private fun unzip(zip: File, into: File) {
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val f = File(into, e.name).canonicalFile
                if (!f.path.startsWith(into.canonicalPath)) continue          // no paths that climb out
                if (e.isDirectory) f.mkdirs() else { f.parentFile.mkdirs(); f.outputStream().use { z.copyTo(it) } }
            }
        }
    }

    private fun untar(tar: File, into: File) {
        // tar keeps the launcher's executable bit, which java's own zip code would lose
        val p = ProcessBuilder("tar", "-xzf", tar.absolutePath, "-C", into.absolutePath).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        if (p.waitFor() != 0) error("tar failed: ${text.take(300)}")
    }

    // linux lets a running program's folder be renamed: swap now, then start the new one once this one is gone
    private fun swapOnLinux(stage: File, here: File, fresh: File) {
        val old = File(here.path + ".old")
        old.deleteRecursively()
        if (!here.renameTo(old)) error("Could not move ${here.path} aside")
        if (!fresh.renameTo(here)) {
            old.renameTo(here)
            error("Could not put the new version in place")
        }
        stage.deleteRecursively()
        val pid = ProcessHandle.current().pid()
        val script = "while kill -0 $pid 2>/dev/null; do sleep 0.2; done; rm -rf '${old.path}'; exec '${File(here, "bin/Atlas").path}'"
        ProcessBuilder("sh", "-c", script).inheritIO().start()
        exitProcess(0)
    }

    // windows keeps a running program's files locked: a small script waits for atlas to close, swaps, starts it
    private fun swapOnWindows(stage: File, here: File, fresh: File) {
        val old = File(here.path + ".old")
        val pid = ProcessHandle.current().pid()
        val bat = File(dataDir, "atlas-update.bat")
        bat.writeText(
            """
            @echo off
            :wait
            tasklist /FI "PID eq $pid" 2>nul | find "$pid" >nul && (timeout /t 1 /nobreak >nul & goto wait)
            if exist "${old.path}" rmdir /s /q "${old.path}"
            set tries=0
            :swap
            move "${here.path}" "${old.path}" >nul 2>&1 || (set /a tries+=1 & timeout /t 1 /nobreak >nul & if %tries% lss 30 goto swap)
            move "${fresh.path}" "${here.path}" >nul 2>&1 || (move "${old.path}" "${here.path}" >nul 2>&1 & goto start)
            rmdir /s /q "${stage.path}"
            :start
            start "" "${File(here, "Atlas.exe").path}"
            rmdir /s /q "${old.path}" >nul 2>&1
            del "%~f0"
            """.trimIndent().replace("\n", "\r\n"),
        )
        ProcessBuilder("cmd", "/c", "start", "", "/min", bat.absolutePath).start()
        exitProcess(0)
    }

    override fun openPage(url: String) {
        runCatching {
            if (isWindows()) ProcessBuilder("cmd", "/c", "start", "", url).start()
            else ProcessBuilder("xdg-open", url).start()
        }
    }
}
