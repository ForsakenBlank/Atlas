package com.forsakenblank.atlas.ui.onboarding

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.data.StarterPack
import com.forsakenblank.atlas.data.StarterPacks
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.launch

private fun StarterPack.contents(): String {
    val parts = buildList {
        if (trackers.isNotEmpty()) add(trackers.joinToString(", ") { it.name })
        if (notes.isNotEmpty()) add("${notes.size} note${if (notes.size == 1) "" else "s"}")
        if (tasks.isNotEmpty()) add("${tasks.size} task${if (tasks.size == 1) "" else "s"}")
    }
    return parts.joinToString(", plus ")
}

private fun TrackerKind.short(): String = when (this) {
    TrackerKind.COUNTER -> "count"
    TrackerKind.YES_NO -> "yes or no"
    TrackerKind.TIMER -> "timer"
    TrackerKind.NUMBER -> "number"
    TrackerKind.RATING -> "rating"
}

@Composable
private fun PackCard(pack: StarterPack, trailing: @Composable () -> Unit, onClick: (() -> Unit)? = null) {
    AtlasCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(Color(pack.color), CircleShape))
            Column(Modifier.padding(horizontal = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(pack.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(pack.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pack.trackers.isNotEmpty()) {
                    Text(
                        pack.trackers.joinToString(", ") { "${it.name} (${it.kind.short()})" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            trailing()
        }
    }
}

// first launch: pick some starter packs instead of getting built in shortcuts
@Composable
fun WelcomeScreen(onDone: () -> Unit) {
    val app = atlasApp()
    val settings = LocalSettings.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(settings.userName) }
    val chosen = remember { mutableStateListOf("welcome") }
    var oldExamples by remember { mutableStateOf<List<String>>(emptyList()) }
    var removeExamples by rememberSaveable { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        oldExamples = app.repository.unusedOldExamples().map { it.name }
    }

    fun finish(addPacks: Boolean) {
        if (working) return
        working = true
        scope.launch {
            if (addPacks) {
                StarterPacks.filter { it.id in chosen }.forEach { app.repository.addStarterPack(it) }
                if (oldExamples.isNotEmpty() && removeExamples) app.repository.removeOldExamples()
            }
            app.settingsStore.update { it.copy(onboarded = true, userName = if (addPacks) name.trim() else it.userName) }
            onDone()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text("Welcome to Atlas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Notes, trackers, a calendar, a timetable and a pile of handy tools, all in one place and all on this phone.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("What should Atlas call you? (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text("Pick some starter packs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Each one adds a folder of ready made trackers and notes. You can change or delete anything later, and add more packs from Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(StarterPacks, key = { it.id }) { pack ->
                val on = pack.id in chosen
                val toggle = { if (on) chosen.remove(pack.id) else chosen.add(pack.id) }
                PackCard(pack, trailing = { Checkbox(checked = on, onCheckedChange = { toggle() }) }, onClick = { toggle() })
            }
            if (oldExamples.isNotEmpty()) {
                item {
                    AtlasCard(modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Remove the old example shortcuts", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${oldExamples.joinToString(", ")} came with the first version and have never been used. They go to the trash.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = removeExamples, onCheckedChange = { removeExamples = it })
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { finish(addPacks = true) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                        Text(if (chosen.isEmpty()) "Start with a blank Atlas" else "Start with ${chosen.size} pack${if (chosen.size == 1) "" else "s"}")
                    }
                    TextButton(onClick = { finish(addPacks = false) }, enabled = !working, modifier = Modifier.fillMaxWidth()) {
                        Text("Skip for now")
                    }
                }
            }
        }
    }
}

@Composable
fun StarterPacksScreen(navigator: AtlasNavigator) {
    val app = atlasApp()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val added = remember { mutableStateListOf<String>() }

    PageScaffold(title = "Starter packs", onBack = navigator::back) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Each pack adds a new folder with its trackers and notes. Adding a pack twice makes a second copy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(StarterPacks, key = { it.id }) { pack ->
                val done = pack.id in added
                PackCard(
                    pack,
                    trailing = {
                        if (done) {
                            Icon(Icons.Outlined.Check, contentDescription = "Added", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            OutlinedButton(onClick = {
                                added.add(pack.id)
                                scope.launch {
                                    app.repository.addStarterPack(pack)
                                    snackbar.showSnackbar("Added ${pack.title}: ${pack.contents()}")
                                }
                            }) { Text("Add") }
                        }
                    },
                )
            }
        }
    }
}
