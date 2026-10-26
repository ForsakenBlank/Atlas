package com.forsakenblank.atlas.desktop

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

// the newest open screen that cares about back gets it first, like on android
class BackDispatcher {
    private val handlers = mutableListOf<() -> Unit>()
    private var watching = false

    fun push(handler: () -> Unit) {
        handlers += handler
    }

    fun remove(handler: () -> Unit) {
        handlers.remove(handler)
    }

    // true when something took it, so the key press goes no further
    fun handle(): Boolean {
        val handler = handlers.lastOrNull() ?: return false
        handler()
        return true
    }

    // the side button on most mice, java numbers it 4 on windows and on linux
    fun watchMouse(window: Window) {
        if (watching) return
        watching = true
        Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
            if (event is MouseEvent && event.id == MouseEvent.MOUSE_RELEASED && event.button == 4 &&
                SwingUtilities.getWindowAncestor(event.component) == window
            ) {
                handle()
            }
        }, AWTEvent.MOUSE_EVENT_MASK)
    }
}
