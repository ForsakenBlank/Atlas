package com.forsakenblank.atlas.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.Serializable

// a theme is a handful of colours, everything else material needs is worked out from them
@Serializable
data class AtlasTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val background: Int,
    val surface: Int,
    val card: Int,
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val text: Int,
    val mutedText: Int,
    val border: Int,
    val danger: Int,
    val success: Int = 0xFF4CAF50.toInt(),
    val warning: Int = 0xFFFFB300.toInt(),
)

enum class ThemeSlot(val label: String, val hint: String) {
    BACKGROUND("Background", "behind everything"),
    SURFACE("Surface", "bars and sheets"),
    CARD("Cards", "cards and buttons on home"),
    PRIMARY("Primary", "main buttons and highlights"),
    SECONDARY("Secondary", "trackers and chips"),
    ACCENT("Accent", "small touches"),
    TEXT("Text", "main text"),
    MUTED_TEXT("Muted text", "hints and captions"),
    BORDER("Borders", "outlines and dividers"),
    DANGER("Danger", "delete and errors"),
    SUCCESS("Success", "goals hit"),
    WARNING("Warning", "streaks at risk"),
}

operator fun AtlasTheme.get(slot: ThemeSlot): Int = when (slot) {
    ThemeSlot.BACKGROUND -> background
    ThemeSlot.SURFACE -> surface
    ThemeSlot.CARD -> card
    ThemeSlot.PRIMARY -> primary
    ThemeSlot.SECONDARY -> secondary
    ThemeSlot.ACCENT -> accent
    ThemeSlot.TEXT -> text
    ThemeSlot.MUTED_TEXT -> mutedText
    ThemeSlot.BORDER -> border
    ThemeSlot.DANGER -> danger
    ThemeSlot.SUCCESS -> success
    ThemeSlot.WARNING -> warning
}

fun AtlasTheme.with(slot: ThemeSlot, color: Int): AtlasTheme = when (slot) {
    ThemeSlot.BACKGROUND -> copy(background = color)
    ThemeSlot.SURFACE -> copy(surface = color)
    ThemeSlot.CARD -> copy(card = color)
    ThemeSlot.PRIMARY -> copy(primary = color)
    ThemeSlot.SECONDARY -> copy(secondary = color)
    ThemeSlot.ACCENT -> copy(accent = color)
    ThemeSlot.TEXT -> copy(text = color)
    ThemeSlot.MUTED_TEXT -> copy(mutedText = color)
    ThemeSlot.BORDER -> copy(border = color)
    ThemeSlot.DANGER -> copy(danger = color)
    ThemeSlot.SUCCESS -> copy(success = color)
    ThemeSlot.WARNING -> copy(warning = color)
}

// black or white, whichever reads better on top of the colour
fun Color.onColor(): Color = if (luminance() > 0.45f) Color(0xFF111111) else Color.White

