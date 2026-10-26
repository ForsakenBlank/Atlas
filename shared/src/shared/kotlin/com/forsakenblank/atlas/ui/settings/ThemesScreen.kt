@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.forsakenblank.atlas.ui.settings

import com.forsakenblank.atlas.platform.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.ThemeMode
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.CustomColorDialog
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.theme.AtlasTheme
import com.forsakenblank.atlas.ui.theme.BuiltInThemes
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.ui.theme.ThemeSlot
import com.forsakenblank.atlas.ui.theme.allThemes
import com.forsakenblank.atlas.ui.theme.contrastRatio
import com.forsakenblank.atlas.ui.theme.get
import com.forsakenblank.atlas.ui.theme.hex
import com.forsakenblank.atlas.ui.theme.isDarkMode
import com.forsakenblank.atlas.ui.theme.themeFor
import com.forsakenblank.atlas.ui.theme.toColorScheme
import com.forsakenblank.atlas.ui.theme.with
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Base64
import kotlin.math.roundToInt
import kotlin.random.Random
import com.forsakenblank.atlas.platform.platform

private const val THEME_CODE_PREFIX = "atlas-theme:"
private val TileWidth = 84.dp
private val TileHeight = 104.dp
private val Opaque = 0xFF000000.toInt()

private val themeJson = Json { ignoreUnknownKeys = true }

// keeps the editor draft through rotation
private val themeSaver = Saver<AtlasTheme, String>(
    save = { themeJson.encodeToString(AtlasTheme.serializer(), it) },
    restore = { themeJson.decodeFromString(AtlasTheme.serializer(), it) },
)

// light themes fill the light slot and dark themes the dark one, and a fixed mode flips so the change shows
private fun AppSettings.withTheme(theme: AtlasTheme): AppSettings {
    val picked = if (theme.dark) copy(darkThemeId = theme.id) else copy(lightThemeId = theme.id)
    return when {
        themeMode == ThemeMode.LIGHT && theme.dark -> picked.copy(themeMode = ThemeMode.DARK)
        themeMode == ThemeMode.DARK && !theme.dark -> picked.copy(themeMode = ThemeMode.LIGHT)
        else -> picked
    }
}

@Composable
private fun rememberThemeApplier(): (AtlasTheme) -> Unit {
    val app = atlasApp()
    val settings = LocalSettings.current
    val showingDark = settings.isDarkMode()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val buzz = rememberHaptic()
    return { theme ->
        buzz()
        app.updateSettings { it.withTheme(theme) }
        // when following the phone, the other kind of theme only shows once the phone switches
        if (settings.themeMode == ThemeMode.SYSTEM && theme.dark != showingDark) {
            val mode = if (theme.dark) "dark" else "light"
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("${theme.name} will show when your phone is in $mode mode")
            }
        }
    }
}

@Composable
private fun wallpaperColoursOn(settings: AppSettings): Boolean =
    settings.dynamicColor && platform().hasWallpaperColours

private fun uniqueThemeName(base: String, settings: AppSettings): String {
    val taken = settings.allThemes().map { it.name.lowercase() }.toSet()
    if (base.lowercase() !in taken) return base
    var n = 2
    while ("$base $n".lowercase() in taken) n++
    return "$base $n"
}

private fun AtlasTheme.toCode(): String {
    val json = themeJson.encodeToString(AtlasTheme.serializer(), this)
    return THEME_CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))
}

private fun isCodeChar(c: Char): Boolean =
    c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' || c == '='

private fun decodeThemeBody(body: String): AtlasTheme? {
    if (body.isEmpty()) return null
    return runCatching {
        val json = Base64.getUrlDecoder().decode(body).toString(Charsets.UTF_8)
        themeJson.decodeFromString(AtlasTheme.serializer(), json)
    }.getOrNull()
}

