package com.forsakenblank.atlas.desktop

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

// linux gets the desktop's own picker through zenity or kdialog when one is there,
// java's built in one looks out of place on hyprland, windows already gets its native one
object FileDialogs {

    private sealed interface Answer {
        data class Picked(val file: File) : Answer
        data object Cancelled : Answer
        data object NoTool : Answer
    }

    fun save(suggestedName: String): File? {
        val home = System.getProperty("user.home")
        val answer = if (isWindows()) {
            Answer.NoTool
        } else {
            ask("zenity", "--file-selection", "--save", "--confirm-overwrite", "--title=Save a backup", "--filename=$home/$suggestedName")
                .orElse { ask("kdialog", "--getsavefilename", "$home/$suggestedName", "*.json") }
        }
        return answer.orElse { awt(FileDialog.SAVE, suggestedName) }.file()
    }

    fun open(): File? {
        val home = System.getProperty("user.home")
        val answer = if (isWindows()) {
            Answer.NoTool
        } else {
            ask("zenity", "--file-selection", "--title=Choose a backup", "--file-filter=Atlas backups | *.json", "--file-filter=All files | *")
                .orElse { ask("kdialog", "--getopenfilename", home, "*.json") }
        }
        return answer.orElse { awt(FileDialog.LOAD, null) }.file()
    }

    // only moves on to the next picker when this one is not installed, a cancel stays a cancel
    private fun Answer.orElse(next: () -> Answer): Answer = if (this == Answer.NoTool) next() else this

    private fun Answer.file(): File? = (this as? Answer.Picked)?.file

    private fun ask(vararg command: String): Answer {
        val installed = System.getenv("PATH").orEmpty().split(File.pathSeparator).any { File(it, command.first()).canExecute() }
        if (!installed) return Answer.NoTool
        return runCatching {
            val process = ProcessBuilder(*command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val path = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && path.isNotEmpty()) Answer.Picked(File(path)) else Answer.Cancelled
        }.getOrDefault(Answer.NoTool)
    }

    private fun awt(mode: Int, suggestedName: String?): Answer {
        val dialog = FileDialog(null as Frame?, if (mode == FileDialog.SAVE) "Save a backup" else "Choose a backup", mode)
        if (suggestedName != null) dialog.file = suggestedName
        dialog.isVisible = true
        val name = dialog.file ?: return Answer.Cancelled
        return Answer.Picked(File(dialog.directory, name))
    }
}
