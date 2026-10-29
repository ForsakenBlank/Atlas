package com.forsakenblank.atlas.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.ATLAS_VERSION
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.CardStyle
import com.forsakenblank.atlas.data.ThemeMode
import com.forsakenblank.atlas.data.Transition
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.platform.platform
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, navigator: AtlasNavigator, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
fun SettingsScreen(navigator: AtlasNavigator) {
    val app = atlasApp()
    val settings = LocalSettings.current
    var query by rememberSaveable { mutableStateOf("") }
    val onAction = rememberSettingsActions(navigator)
    val allSettings = visibleSettings()
    val matches = remember(query, allSettings) { if (query.isBlank()) emptyList() else allSettings.filter { it.matches(query) } }

    SettingsScaffold("Settings", navigator) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search ${allSettings.size} settings") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear") }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (query.isNotBlank()) {
                if (matches.isEmpty()) {
                    item {
                        Text(
                            "Nothing matches \"$query\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                items(matches, key = { "${it.category}-${it.title}" }) { setting ->
                    SettingRow(setting, settings, app::updateSettings, onAction, showCategory = true)
                }
            } else {
                items(SettingsCategory.entries, key = { it.name }) { category ->
                    val count = allSettings.count { it.category == category }
                    ListItem(
                        headlineContent = { Text(category.title) },
                        supportingContent = { Text(category.blurb) },
                        leadingContent = { Icon(category.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (count > 0) Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable { navigator.openSettingsCategory(category) },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCategoryScreen(categoryName: String, navigator: AtlasNavigator) {
    val app = atlasApp()
    val settings = LocalSettings.current
    val category = SettingsCategory.entries.firstOrNull { it.name == categoryName } ?: SettingsCategory.APPEARANCE
    val onAction = rememberSettingsActions(navigator)
    val allSettings = visibleSettings()
    val entries = remember(category, allSettings) { allSettings.filter { it.category == category } }

    SettingsScaffold(category.title, navigator) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (category == SettingsCategory.APPEARANCE) {
                item { ThemeStrip(navigator) }
            }
            if (category == SettingsCategory.REMINDERS) {
                item { NotificationsBlockedRow(onOpenSettings = { onAction(SettingsAction.NOTIFICATION_SETTINGS) }) }
            }
            items(entries, key = { it.title }) { setting ->
                SettingRow(setting, settings, app::updateSettings, onAction, showCategory = false)
            }
            if (category == SettingsCategory.ABOUT) {
                item { AboutBlock() }
            }
        }
    }
}

@Composable
private fun AboutBlock() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Atlas ${ATLAS_VERSION}", style = MaterialTheme.typography.titleLarge)
        Text(
            "One app for notes, tracking, planning and everything in between. Everything stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("New in this version", style = MaterialTheme.typography.titleMedium)
        listOf(
            "Sheets: spreadsheets with formulas like =SUM(A1:A5)",
            "Countdowns for birthdays, anniversaries and big dates, shown on Home",
            "Reminders for birthdays, tasks due today and upcoming events",
            "Search everything from the top bar",
            "Hold to select shortcuts, trackers and notes, then remove them in one go",
            "Swipe a task right to tick it off or left to delete it",
            "Repeating tasks and yearly events show on every date they repeat",
            "Add events or tasks straight from any day in the calendar",
            "Share or copy a note",
        ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
fun SettingRow(
    setting: Setting,
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    onAction: (SettingsAction) -> Unit,
    showCategory: Boolean,
) {
    val overline: (@Composable () -> Unit)? = if (showCategory) {
        { Text(setting.category.title) }
    } else {
        null
    }
    val summary: (@Composable () -> Unit)? = if (setting.summary.isNotBlank()) {
        { Text(setting.summary) }
    } else {
        null
    }
    when (setting) {
        is Setting.Toggle -> {
            val on = setting.get(settings)
            ListItem(
                overlineContent = overline,
                headlineContent = { Text(setting.title) },
                supportingContent = summary,
                trailingContent = { Switch(checked = on, onCheckedChange = { v -> update { setting.set(it, v) } }) },
                modifier = Modifier.clickable { update { setting.set(it, !on) } },
            )
        }
        is Setting.Choice<*> -> {
            @Suppress("UNCHECKED_CAST")
            ChoiceRow(setting as Setting.Choice<Any?>, settings, update, overline)
        }
        is Setting.Range -> RangeRow(setting, settings, update, overline)
        is Setting.TextValue -> {
            var editing by remember { mutableStateOf(false) }
            val value = setting.get(settings)
            ListItem(
                overlineContent = overline,
                headlineContent = { Text(setting.title) },
                supportingContent = { Text(value.ifBlank { setting.summary.ifBlank { "Not set" } }) },
                modifier = Modifier.clickable { editing = true },
            )
            if (editing) {
                var text by remember { mutableStateOf(value) }
                AlertDialog(
                    onDismissRequest = { editing = false },
                    title = { Text(setting.title) },
                    text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
                    confirmButton = {
                        TextButton(onClick = {
                            update { setting.set(it, text.trim()) }
                            editing = false
                        }) { Text("Save") }
                    },
                    dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } },
                )
            }
        }
        is Setting.Action -> ListItem(
            overlineContent = overline,
            headlineContent = { Text(setting.title, color = if (setting.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
            supportingContent = summary,
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable { onAction(setting.action) },
        )
    }
}

@Composable
private fun ChoiceRow(
    setting: Setting.Choice<Any?>,
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    overline: (@Composable () -> Unit)?,
) {
    var open by remember { mutableStateOf(false) }
    val current = setting.get(settings)
    ListItem(
        overlineContent = overline,
        headlineContent = { Text(setting.title) },
        supportingContent = {
            Column {
                Text(setting.labelWith(settings, current), color = MaterialTheme.colorScheme.primary)
                if (setting.summary.isNotBlank()) Text(setting.summary)
            }
        },
        modifier = Modifier.clickable { open = true },
    )
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(setting.title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    setting.options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = option == current, role = Role.RadioButton) {
                                    update { setting.set(it, option) }
                                    open = false
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(selected = option == current, onClick = null)
                            Text(setting.labelWith(settings, option), modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun RangeRow(
    setting: Setting.Range,
    settings: AppSettings,
    update: ((AppSettings) -> AppSettings) -> Unit,
    overline: (@Composable () -> Unit)?,
) {
    val saved = setting.get(settings)
    // the slider moves locally and only saves when you let go
    var value by remember(saved) { mutableFloatStateOf(saved) }
    ListItem(
        overlineContent = overline,
        headlineContent = {
            Row {
                Text(setting.title, modifier = Modifier.weight(1f))
                Text(setting.format(value), color = MaterialTheme.colorScheme.primary)
            }
        },
        supportingContent = {
            Column {
                if (setting.summary.isNotBlank()) Text(setting.summary)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    onValueChangeFinished = { update { setting.set(it, value) } },
                    valueRange = setting.min..setting.max,
                    steps = setting.steps,
                )
            }
        },
        colors = ListItemDefaults.colors(),
    )
}

@Composable
fun rememberSettingsActions(navigator: AtlasNavigator): (SettingsAction) -> Unit {
    val app = atlasApp()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val platform = platform()
    val testReminder = rememberTestReminder()
    var dialog by remember { mutableStateOf<SettingsAction?>(null) }

    val exportLauncher = platform.rememberSaveFile("application/json") { writer ->
        if (writer != null) {
            scope.launch {
                val message = runCatching { app.backups.exportTo(writer) }.fold({ "Backup saved" }, { "Backup failed: ${it.message}" })
                snackbar.showSnackbar(message)
            }
        }
    }
    val importLauncher = platform.rememberOpenFile { reader ->
        if (reader != null) {
            scope.launch {
                val message = runCatching { app.backups.importFrom(reader) }.fold({ "Restored $it items" }, { "Restore failed: ${it.message}" })
                snackbar.showSnackbar(message)
            }
        }
    }

    when (dialog) {
        SettingsAction.EDIT_TABS -> TabsDialog(onDismiss = { dialog = null })
        SettingsAction.PICK_FOCUS_TRACKER -> FocusTrackerDialog(onDismiss = { dialog = null })
        SettingsAction.RESTORE -> ConfirmDialog(
            title = "Restore a backup?",
            body = "Everything currently in Atlas will be replaced by what is in the backup file.",
            button = "Choose file",
            onDismiss = { dialog = null },
        ) { importLauncher() }
        SettingsAction.EMPTY_TRASH -> ConfirmDialog(
            title = "Empty the trash?",
            body = "Everything in the trash is deleted for good.",
            button = "Empty trash",
            onDismiss = { dialog = null },
        ) {
            scope.launch {
                app.repository.emptyTrash()
                snackbar.showSnackbar("Trash emptied")
            }
        }
        SettingsAction.CLEAR_DONE_TASKS -> ConfirmDialog(
            title = "Clear finished tasks?",
            body = "Every ticked task is deleted.",
            button = "Clear",
            onDismiss = { dialog = null },
        ) {
            scope.launch {
                app.repository.clearDoneTasks()
                snackbar.showSnackbar("Finished tasks cleared")
            }
        }
        SettingsAction.RESET_SETTINGS -> ConfirmDialog(
            title = "Reset all settings?",
            body = "Every setting goes back to its default. Your notes, trackers and your own themes stay.",
            button = "Reset",
            onDismiss = { dialog = null },
        ) {
            scope.launch {
                app.settingsStore.reset(keepThemes = true)
                snackbar.showSnackbar("Settings reset")
            }
        }
        else -> Unit
    }

    return { action ->
        when (action) {
            SettingsAction.OPEN_THEMES -> navigator.openThemes()
            SettingsAction.NEW_THEME -> navigator.openThemeEditor("new")
            SettingsAction.BACKUP -> exportLauncher("atlas-backup-${LocalDate.now()}.json")
            SettingsAction.STARTER_PACKS -> navigator.openStarterPacks()
            SettingsAction.SUBJECTS -> navigator.openSubjects()
            SettingsAction.TERMS -> navigator.openTerms()
            SettingsAction.NOTIFICATION_SETTINGS -> platform.reminders.openSettings()
            SettingsAction.TEST_REMINDER -> testReminder()
            SettingsAction.SYNC -> navigator.openSync()
            SettingsAction.CAELESTIA_LOOK -> scope.launch {
                app.settingsStore.update {
                    it.copy(
                        darkThemeId = "caelestia", themeMode = ThemeMode.DARK, cardStyle = CardStyle.GLASS,
                        cornerRadius = 24, floatingBar = true, screenTransition = Transition.SHARED_AXIS,
                        tabTransition = Transition.FADE, boldHeadings = false,
                    )
                }
                snackbar.showSnackbar("Caelestia look on. Change any part of it below.")
            }
            else -> dialog = action
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, button: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(button) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FocusTrackerDialog(onDismiss: () -> Unit) {
    val app = atlasApp()
    val settings = LocalSettings.current
    val trackers by app.repository.trackers().collectAsState(initial = emptyList())
    val timers = trackers.filter { it.tracker.kind == TrackerKind.TIMER }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log focus time to") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val options = listOf<Pair<Long?, String>>(null to "Don't log") + timers.map { it.item.id to it.item.name }
                options.forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = settings.focusTrackerId == id, role = Role.RadioButton) {
                                app.updateSettings { it.copy(focusTrackerId = id) }
                                onDismiss()
                            }
                            .padding(vertical = 6.dp),
                    ) {
                        RadioButton(selected = settings.focusTrackerId == id, onClick = null)
                        Text(name, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                if (timers.isEmpty()) {
                    Text(
                        "Make a timer tracker in Track first, then pick it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp).width(260.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