private fun parseThemeCode(text: String): AtlasTheme? {
    val start = text.indexOf(THEME_CODE_PREFIX)
    if (start < 0) return null
    val rest = text.substring(start + THEME_CODE_PREFIX.length)
    // chat apps sometimes wrap long codes over several lines, so try again without the line breaks
    val theme = listOf(
        rest.trimStart().takeWhile(::isCodeChar),
        rest.filterNot { it.isWhitespace() }.takeWhile(::isCodeChar),
    ).firstNotNullOfOrNull { decodeThemeBody(it) } ?: return null
    return ThemeSlot.entries.fold(theme) { t, slot -> t.with(slot, t[slot] or Opaque) }
}

private fun Int.toHsl(): FloatArray {
    val r = ((this shr 16) and 0xFF) / 255f
    val g = ((this shr 8) and 0xFF) / 255f
    val b = (this and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val lightness = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, lightness)
    val d = max - min
    val saturation = if (lightness > 0.5f) d / (2f - max - min) else d / (max + min)
    val hue = when (max) {
        r -> (g - b) / d + (if (g < b) 6f else 0f)
        g -> (b - r) / d + 2f
        else -> (r - g) / d + 4f
    } * 60f
    return floatArrayOf(hue, saturation, lightness)
}

private fun hslColor(hue: Float, saturation: Float, lightness: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    return Color.hsl(h, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f)).toArgb()
}

// nudges a colour lighter or darker until it stands out enough from the background
private fun readable(color: Int, background: Int, min: Float): Int {
    val bg = Color(background)
    if (contrastRatio(Color(color), bg) >= min) return color
    val towardsWhite = contrastRatio(Color.White, bg) >= contrastRatio(Color.Black, bg)
    val parts = color.toHsl()
    var lightness = parts[2]
    repeat(40) {
        lightness = (lightness + if (towardsWhite) 0.025f else -0.025f).coerceIn(0f, 1f)
        val next = hslColor(parts[0], parts[1], lightness)
        if (contrastRatio(Color(next), bg) >= min) return next
    }
    return if (towardsWhite) Color.White.toArgb() else Color.Black.toArgb()
}

// a fresh palette built around one random hue, light or dark to match the theme
private fun AtlasTheme.shuffled(): AtlasTheme {
    val hue = Random.nextFloat() * 360f
    val vivid = 0.55f + Random.nextFloat() * 0.3f
    val tint = 0.08f + Random.nextFloat() * 0.22f
    return if (dark) {
        val background = hslColor(hue, tint, 0.08f)
        val card = hslColor(hue, tint, 0.14f)
        val text = hslColor(hue, 0.18f, 0.93f)
        copy(
            background = background,
            surface = background,
            card = card,
            primary = readable(hslColor(hue, vivid, 0.68f), background, 3f),
            secondary = readable(hslColor(hue + 150f, vivid * 0.8f, 0.7f), background, 3f),
            accent = readable(hslColor(hue + 210f, vivid * 0.8f, 0.72f), background, 3f),
            text = text,
            mutedText = readable(hslColor(hue, 0.12f, 0.68f), background, 4.5f),
            border = lerp(Color(card), Color(text), 0.2f).toArgb(),
            danger = 0xFFFF8A80.toInt(),
            success = 0xFF81C784.toInt(),
            warning = 0xFFFFCA28.toInt(),
        )
    } else {
        val background = hslColor(hue, tint + 0.1f, 0.97f)
        val card = hslColor(hue, tint + 0.1f, 0.92f)
        val text = hslColor(hue, 0.25f, 0.12f)
        copy(
            background = background,
            surface = background,
            card = card,
            primary = readable(hslColor(hue, vivid, 0.45f), background, 3f),
            secondary = readable(hslColor(hue + 150f, vivid * 0.8f, 0.4f), background, 3f),
            accent = readable(hslColor(hue + 210f, vivid * 0.8f, 0.45f), background, 3f),
            text = text,
            mutedText = readable(hslColor(hue, 0.12f, 0.42f), background, 4.5f),
            border = lerp(Color(card), Color(text), 0.18f).toArgb(),
            danger = 0xFFC62828.toInt(),
            success = 0xFF2E7D32.toInt(),
            warning = 0xFFF59E0B.toInt(),
        )
    }
}

