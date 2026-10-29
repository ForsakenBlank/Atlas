package com.forsakenblank.atlas.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.ATLAS_VERSION
import com.forsakenblank.atlas.platform.platform
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.update.Release
import com.forsakenblank.atlas.update.Releases
import kotlinx.coroutines.launch

// settings -> about: check for updates, and every version with a button to update, reinstall or go back to it
@Composable
fun UpdatesBlock() {
    val updater = platform().updater
    val scope = rememberCoroutineScope()
    var releases by remember { mutableStateOf<List<Release>?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Release?>(null) }

    fun check() {
        busy = true
        status = "Asking GitHub…"
        scope.launch {
            runCatching { Releases.fetch() }
                .onSuccess { list ->
                    releases = list
                    val newest = list.firstOrNull()
                    status = when {
                        list.isEmpty() -> "No versions are published yet."
                        Releases.newer(newest!!.version, ATLAS_VERSION) -> "Atlas ${newest.version} is out. Your notes and everything else are kept."
                        else -> "You have the newest version."
                    }
                }
                .onFailure { status = "Could not reach GitHub: ${it.message}" }
            busy = false
        }
    }

    fun install(release: Release) {
        val u = updater ?: return
        busy = true
        status = "Getting ${release.version}…"
        scope.launch {
            val error = runCatching { u.install(release) { status = it } }.getOrElse { it.message ?: "it failed" }
            status = error?.let { "Could not install it: $it" } ?: "Installing ${release.version}…"
            busy = false
        }
    }

    LaunchedEffect(Unit) { check() }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Updates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { check() }, enabled = !busy) { Text("Check for updates") }
        }
        if (status.isNotEmpty()) {
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (updater == null) {
            Text(
                "This copy can not update itself. New versions are on the releases page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        releases.orEmpty().take(15).forEachIndexed { i, release ->
            ReleaseRow(release, newest = i == 0, busy = busy, canDowngrade = updater?.canDowngrade ?: false,
                downgradeNote = updater?.downgradeNote, hasUpdater = updater != null) { action ->
                when (action) {
                    "page" -> updater?.openPage(release.page)
                    "downgrade" -> confirm = release
                    else -> install(release)
                }
            }
        }
    }

    confirm?.let { release ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Go back to ${release.version}?") },
            text = {
                Text("Atlas restarts into ${release.version}. Your data is kept, but settings added in newer versions are ignored by the older one.")
            },
            confirmButton = { TextButton(onClick = { confirm = null; install(release) }) { Text("Go back") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReleaseRow(
    release: Release,
    newest: Boolean,
    busy: Boolean,
    canDowngrade: Boolean,
    downgradeNote: String?,
    hasUpdater: Boolean,
    onAction: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val cmp = Releases.compare(release.version, ATLAS_VERSION)
    AtlasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(release.tag, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(release.date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                if (newest) Badge("NEWEST")
                if (cmp == 0) Badge("INSTALLED")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { open = !open }) { Text(if (open) "Hide" else "What changed") }
            }
            AnimatedVisibility(open) {
                Text(
                    release.notes.replace("**", "").ifBlank { "No notes." }.take(2500),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when {
                    !hasUpdater -> TextButton(onClick = { onAction("page") }) { Text("Open the download page") }
                    cmp > 0 -> Button(onClick = { onAction("install") }, enabled = !busy) { Text("Update") }
                    cmp == 0 -> OutlinedButton(onClick = { onAction("install") }, enabled = !busy) { Text("Reinstall") }
                    canDowngrade -> OutlinedButton(onClick = { onAction("downgrade") }, enabled = !busy) { Text("Downgrade") }
                    else -> Text(
                        downgradeNote ?: "Going back is not possible here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 6.dp),
    )
}
