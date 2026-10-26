package com.forsakenblank.atlas.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.theme.LocalSettings
import java.util.Collections

private const val MIN_TABS = 2
private const val MAX_TABS = 5

@Composable
fun TabsDialog(onDismiss: () -> Unit) {
    val app = atlasApp()
    val settings = LocalSettings.current
    var chosen by remember { mutableStateOf(settings.tabs.distinct().take(MAX_TABS)) }
    val others = Section.entries.filter { it !in chosen }

    fun move(index: Int, by: Int) {
        val target = index + by
        if (target !in chosen.indices) return
        val list = chosen.toMutableList()
        Collections.swap(list, index, target)
        chosen = list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bottom bar tabs") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Pick $MIN_TABS to $MAX_TABS sections for the bottom bar and put them in the order you like.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TabsHeading("In the bar, ${chosen.size} of $MAX_TABS")
                chosen.forEachIndexed { index, section ->
                    TabsSectionRow(section) {
                        IconButton(onClick = { move(index, -1) }, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move ${section.label} up")
                        }
                        IconButton(onClick = { move(index, 1) }, enabled = index < chosen.lastIndex, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move ${section.label} down")
                        }
                        IconButton(
                            onClick = { chosen = chosen - section },
                            enabled = chosen.size > MIN_TABS,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Remove ${section.label}")
                        }
                    }
                }
                TabsHeading("Not in the bar")
                if (chosen.size >= MAX_TABS) {
                    Text(
                        "The bar is full. Remove a tab to add another.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                others.forEach { section ->
                    TabsSectionRow(section) {
                        IconButton(
                            onClick = { chosen = chosen + section },
                            enabled = chosen.size < MAX_TABS,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Add ${section.label}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tabs = chosen
                    // a section you add as a tab should not stay hidden
                    app.updateSettings { it.copy(tabs = tabs, hiddenSections = it.hiddenSections - tabs.toSet()) }
                    onDismiss()
                },
                enabled = chosen.size in MIN_TABS..MAX_TABS,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { chosen = Section.defaultTabs }) { Text("Reset") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun TabsHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun TabsSectionRow(section: Section, buttons: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(section.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                section.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        buttons()
    }
}