// turns a light palette dark or the other way round, keeping every hue
private fun AtlasTheme.flipped(toDark: Boolean): AtlasTheme {
    fun invert(c: Int): Int {
        val p = c.toHsl()
        return hslColor(p[0], p[1], 1f - p[2])
    }
    fun brighten(c: Int): Int {
        val p = c.toHsl()
        return hslColor(p[0], p[1], if (toDark) maxOf(p[2], 0.65f) else minOf(p[2], 0.45f))
    }
    val newBackground = invert(background)
    return copy(
        dark = toDark,
        background = newBackground,
        surface = invert(surface),
        card = invert(card),
        text = invert(text),
        mutedText = invert(mutedText),
        border = invert(border),
        primary = readable(brighten(primary), newBackground, 3f),
        secondary = brighten(secondary),
        accent = brighten(accent),
        danger = brighten(danger),
        success = brighten(success),
        warning = brighten(warning),
    )
}

private fun startingDraft(themeId: String, settings: AppSettings, showingDark: Boolean): AtlasTheme {
    settings.customThemes.firstOrNull { it.id == themeId }?.let { return it }
    val newId = "custom-" + System.currentTimeMillis()
    val builtIn = BuiltInThemes.find(themeId)
    return if (builtIn != null) {
        builtIn.copy(id = newId, name = uniqueThemeName("${builtIn.name} copy", settings))
    } else {
        settings.themeFor(showingDark).copy(id = newId, name = uniqueThemeName("My theme", settings))
    }
}

private fun ratioText(ratio: Float): String = "${(ratio * 10).roundToInt() / 10.0}"

@Composable
fun ThemeStrip(navigator: AtlasNavigator) {
    val settings = LocalSettings.current
    val useTheme = rememberThemeApplier()
    val showingDark = settings.isDarkMode()
    val light = settings.themeFor(false)
    val dark = settings.themeFor(true)
    // your own themes come first, they are the ones you are most likely after
    val themes = remember(settings.customThemes) { (settings.customThemes + BuiltInThemes.all).distinctBy { it.id } }
    val showingId = if (showingDark) dark.id else light.id
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = themes.indexOfFirst { it.id == showingId }.coerceAtLeast(0),
    )
    val summary = when (settings.themeMode) {
        ThemeMode.SYSTEM -> "${light.name} in light mode, ${dark.name} in dark mode"
        ThemeMode.LIGHT -> "Always light, using ${light.name}"
        ThemeMode.DARK -> "Always dark, using ${dark.name}"
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SectionTitle("Theme", Modifier.padding(horizontal = 16.dp))
        Text(
            summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (wallpaperColoursOn(settings)) {
            WallpaperBanner(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "create") {
                StripActionTile(Icons.Outlined.Add, "Create") { navigator.openThemeEditor("new") }
            }
            items(themes, key = { it.id }) { theme ->
                ThemeTile(
                    theme = theme,
                    inUse = theme.id == light.id || theme.id == dark.id,
                    pureBlack = settings.pureBlack,
                    onClick = { useTheme(theme) },
                    onLongClick = { navigator.openThemeEditor(theme.id) },
                )
            }
            item(key = "see-all") {
                StripActionTile(Icons.AutoMirrored.Filled.KeyboardArrowRight, "See all") { navigator.openThemes() }
            }
        }
    }
}

