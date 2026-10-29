package com.forsakenblank.atlas.platform

import androidx.compose.runtime.Composable
import com.forsakenblank.atlas.LocalAtlas

// short forms so screens read the same on the phone and the pc

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) = LocalAtlas.current.platform.BackHandler(enabled, onBack)

@Composable
fun KeepScreenOn(on: Boolean) = LocalAtlas.current.platform.KeepScreenOn(on)

@Composable
fun platform(): Platform = LocalAtlas.current.platform
