package com.forsakenblank.atlas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forsakenblank.atlas.AtlasApp
import com.forsakenblank.atlas.LocalAtlas
import com.forsakenblank.atlas.ui.onboarding.WelcomeScreen
import com.forsakenblank.atlas.ui.theme.AtlasTheme

// the whole app, the phone and the pc both just drop this into their window
@Composable
fun AtlasContent(app: AtlasApp) {
    CompositionLocalProvider(LocalAtlas provides app) {
        // nothing is drawn until settings load, so the wrong theme never flashes up
        val settings by app.settings.collectAsStateWithLifecycle()
        val current = settings ?: return@CompositionLocalProvider
        AtlasTheme(current) {
            if (current.onboarded) AtlasRoot() else WelcomeScreen(onDone = {})
        }
    }
}