@Composable
fun ThemesScreen(navigator: AtlasNavigator) {
    val app = atlasApp()
    val settings = LocalSettings.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val useTheme = rememberThemeApplier()
    val showingDark = settings.isDarkMode()
    val light = settings.themeFor(false)
    val dark = settings.themeFor(true)
    val custom = remember(settings.customThemes) { settings.customThemes.distinctBy { it.id } }
    val builtInGroups = remember {
        listOf(
            "Light" to BuiltInThemes.all.filter { !it.dark },
            "Dark" to BuiltInThemes.all.filter { it.dark },
        )
    }

    fun importFromClipboard() {
        val text = runCatching { clipboard.getText()?.text }.getOrNull().orEmpty()
        val parsed = parseThemeCode(text)
        if (parsed == null) {
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar("That is not a theme code")
            }
            return
        }
        val imported = parsed.copy(
            id = "custom-" + System.currentTimeMillis(),
            name = uniqueThemeName(parsed.name.trim().ifBlank { "Imported theme" }, settings),
        )
        app.updateSettings { it.copy(customThemes = it.customThemes + imported) }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Imported ${imported.name}", actionLabel = "Use it", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) useTheme(imported)
        }
    }

    val card: @Composable (AtlasTheme) -> Unit = { theme ->
        ThemeCard(
            theme = theme,
            inUse = theme.id == light.id || theme.id == dark.id,
            pureBlack = settings.pureBlack,
            onClick = { useTheme(theme) },
            onEdit = { navigator.openThemeEditor(theme.id) },
        )
    }

    val showWallpaper = wallpaperColoursOn(settings)
    PageScaffold(
        title = "Themes",
        onBack = navigator::back,
        actions = {
            TextButton(onClick = { importFromClipboard() }) {
                Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Import")
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.openThemeEditor("new") },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New theme") },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showWallpaper) {
                fullWidth("wallpaper") { WallpaperBanner() }
            }
            fullWidth("mode") {
                ThemeModeChooser(settings.themeMode) { mode -> app.updateSettings { it.copy(themeMode = mode) } }
            }
            fullWidth("in-use") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InUseCard("Light theme", light, showing = !showingDark, pureBlack = settings.pureBlack, modifier = Modifier.weight(1f))
                    InUseCard("Dark theme", dark, showing = showingDark, pureBlack = settings.pureBlack, modifier = Modifier.weight(1f))
                }
            }
            fullWidth("hint") {
                Text(
                    "Tap a theme to use it. Light and dark themes are picked separately. Hold a theme or tap its pencil to change the colours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fullWidth("title-custom") { SectionTitle("Your themes") }
            if (custom.isEmpty()) {
                fullWidth("custom-empty") { CreateThemeCard { navigator.openThemeEditor("new") } }
            } else {
                items(custom, key = { "custom/${it.id}" }) { theme -> card(theme) }
            }
            builtInGroups.forEach { (title, themes) ->
                fullWidth("title-$title") { SectionTitle(title) }
                items(themes, key = { "built-in/${it.id}" }) { theme -> card(theme) }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
fun ThemeEditorScreen(themeId: String, navigator: AtlasNavigator) {
    val app = atlasApp()
    val settings = LocalSettings.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val showingDark = settings.isDarkMode()

    val start by rememberSaveable(themeId, stateSaver = themeSaver) {
        mutableStateOf(startingDraft(themeId, settings, showingDark))
    }
    var draft by rememberSaveable(themeId, stateSaver = themeSaver) { mutableStateOf(start) }
    val history = remember(themeId) { mutableStateListOf<AtlasTheme>() }
    var editingSlot by remember { mutableStateOf<ThemeSlot?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // a new theme or a copy gets a fresh id, so a matching id means an existing theme of yours
    val editingExisting = start.id == themeId
    val changed = draft != start

    fun change(next: AtlasTheme) {
        if (next == draft) return
        history.add(draft)
        if (history.size > 50) history.removeAt(0)
        draft = next
    }

    fun undo() {
        if (history.isEmpty()) return
        // undo is for colours, so the name you typed stays
        draft = history.removeAt(history.lastIndex).copy(name = draft.name)
    }

    fun setDark(wantDark: Boolean) {
        if (wantDark == draft.dark) return
        val looksDark = Color(draft.background).luminance() < 0.18f
        change(if (looksDark == wantDark) draft.copy(dark = wantDark) else draft.flipped(wantDark))
    }

    fun fixContrast() {
        change(
            draft.copy(
                text = readable(draft.text, draft.background, 4.5f),
                primary = readable(draft.primary, draft.background, 3f),
            )
        )
    }

    fun finalDraft(): AtlasTheme = draft.copy(name = draft.name.trim().ifBlank { "My theme" })

    fun copyCode() {
        clipboard.setText(AnnotatedString(finalDraft().toCode()))
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar("Theme code copied, paste it anywhere to share")
        }
    }

    fun save() {
        val saved = finalDraft()
        app.updateSettings { s ->
            val themes = if (s.customThemes.any { it.id == saved.id }) {
                s.customThemes.map { if (it.id == saved.id) saved else it }
            } else {
                s.customThemes + saved
            }
            var next = s.copy(customThemes = themes)
            // a theme that changed between light and dark leaves the slot it no longer fits
            if (saved.dark && next.lightThemeId == saved.id) next = next.copy(lightThemeId = BuiltInThemes.DEFAULT_LIGHT)
            if (!saved.dark && next.darkThemeId == saved.id) next = next.copy(darkThemeId = BuiltInThemes.DEFAULT_DARK)
            next.withTheme(saved)
        }
        val message = if (settings.themeMode == ThemeMode.SYSTEM && saved.dark != showingDark) {
            "Saved ${saved.name}, it shows when your phone is in ${if (saved.dark) "dark" else "light"} mode"
        } else {
            "Saved ${saved.name}"
        }
        // the app scope keeps the snackbar alive after this page closes
        app.appScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
        navigator.back()
    }

    fun delete() {
        val id = draft.id
        val removed = settings.customThemes.firstOrNull { it.id == id }
        val wasLight = settings.lightThemeId == id
        val wasDark = settings.darkThemeId == id
        app.updateSettings { s ->
            s.copy(
                customThemes = s.customThemes.filterNot { it.id == id },
                lightThemeId = if (s.lightThemeId == id) BuiltInThemes.DEFAULT_LIGHT else s.lightThemeId,
                darkThemeId = if (s.darkThemeId == id) BuiltInThemes.DEFAULT_DARK else s.darkThemeId,
            )
        }
        navigator.back()
        if (removed != null) {
            app.appScope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar("Deleted ${removed.name}", actionLabel = "Undo", duration = SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) {
                    app.updateSettings { s ->
                        if (s.customThemes.any { it.id == removed.id }) {
                            s
                        } else {
                            s.copy(
                                customThemes = s.customThemes + removed,
                                lightThemeId = if (wasLight) removed.id else s.lightThemeId,
                                darkThemeId = if (wasDark) removed.id else s.darkThemeId,
                            )
                        }
                    }
                }
            }
        }
    }

    fun leave() {
        if (changed) confirmDiscard = true else navigator.back()
    }

    BackHandler(enabled = changed) { confirmDiscard = true }

    val textRatio = contrastRatio(Color(draft.text), Color(draft.background))
    val primaryRatio = contrastRatio(Color(draft.primary), Color(draft.background))
    val contrastProblem = when {
        textRatio < 4.5f && primaryRatio < 3f -> "Text and primary colours are both hard to read on the background."
        textRatio < 4.5f -> "Text is hard to read on the background. It is ${ratioText(textRatio)} to 1, aim for 4.5 or more."
        primaryRatio < 3f -> "Primary is hard to see on the background. It is ${ratioText(primaryRatio)} to 1, aim for 3 or more."
        else -> null
    }

    PageScaffold(
        title = if (editingExisting) "Edit theme" else "New theme",
        onBack = { leave() },
        actions = {
            IconButton(onClick = { undo() }, enabled = history.isNotEmpty()) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = { save() }) {
                Icon(Icons.Outlined.Check, contentDescription = "Save")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeLivePreview(draft, settings.pureBlack, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(onClick = { change(draft.shuffled()) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Shuffle, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Shuffle")
                }
                OutlinedButton(onClick = { copyCode() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Copy code")
                }
            }

            if (contrastProblem != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(Icons.Outlined.Warning, contentDescription = null)
                        Text(contrastProblem, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { fixContrast() }) { Text("Fix") }
                    }
                }
            }

            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it.take(40)) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            Column {
                ListItem(
                    headlineContent = { Text("Dark theme") },
                    supportingContent = {
                        Text(if (draft.dark) "Shows when Atlas is in dark mode" else "Shows when Atlas is in light mode")
                    },
                    trailingContent = { Switch(checked = draft.dark, onCheckedChange = { setDark(it) }) },
                    modifier = Modifier.clickable { setDark(!draft.dark) },
                )
                SectionTitle("Colours", Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
                Text(
                    "Tap a colour to change it. The preview at the top updates as you go.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                ThemeSlot.entries.forEach { slot ->
                    SlotRow(slot, draft[slot]) { editingSlot = slot }
                }
            }

            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text("Save theme")
            }
            if (editingExisting) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Delete theme")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    editingSlot?.let { slot ->
        key(slot) {
            CustomColorDialog(
                initial = draft[slot],
                title = slot.label,
                onDismiss = { editingSlot = null },
                onConfirm = { picked ->
                    change(draft.with(slot, picked))
                    editingSlot = null
                },
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to ${draft.name.ifBlank { "this theme" }} will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    navigator.back()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${start.name}?") },
            text = { Text("The theme is removed. If you were using it, Atlas goes back to its own theme.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ThemeModeChooser(mode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    val options = listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                // no tick, the long label needs the room
                icon = {},
            ) {
                Text(label, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun InUseCard(label: String, theme: AtlasTheme, showing: Boolean, pureBlack: Boolean, modifier: Modifier = Modifier) {
    AtlasCard(modifier = modifier) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniThemePreview(
                theme,
                pureBlack,
                Modifier
                    .size(width = 36.dp, height = 48.dp)
                    .clip(MaterialTheme.shapes.small),
                line = 3.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(theme.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showing) {
                    Text("Showing now", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun CreateThemeCard(onClick: () -> Unit) {
    AtlasCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                Icons.Outlined.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Column(Modifier.weight(1f)) {
                Text("Make your own", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Start from the theme you are using and pick every colour yourself, or tap Shuffle for a surprise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WallpaperBanner(modifier: Modifier = Modifier) {
    val app = atlasApp()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Palette, contentDescription = null)
            Text(
                "Wallpaper colours are on, so themes will not show.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { app.updateSettings { it.copy(dynamicColor = false) } }) { Text("Turn off") }
        }
    }
}

@Composable
private fun ThemeCard(theme: AtlasTheme, inUse: Boolean, pureBlack: Boolean, onClick: () -> Unit, onEdit: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = if (inUse) 2.dp else 1.dp,
                color = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .combinedClickable(onClick = onClick, onLongClick = onEdit, onLongClickLabel = "Edit"),
    ) {
        Box {
            MiniThemePreview(theme, pureBlack, Modifier.fillMaxWidth().height(112.dp), line = 6.dp)
            if (inUse) CheckBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(theme.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        inUse -> "In use"
                        theme.dark -> "Dark"
                        else -> "Light"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Outlined.Edit, contentDescription = "Edit ${theme.name}", modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ThemeTile(theme: AtlasTheme, inUse: Boolean, pureBlack: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(Modifier.width(TileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TileHeight)
                .clip(shape)
                .border(
                    width = if (inUse) 2.dp else 1.dp,
                    color = if (inUse) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Edit"),
        ) {
            MiniThemePreview(theme, pureBlack, Modifier.fillMaxSize())
            if (inUse) CheckBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            theme.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun StripActionTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(Modifier.width(TileWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TileHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Check,
            contentDescription = "In use",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp),
        )
    }
}

// a tiny drawing of a screen in the theme's own colours
@Composable
private fun MiniThemePreview(theme: AtlasTheme, pureBlack: Boolean, modifier: Modifier = Modifier, line: Dp = 5.dp) {
    val scheme = remember(theme, pureBlack) { theme.toColorScheme(pureBlack) }
    Column(
        modifier
            .background(scheme.background)
            .padding(line * 1.6f),
        verticalArrangement = Arrangement.spacedBy(line),
    ) {
        PreviewLine(scheme.onBackground, 0.45f, line)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(line * 1.2f))
                .background(scheme.surfaceContainer)
                .padding(line * 1.2f),
            verticalArrangement = Arrangement.spacedBy(line * 0.8f),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { PreviewLine(scheme.onSurface, 0.85f, line) }
                Box(
                    Modifier
                        .size(line * 1.4f)
                        .clip(CircleShape)
                        .background(scheme.tertiary)
                )
            }
            PreviewLine(scheme.onSurfaceVariant, 0.6f, line)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(line * 0.8f)) {
            Box(
                Modifier
                    .weight(2f)
                    .height(line * 2)
                    .clip(CircleShape)
                    .background(scheme.primary)
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(line * 2)
                    .clip(CircleShape)
                    .background(scheme.secondary)
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewLine(color: Color, fraction: Float, line: Dp) {
    Box(
        Modifier
            .fillMaxWidth(fraction)
            .height(line)
            .clip(CircleShape)
            .background(color)
    )
}

// real material parts drawn in the draft colours, so what you see is what the app will look like
@Composable
private fun ThemeLivePreview(theme: AtlasTheme, pureBlack: Boolean, modifier: Modifier = Modifier) {
    val scheme = remember(theme, pureBlack) { theme.toColorScheme(pureBlack) }
    var switchOn by remember { mutableStateOf(true) }
    var pinnedOn by remember { mutableStateOf(true) }
    MaterialTheme(colorScheme = scheme) {
        val colors = MaterialTheme.colorScheme
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.background,
            contentColor = colors.onBackground,
            border = BorderStroke(1.dp, colors.outlineVariant),
        ) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Outlined.Menu, contentDescription = null, tint = colors.onSurface)
                    Text(
                        theme.name.ifBlank { "My theme" },
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.onSurfaceVariant)
                }
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Morning pages", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(colors.tertiary)
                                )
                            }
                            Text(
                                "Three pages before breakfast. Easier than yesterday, and the tea was good.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {}) { Text("Save") }
                        OutlinedButton(onClick = {}) { Text("Cancel") }
                        Spacer(Modifier.weight(1f))
                        Switch(checked = switchOn, onCheckedChange = { switchOn = it })
                    }
                    val pinnedIcon: (@Composable () -> Unit)? = if (pinnedOn) {
                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = pinnedOn,
                            onClick = { pinnedOn = !pinnedOn },
                            label = { Text("Pinned") },
                            leadingIcon = pinnedIcon,
                        )
                        FilterChip(
                            selected = !pinnedOn,
                            onClick = { pinnedOn = !pinnedOn },
                            label = { Text("Recent") },
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text("Reading goal", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            Text("62%", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                        }
                        LinearProgressIndicator(progress = { 0.62f }, modifier = Modifier.fillMaxWidth())
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(Color(theme.success), "Goal hit")
                        StatusDot(Color(theme.warning), "Streak at risk")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
                        Text("Could not sync, try again", style = MaterialTheme.typography.bodyMedium, color = colors.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SlotRow(slot: ThemeSlot, color: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(slot.label) },
        supportingContent = { Text(slot.hint.replaceFirstChar { it.uppercase() }) },
        leadingContent = {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            )
        },
        trailingContent = {
            Text(Color(color).hex(), style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

