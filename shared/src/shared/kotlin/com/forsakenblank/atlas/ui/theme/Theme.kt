package com.forsakenblank.atlas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.FontChoice
import com.forsakenblank.atlas.data.ThemeMode
import com.forsakenblank.atlas.platform.platform

val LocalSettings = compositionLocalOf { AppSettings() }

fun AppSettings.allThemes(): List<AtlasTheme> = BuiltInThemes.all + customThemes

fun AppSettings.themeFor(dark: Boolean): AtlasTheme {
    val id = if (dark) darkThemeId else lightThemeId
    return allThemes().firstOrNull { it.id == id }
        ?: BuiltInThemes.find(if (dark) BuiltInThemes.DEFAULT_DARK else BuiltInThemes.DEFAULT_LIGHT)!!
}

@Composable
fun AppSettings.isDarkMode(): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun AtlasTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = settings.isDarkMode()
    val theme = settings.themeFor(dark)
    val dynamic = if (settings.dynamicColor) platform().wallpaperColours(theme.dark) else null
    val colors = if (dynamic != null) {
        if (theme.dark && settings.pureBlack) dynamic.copy(background = Color.Black, surface = Color.Black) else dynamic
    } else {
        remember(theme, settings.pureBlack) { theme.toColorScheme(settings.pureBlack) }
    }
    val shapes = remember(settings.cornerRadius) { shapesFor(settings.cornerRadius) }
    val typography = remember(settings.font, settings.boldHeadings) { typographyFor(settings.font, settings.boldHeadings) }
    val base = LocalDensity.current
    val density = Density(base.density * settings.uiScale, base.fontScale * settings.fontScale)

    CompositionLocalProvider(LocalDensity provides density, LocalSettings provides settings) {
        MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
    }
}

private fun shapesFor(radius: Int): Shapes {
    val r = radius.coerceIn(0, 32)
    return Shapes(
        extraSmall = RoundedCornerShape((r / 4).dp),
        small = RoundedCornerShape((r / 2).dp),
        medium = RoundedCornerShape((r * 3 / 4).dp),
        large = RoundedCornerShape(r.dp),
        extraLarge = RoundedCornerShape((r * 3 / 2).dp),
    )
}

private fun typographyFor(font: FontChoice, boldHeadings: Boolean): Typography {
    val family = when (font) {
        FontChoice.DEFAULT -> FontFamily.Default
        FontChoice.SANS -> FontFamily.SansSerif
        FontChoice.SERIF -> FontFamily.Serif
        FontChoice.MONO -> FontFamily.Monospace
        FontChoice.CURSIVE -> FontFamily.Cursive
    }
    val t = Typography()
    fun body(style: TextStyle) = style.copy(fontFamily = family)
    fun heading(style: TextStyle) = style.copy(fontFamily = family, fontWeight = if (boldHeadings) FontWeight.Bold else style.fontWeight)
    return Typography(
        displayLarge = heading(t.displayLarge),
        displayMedium = heading(t.displayMedium),
        displaySmall = heading(t.displaySmall),
        headlineLarge = heading(t.headlineLarge),
        headlineMedium = heading(t.headlineMedium),
        headlineSmall = heading(t.headlineSmall),
        titleLarge = heading(t.titleLarge),
        titleMedium = heading(t.titleMedium),
        titleSmall = heading(t.titleSmall),
        bodyLarge = body(t.bodyLarge),
        bodyMedium = body(t.bodyMedium),
        bodySmall = body(t.bodySmall),
        labelLarge = body(t.labelLarge),
        labelMedium = body(t.labelMedium),
        labelSmall = body(t.labelSmall),
    )
}

// swatches offered when colouring folders, notes, events and trackers
val ItemColors = listOf(
    0xFFE57373, 0xFFF06292, 0xFFBA68C8, 0xFF9575CD, 0xFF7986CB, 0xFF64B5F6,
    0xFF4FC3F7, 0xFF4DB6AC, 0xFF81C784, 0xFFAED581, 0xFFFFD54F, 0xFFFFB74D,
    0xFFF4A259, 0xFFA1887F, 0xFF90A4AE, 0xFF616161,
).map { it.toInt() }