fun contrastRatio(a: Color, b: Color): Float {
    val l1 = maxOf(a.luminance(), b.luminance())
    val l2 = minOf(a.luminance(), b.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

fun AtlasTheme.toColorScheme(pureBlack: Boolean = false): ColorScheme {
    val bg = if (dark && pureBlack) Color.Black else Color(background)
    val surf = if (dark && pureBlack) Color.Black else Color(surface)
    val cardColor = if (dark && pureBlack) lerp(Color.Black, Color(card), 0.6f) else Color(card)
    val primaryColor = Color(primary)
    val secondaryColor = Color(secondary)
    val accentColor = Color(accent)
    val textColor = Color(text)
    val muted = Color(mutedText)
    val borderColor = Color(border)
    val dangerColor = Color(danger)

    fun container(c: Color) = lerp(surf, c, if (dark) 0.35f else 0.22f)
    fun onContainer(c: Color) = lerp(textColor, c, 0.25f)

    val start = if (dark) darkColorScheme() else lightColorScheme()
    return start.copy(
        primary = primaryColor,
        onPrimary = primaryColor.onColor(),
        primaryContainer = container(primaryColor),
        onPrimaryContainer = onContainer(primaryColor),
        inversePrimary = lerp(primaryColor, if (dark) Color.Black else Color.White, 0.4f),
        secondary = secondaryColor,
        onSecondary = secondaryColor.onColor(),
        secondaryContainer = container(secondaryColor),
        onSecondaryContainer = onContainer(secondaryColor),
        tertiary = accentColor,
        onTertiary = accentColor.onColor(),
        tertiaryContainer = container(accentColor),
        onTertiaryContainer = onContainer(accentColor),
        background = bg,
        onBackground = textColor,
        surface = surf,
        onSurface = textColor,
        surfaceVariant = lerp(surf, borderColor, 0.25f),
        onSurfaceVariant = muted,
        surfaceTint = primaryColor,
        inverseSurface = textColor,
        inverseOnSurface = bg,
        error = dangerColor,
        onError = dangerColor.onColor(),
        errorContainer = container(dangerColor),
        onErrorContainer = onContainer(dangerColor),
        outline = borderColor,
        outlineVariant = lerp(surf, borderColor, 0.5f),
        surfaceBright = lerp(surf, textColor, 0.08f),
        surfaceDim = lerp(surf, Color.Black, 0.1f),
        surfaceContainerLowest = lerp(bg, cardColor, 0.3f),
        surfaceContainerLow = lerp(bg, cardColor, 0.6f),
        surfaceContainer = cardColor,
        surfaceContainerHigh = lerp(cardColor, textColor, 0.05f),
        surfaceContainerHighest = lerp(cardColor, textColor, 0.1f),
    )
}

fun Color.hex(): String = "#%06X".format(toArgb() and 0xFFFFFF)

private fun c(hex: Long) = hex.toInt()

object BuiltInThemes {
    const val DEFAULT_LIGHT = "atlas-light"
    const val DEFAULT_DARK = "atlas-dark"

    val all: List<AtlasTheme> = listOf(
        AtlasTheme(
            DEFAULT_LIGHT, "Atlas Light", false,
            background = c(0xFFFBF7F2), surface = c(0xFFFBF7F2), card = c(0xFFF2EBE2),
            primary = c(0xFFD9692B), secondary = c(0xFF2E8B80), accent = c(0xFF1F3A5F),
            text = c(0xFF1E1B18), mutedText = c(0xFF6B635B), border = c(0xFFCFC6BC), danger = c(0xFFC62828),
        ),
        AtlasTheme(
            DEFAULT_DARK, "Atlas Dark", true,
            background = c(0xFF16130F), surface = c(0xFF16130F), card = c(0xFF231F1A),
            primary = c(0xFFF4A259), secondary = c(0xFF7FD1C7), accent = c(0xFFA9C7EE),
            text = c(0xFFF2ECE4), mutedText = c(0xFFB3A99D), border = c(0xFF4A433B), danger = c(0xFFEF9A9A),
        ),
        AtlasTheme(
            "midnight", "Midnight", true,
            background = c(0xFF0D1321), surface = c(0xFF0D1321), card = c(0xFF1A2236),
            primary = c(0xFF8FB8FF), secondary = c(0xFF7FD1C7), accent = c(0xFFE9C46A),
            text = c(0xFFE8EDF7), mutedText = c(0xFF9AA6BF), border = c(0xFF34405A), danger = c(0xFFFF8A80),
        ),
        AtlasTheme(
            "paper", "Paper", false,
            background = c(0xFFFAF8F3), surface = c(0xFFFAF8F3), card = c(0xFFFFFFFF),
            primary = c(0xFF3D3D3D), secondary = c(0xFF8C7B6B), accent = c(0xFFB5523B),
            text = c(0xFF222222), mutedText = c(0xFF77706A), border = c(0xFFDDD6CC), danger = c(0xFFB3261E),
        ),
        AtlasTheme(
            "forest", "Forest", true,
            background = c(0xFF0F1A14), surface = c(0xFF0F1A14), card = c(0xFF1A2A20),
            primary = c(0xFF8BD17C), secondary = c(0xFFD9C27A), accent = c(0xFF7FC8D1),
            text = c(0xFFE6F0E6), mutedText = c(0xFF9DB5A2), border = c(0xFF32483A), danger = c(0xFFFF8A80),
        ),
        AtlasTheme(
            "ocean", "Ocean", false,
            background = c(0xFFF2F8FC), surface = c(0xFFF2F8FC), card = c(0xFFE1EFF8),
            primary = c(0xFF0B6E99), secondary = c(0xFF2AA198), accent = c(0xFFF2994A),
            text = c(0xFF0D2233), mutedText = c(0xFF4D6B80), border = c(0xFFB9D3E3), danger = c(0xFFC62828),
        ),
        AtlasTheme(
            "sunset", "Sunset", true,
            background = c(0xFF1C1020), surface = c(0xFF1C1020), card = c(0xFF2C1A30),
            primary = c(0xFFFF8A65), secondary = c(0xFFF06292), accent = c(0xFFFFD54F),
            text = c(0xFFFBEAF0), mutedText = c(0xFFC2A3B5), border = c(0xFF4E3352), danger = c(0xFFFF8A80),
        ),
        AtlasTheme(
            "mono", "Mono", false,
            background = c(0xFFFFFFFF), surface = c(0xFFFFFFFF), card = c(0xFFF2F2F2),
            primary = c(0xFF000000), secondary = c(0xFF555555), accent = c(0xFF888888),
            text = c(0xFF000000), mutedText = c(0xFF666666), border = c(0xFFCCCCCC), danger = c(0xFF000000),
        ),
        AtlasTheme(
            "mono-dark", "Mono Dark", true,
            background = c(0xFF000000), surface = c(0xFF000000), card = c(0xFF141414),
            primary = c(0xFFFFFFFF), secondary = c(0xFFBBBBBB), accent = c(0xFF888888),
            text = c(0xFFFFFFFF), mutedText = c(0xFF999999), border = c(0xFF333333), danger = c(0xFFFFFFFF),
        ),
        AtlasTheme(
            "contrast", "High Contrast", true,
            background = c(0xFF000000), surface = c(0xFF000000), card = c(0xFF0A0A0A),
            primary = c(0xFFFFEB3B), secondary = c(0xFF00E5FF), accent = c(0xFFFF4081),
            text = c(0xFFFFFFFF), mutedText = c(0xFFE0E0E0), border = c(0xFFFFFFFF), danger = c(0xFFFF5252),
        ),
        AtlasTheme(
            "terminal", "Retro Terminal", true,
            background = c(0xFF050A05), surface = c(0xFF050A05), card = c(0xFF0C160C),
            primary = c(0xFF33FF66), secondary = c(0xFF22CC55), accent = c(0xFFFFB000),
            text = c(0xFF66FF88), mutedText = c(0xFF2FA84A), border = c(0xFF1A4D26), danger = c(0xFFFF5555),
        ),
        AtlasTheme(
            "rose", "Rose", false,
            background = c(0xFFFFF5F7), surface = c(0xFFFFF5F7), card = c(0xFFFBE4EA),
            primary = c(0xFFC2185B), secondary = c(0xFF8E6C88), accent = c(0xFFE57373),
            text = c(0xFF2B1A20), mutedText = c(0xFF7A5C66), border = c(0xFFE8C5CF), danger = c(0xFFB71C1C),
        ),
        AtlasTheme(
            "lavender", "Lavender", false,
            background = c(0xFFF7F5FC), surface = c(0xFFF7F5FC), card = c(0xFFEAE4F7),
            primary = c(0xFF6A4FB3), secondary = c(0xFF4F8AB3), accent = c(0xFFD17FA8),
            text = c(0xFF1F1A2B), mutedText = c(0xFF6A6280), border = c(0xFFD3CBE6), danger = c(0xFFC62828),
        ),
        AtlasTheme(
            "coffee", "Coffee", true,
            background = c(0xFF1A1411), surface = c(0xFF1A1411), card = c(0xFF2A211C),
            primary = c(0xFFD4A373), secondary = c(0xFFA3B18A), accent = c(0xFFE9C46A),
            text = c(0xFFF1E6DA), mutedText = c(0xFFB8A593), border = c(0xFF4A3B31), danger = c(0xFFE57373),
        ),
        AtlasTheme(
            "nord", "Arctic", true,
            background = c(0xFF2E3440), surface = c(0xFF2E3440), card = c(0xFF3B4252),
            primary = c(0xFF88C0D0), secondary = c(0xFFA3BE8C), accent = c(0xFFEBCB8B),
            text = c(0xFFECEFF4), mutedText = c(0xFFAEB7C6), border = c(0xFF4C566A), danger = c(0xFFBF616A),
        ),
        AtlasTheme(
            "dusk", "Dusk", true,
            background = c(0xFF1E1F29), surface = c(0xFF1E1F29), card = c(0xFF282A36),
            primary = c(0xFFBD93F9), secondary = c(0xFF8BE9FD), accent = c(0xFFFF79C6),
            text = c(0xFFF8F8F2), mutedText = c(0xFFA4A7C0), border = c(0xFF44475A), danger = c(0xFFFF5555),
        ),
        AtlasTheme(
            "sand", "Sand", false,
            background = c(0xFFF8F1E5), surface = c(0xFFF8F1E5), card = c(0xFFEFE3CF),
            primary = c(0xFF9C6B30), secondary = c(0xFF5E8C61), accent = c(0xFF3F6E8C),
            text = c(0xFF2A2118), mutedText = c(0xFF7A6A58), border = c(0xFFD9C8AE), danger = c(0xFFB3261E),
        ),
    )

    fun find(id: String): AtlasTheme? = all.firstOrNull { it.id == id }
}
