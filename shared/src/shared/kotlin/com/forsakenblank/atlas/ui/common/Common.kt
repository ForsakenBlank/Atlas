package com.forsakenblank.atlas.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forsakenblank.atlas.AtlasApp
import com.forsakenblank.atlas.LocalAtlas
import com.forsakenblank.atlas.data.CardStyle
import com.forsakenblank.atlas.data.ItemType
import com.forsakenblank.atlas.ui.theme.ItemColors
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.ui.theme.onColor

@Composable
inline fun <reified VM : ViewModel> atlasViewModel(crossinline create: (AtlasApp) -> VM): VM {
    val app = LocalAtlas.current
    return viewModel(factory = viewModelFactory { initializer { create(app) } })
}

@Composable
fun atlasApp(): AtlasApp = LocalAtlas.current

fun ItemType.icon(): ImageVector = when (this) {
    ItemType.FOLDER -> Icons.Outlined.Folder
    ItemType.NOTE -> Icons.Outlined.Description
    ItemType.TRACKER -> Icons.Outlined.Insights
    ItemType.SHEET -> Icons.Outlined.TableChart
}

fun Int?.toItemColor(fallback: Color): Color = if (this == null) fallback else Color(this)

// a tap that buzzes when haptics are switched on in settings
@Composable
fun rememberHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val enabled = LocalSettings.current.haptics
    return remember(haptics, enabled) { { if (enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

// card that follows the card style setting (filled, outlined or raised)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AtlasCard(
    modifier: Modifier = Modifier,
    color: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalSettings.current.cardStyle
    val shape = CardDefaults.shape
    val clicks = if (onClick != null || onLongClick != null) {
        Modifier.clip(shape).combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    } else {
        Modifier
    }
    val container = color ?: MaterialTheme.colorScheme.surfaceContainer
    when (style) {
        CardStyle.FILLED -> Card(
            modifier = modifier.then(clicks),
            colors = CardDefaults.cardColors(containerColor = container),
            content = content,
        )
        CardStyle.OUTLINED -> OutlinedCard(
            modifier = modifier.then(clicks),
            colors = CardDefaults.outlinedCardColors(containerColor = color ?: MaterialTheme.colorScheme.surface),
            content = content,
        )
        CardStyle.ELEVATED -> ElevatedCard(
            modifier = modifier.then(clicks),
            colors = CardDefaults.elevatedCardColors(containerColor = container),
            content = content,
        )
        // see-through like Caelestia's panels, with a thin light edge
        CardStyle.GLASS -> OutlinedCard(
            modifier = modifier.then(clicks),
            colors = CardDefaults.outlinedCardColors(containerColor = container.copy(alpha = 0.55f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            content = content,
        )
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "Name",
    confirm: String = "Save",
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorRow(selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier = Modifier, allowNone: Boolean = true) {
    var custom by remember { mutableStateOf(false) }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (allowNone) Swatch(color = null, selected = selected == null) { onSelect(null) }
        ItemColors.forEach { c -> Swatch(color = c, selected = selected == c) { onSelect(c) } }
        // a colour picked from the full picker shows up as its own swatch
        if (selected != null && selected !in ItemColors) Swatch(color = selected, selected = true) { custom = true }
        Box(
            modifier = Modifier
                .size(34.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                .clip(CircleShape)
                .clickable { custom = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Colorize, contentDescription = "Pick any colour", modifier = Modifier.size(18.dp))
        }
    }
    if (custom) {
        CustomColorDialog(
            initial = selected ?: MaterialTheme.colorScheme.primary.toArgb(),
            onDismiss = { custom = false },
            onConfirm = {
                onSelect(it)
                custom = false
            },
        )
    }
}

@Composable
private fun Swatch(color: Int?, selected: Boolean, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    val fill = color.toItemColor(MaterialTheme.colorScheme.surfaceVariant)
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(fill, CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else outline, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            color == null -> Icon(Icons.Outlined.Block, contentDescription = "No colour", modifier = Modifier.size(18.dp))
            selected -> Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = fill.onColor(), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ColorPickerDialog(initial: Int?, onDismiss: () -> Unit, onConfirm: (Int?) -> Unit) {
    var picked by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour") },
        text = { ColorRow(selected = picked, onSelect = { picked = it }) },
        confirmButton = { TextButton(onClick = { onConfirm(picked) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 4.dp),
    )
}

// the usual page with a back arrow, used by everything that opens on top of the tabs
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = actions,
            )
        },
        floatingActionButton = floatingActionButton,
        content = content,
    )
}
