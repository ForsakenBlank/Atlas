package com.forsakenblank.atlas.ui.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forsakenblank.atlas.data.SyncPeer
import com.forsakenblank.atlas.platform.KeepScreenOn
import com.forsakenblank.atlas.sync.Change
import com.forsakenblank.atlas.sync.Choice
import com.forsakenblank.atlas.sync.ClientStep
import com.forsakenblank.atlas.sync.HostInfo
import com.forsakenblank.atlas.sync.PlanItem
import com.forsakenblank.atlas.sync.SyncStatus
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.theme.LocalSettings
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncScreen(navigator: AtlasNavigator) {
    val app = atlasApp()
    val sync = app.sync
    val host by sync.host.collectAsStateWithLifecycle()
    val hostStatus by sync.hostStatus.collectAsStateWithLifecycle()
    val step by sync.client.collectAsStateWithLifecycle()
    val peers by sync.peers.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()

    // a phone that dims mid sync drops off the wi-fi
    KeepScreenOn(host != null || step is ClientStep.Working)

    PageScaffold("Sync", onBack = navigator::back) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Swap changes with your phone or PC over Wi-Fi. Both need Atlas open and to be on the same network. " +
                        "Nothing goes through the internet, and everything sent is locked with the code.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (val current = step) {
                is ClientStep.Review -> item(key = "review") {
                    ReviewCard(current, onCancel = sync::reset, onSync = { choices -> scope.launch { sync.run(choices) } })
                }
                else -> {
                    item(key = "host") {
                        HostCard(
                            host = host,
                            status = hostStatus,
                            deviceName = app.platform.deviceName,
                            onToggle = { on -> scope.launch { if (on) runCatching { sync.startHosting() } else sync.stopHosting() } },
                        )
                    }
                    item(key = "connect") {
                        ConnectCard(
                            step = current,
                            onConnect = { address, code -> scope.launch { sync.connect(address, code) } },
                            onReset = sync::reset,
                        )
                    }
                }
            }

            if (peers.isNotEmpty() && step !is ClientStep.Review) {
                item { SectionTitle("Synced before") }
                items(peers, key = { it.peerId }) { peer ->
                    PeerRow(peer, onForget = { scope.launch { sync.forget(peer.peerId) } })
                }
            }
        }
    }
}

