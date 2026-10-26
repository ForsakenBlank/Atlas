package com.forsakenblank.atlas.desktop

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// the window opens where and how big it was last time
@Serializable
data class SavedWindow(val width: Float = 1100f, val height: Float = 760f, val x: Float? = null, val y: Float? = null)

object WindowMemory {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(dataDir: File): SavedWindow =
        runCatching { json.decodeFromString(SavedWindow.serializer(), File(dataDir, "window.json").readText()) }.getOrDefault(SavedWindow())

    fun save(dataDir: File, state: WindowState) {
        // a maximised or tiled size is not worth keeping, hyprland decides those anyway
        if (state.placement != WindowPlacement.Floating || state.isMinimized) return
        val position = state.position as? WindowPosition.Absolute
        val saved = SavedWindow(state.size.width.value, state.size.height.value, position?.x?.value, position?.y?.value)
        runCatching { File(dataDir, "window.json").writeText(json.encodeToString(SavedWindow.serializer(), saved)) }
    }
}
