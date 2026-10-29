package com.forsakenblank.atlas.desktop

import java.util.concurrent.TimeUnit

// hyprland draws xwayland windows at the monitor's real resolution when force_zero_scaling is on,
// so a 1.5x screen shows atlas tiny until its own interface size matches the monitor
object Hyprland {

    val running: Boolean get() = !System.getenv("HYPRLAND_INSTANCE_SIGNATURE").isNullOrBlank()

    // the scale atlas should start at, null when hyprland already scales it or is not running
    fun suggestedScale(): Float? {
        if (!running) return null
        val zeroScaling = ask("hyprctl", "getoption", "xwayland:force_zero_scaling", "-j")
            ?.let { Regex("\"int\"\\s*:\\s*(\\d+)").find(it)?.groupValues?.get(1) } == "1"
        if (!zeroScaling) return null
        val monitors = ask("hyprctl", "monitors", "-j") ?: return null
        // the focused monitor's scale, or the first one listed
        val focused = Regex("\\{[^{}]*?\"focused\"\\s*:\\s*true[^{}]*?}").find(monitors)?.value ?: monitors
        val scale = Regex("\"scale\"\\s*:\\s*([0-9.]+)").find(focused)?.groupValues?.get(1)?.toFloatOrNull() ?: return null
        return scale.takeIf { it > 1.05f }?.coerceAtMost(2f)
    }

    private fun ask(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) out else null
    }.getOrNull()
}