@Composable
private fun HostCard(host: HostInfo?, status: SyncStatus?, deviceName: String, onToggle: (Boolean) -> Unit) {
    AtlasCard(Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
            headlineContent = { Text("Let another device connect") },
            supportingContent = { Text("This $deviceName shows an address and a code to type on the other one") },
            trailingContent = { Switch(checked = host != null, onCheckedChange = onToggle) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        AnimatedVisibility(host != null) {
            val info = host ?: return@AnimatedVisibility
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Address")
                if (info.addresses.isEmpty()) {
                    Text("No network found. Connect to Wi-Fi first.", color = MaterialTheme.colorScheme.error)
                } else {
                    info.addresses.forEach { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                }
                Label("Code")
                Text(
                    info.code.chunked(3).joinToString(" "),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                status?.let { StatusLine(it) }
            }
        }
    }
}

@Composable
private fun ConnectCard(step: ClientStep, onConnect: (String, String) -> Unit, onReset: () -> Unit) {
    val settings = LocalSettings.current
    var address by rememberSaveable { mutableStateOf(settings.lastSyncAddress) }
    var code by rememberSaveable { mutableStateOf("") }
    val busy = step is ClientStep.Working

    AtlasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connect to a device", style = MaterialTheme.typography.titleMedium)
            Text(
                "Turn on \"Let another device connect\" on the other one, then type what it shows.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = address,
                onValueChange = { address = it.trim() },
                label = { Text("Address") },
                placeholder = { Text("192.168.1.20") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = code,
                onValueChange = { text -> code = text.filter(Char::isDigit).take(6) },
                label = { Text("Code") },
                singleLine = true,
                enabled = !busy,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            when (step) {
                is ClientStep.Working -> StatusLine(step.status)
                is ClientStep.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(step.text, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                }
                is ClientStep.Done -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(step.text, modifier = Modifier.weight(1f))
                }
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onConnect(address, code) }, enabled = !busy && address.isNotBlank() && code.length == 6) {
                    Text(if (step is ClientStep.Failed) "Try again" else "Connect")
                }
                if (step is ClientStep.Done || step is ClientStep.Failed) {
                    TextButton(onClick = onReset) { Text("Clear") }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(review: ClientStep.Review, onCancel: () -> Unit, onSync: (Map<String, Choice>) -> Unit) {
    val plan = review.plan
    val peer = review.peer.name
    val choices = remember(plan) { mutableStateMapOf<String, Choice>().apply { plan.items.forEach { put(it.key, it.default) } } }
    val conflicts = plan.items.filter { it.change.conflict }
    val incoming = plan.items.filter { !it.change.conflict && it.default == Choice.THEIRS }
    val outgoing = plan.items.filter { !it.change.conflict && it.default == Choice.MINE }
    val names = review.names
    val currency = LocalSettings.current.currencySymbol
    val label = remember(names, currency) { Labels(names, currency, peer) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            if (plan.items.isEmpty()) "Already in sync with the $peer." else "Here's what changed since you last synced with the $peer. Untick anything you'd rather leave.",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (review.firstTime && plan.items.isNotEmpty()) {
            Text(
                "First sync with this device. Anything only one side has goes across, and things both made the same way, like starter packs, are joined up instead of doubled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (conflicts.isNotEmpty()) {
            AtlasCard(Modifier.fillMaxWidth()) {
                SectionHeader(Icons.Outlined.MergeType, "Needs a decision", "Changed on both, the newer one is picked for you")
                conflicts.forEach { item ->
                    ConflictRow(item, label, choices[item.key] ?: item.default) { choices[item.key] = it }
                }
            }
        }
        if (incoming.isNotEmpty()) {
            DirectionCard(Icons.Outlined.CallReceived, "From the $peer to this device", incoming, label, choices, Choice.THEIRS)
        }
        if (outgoing.isNotEmpty()) {
            DirectionCard(Icons.Outlined.CallMade, "From this device to the $peer", outgoing, label, choices, Choice.MINE)
        }

        val picked = plan.items.count { (choices[it.key] ?: it.default) != Choice.SKIP }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onSync(choices.toMap()) }) {
                Text(if (plan.items.isEmpty()) "Done" else if (picked == 1) "Sync 1 change" else "Sync $picked changes")
            }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
        }
        if (picked < plan.items.size) {
            Text(
                "Unticked changes stay as they are and show up again next time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DirectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    items: List<PlanItem>,
    label: Labels,
    choices: MutableMap<String, Choice>,
    on: Choice,
) {
    AtlasCard(Modifier.fillMaxWidth()) {
        SectionHeader(icon, title, if (items.size == 1) "1 change" else "${items.size} changes")
        items.groupBy { label.category(it) }.toSortedMap().forEach { (category, group) ->
            CategoryGroup(category, group, label, choices, on)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun CategoryGroup(category: String, group: List<PlanItem>, label: Labels, choices: MutableMap<String, Choice>, on: Choice) {
    var open by remember { mutableStateOf(false) }
    val ticked = group.count { choices[it.key] == on }
    val state = when (ticked) {
        group.size -> ToggleableState.On
        0 -> ToggleableState.Off
        else -> ToggleableState.Indeterminate
    }
    Row(
        Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = {
            val next = if (state == ToggleableState.On) Choice.SKIP else on
            group.forEach { choices[it.key] = next }
        })
        Column(Modifier.weight(1f)) {
            Text(category, style = MaterialTheme.typography.titleSmall)
            Text(
                label.groupSummary(group) + if (ticked != group.size) " · $ticked of ${group.size} ticked" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = if (open) "Hide" else "Show")
        Spacer(Modifier.width(8.dp))
    }
    AnimatedVisibility(open) {
        Column {
            group.take(300).forEach { item ->
                Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = choices[item.key] == on, onCheckedChange = { yes -> choices[item.key] = if (yes) on else Choice.SKIP })
                    Column(Modifier.weight(1f)) {
                        Text(label.title(item), style = MaterialTheme.typography.bodyMedium)
                        Text(label.detail(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (group.size > 300) {
                Text("and ${group.size - 300} more", Modifier.padding(start = 56.dp, bottom = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConflictRow(item: PlanItem, label: Labels, choice: Choice, onChoose: (Choice) -> Unit) {
    val options = buildList {
        add(Choice.MINE to "This one")
        add(Choice.THEIRS to "Theirs")
        if (item.canKeepBoth) add(Choice.BOTH to "Both")
        add(Choice.SKIP to "Skip")
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label.title(item), style = MaterialTheme.typography.bodyLarge)
        Text(label.detail(item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, text) ->
                SegmentedButton(
                    selected = choice == value,
                    onClick = { onChoose(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(text, maxLines = 1) }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
private fun PeerRow(peer: SyncPeer, onForget: () -> Unit) {
    var asking by remember { mutableStateOf(false) }
    val time = remember(peer.lastSync) {
        DateTimeFormatter.ofPattern("d MMM, HH:mm").format(Instant.ofEpochMilli(peer.lastSync).atZone(ZoneId.systemDefault()))
    }
    ListItem(
        leadingContent = { Icon(if (peer.kind == "PC") Icons.Outlined.Computer else Icons.Outlined.PhoneAndroid, contentDescription = null) },
        headlineContent = { Text(peer.name) },
        supportingContent = { Text(if (asking) "Next sync compares everything from scratch" else "Last synced $time" + (peer.address?.let { " · $it" } ?: "")) },
        trailingContent = {
            if (asking) {
                Row {
                    TextButton(onClick = { asking = false }) { Text("Keep") }
                    TextButton(onClick = onForget) { Text("Forget") }
                }
            } else {
                TextButton(onClick = { asking = true }) { Text("Forget") }
            }
        },
    )
}

@Composable
private fun StatusLine(status: SyncStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(status.text, style = MaterialTheme.typography.bodyMedium)
        val fraction = status.fraction
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.sp)
}
