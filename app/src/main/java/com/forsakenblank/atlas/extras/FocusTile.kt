package com.forsakenblank.atlas.extras

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.forsakenblank.atlas.AtlasApplication
import com.forsakenblank.atlas.ui.focus.FocusState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// a quick settings tile that starts and pauses the focus timer without opening the app
class FocusTile : TileService() {

    private val atlas get() = (application as AtlasApplication).atlas
    private var watching: Job? = null

    override fun onStartListening() {
        watching = atlas.appScope.launch { atlas.focusTimer.state.collect { show(it) } }
    }

    override fun onStopListening() {
        watching?.cancel()
        watching = null
    }

    override fun onClick() {
        atlas.focusTimer.toggle()
    }

    private fun show(state: FocusState) {
        val tile = qsTile ?: return
        tile.state = if (state.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = state.phase.label
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val seconds = state.remainingMillis / 1000
            tile.subtitle = if (state.running) "%d:%02d left".format(seconds / 60, seconds % 60) else "Tap to start"
        }
        tile.updateTile()
    }
}
