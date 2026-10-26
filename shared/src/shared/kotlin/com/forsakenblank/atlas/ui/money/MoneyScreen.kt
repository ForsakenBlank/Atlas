package com.forsakenblank.atlas.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.MoneyAccount
import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.data.MoneyQuick
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.ColorRow
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.DayPickerDialog
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.toItemColor
import com.forsakenblank.atlas.ui.settings.ConfirmDialog
import com.forsakenblank.atlas.util.currentBalance
import com.forsakenblank.atlas.util.parsePence
import com.forsakenblank.atlas.util.penceInput
import com.forsakenblank.atlas.util.pounds
import com.forsakenblank.atlas.util.signedPounds
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class MoneyViewModel(private val repo: AtlasRepository) : ViewModel() {

    // null until the first load so the empty state does not flash
    val accounts: StateFlow<List<MoneyAccount>?> =
        repo.moneyAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<MoneyEntry>> =
        repo.moneyEntries().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val quicks: StateFlow<List<MoneyQuick>> =
        repo.moneyQuick().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveQuick(quick: MoneyQuick) {
        viewModelScope.launch { repo.saveMoneyQuick(quick) }
    }

    fun deleteQuick(id: Long) {
        viewModelScope.launch { repo.deleteMoneyQuick(id) }
    }

    fun saveEntry(entry: MoneyEntry) {
        viewModelScope.launch { repo.saveMoneyEntry(entry) }
    }

    fun deleteEntry(entry: MoneyEntry) {
        viewModelScope.launch { repo.deleteMoneyEntry(entry) }
    }

    fun saveAccount(account: MoneyAccount) {
        viewModelScope.launch { repo.saveMoneyAccount(account) }
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch { repo.deleteMoneyAccount(id) }
    }

    fun transfer(from: Long, to: Long, day: Long, pence: Long, note: String?, historical: Boolean) {
        viewModelScope.launch { repo.moneyTransfer(from, to, day, pence, note, historical) }
    }
}

internal val Earned = Color(0xFF2E9E6B)
internal val Spent = Color(0xFFD05050)

private val spendCategories = listOf("Food", "Bills", "Rent", "Transport", "Shopping", "Fun", "Health")
private val earnCategories = listOf("Wages", "Gift", "Refund", "Other")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

private class EntryDraft(
    val source: MoneyEntry?,
    val accountId: Long,
    val earn: Boolean,
    val amount: String,
    val category: String,
    val note: String,
    val day: Long,
    val historical: Boolean,
)

private class AccountDraft(val source: MoneyAccount?, val name: String, val opening: String, val color: Int?)

@Composable
fun MoneyScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { MoneyViewModel(it.repository) }
    val accountList by vm.accounts.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val quicks by vm.quicks.collectAsStateWithLifecycle()
    val logQuick = rememberQuickLogger()
    var deletingQuick by remember { mutableStateOf<MoneyQuick?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    var entryDraft by remember { mutableStateOf<EntryDraft?>(null) }
    var accountDraft by remember { mutableStateOf<AccountDraft?>(null) }
    var transferOpen by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MoneyEntry?>(null) }

    val accounts = accountList
    if (accounts == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val today = LocalDate.now().toEpochDay()
    val balances = remember(accounts, entries) {
        val byAccount = entries.groupBy { it.accountId }
        accounts.associate { it.id to currentBalance(it.openingPence, byAccount[it.id].orEmpty()) }
    }

    fun newEntry(earn: Boolean) {
        val first = accounts.firstOrNull()
        if (first == null) {
            accountDraft = AccountDraft(null, "Current account", "", null)
        } else {
            val last = entries.firstOrNull { it.transferId == null }?.accountId ?: first.id
            entryDraft = EntryDraft(null, last, earn, "", "", "", today, historical = false)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                listOf("Overview", "Activity", "Charts").forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            if (accounts.isEmpty() && entries.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        Icons.Outlined.AccountBalanceWallet,
                        "No accounts yet",
                        "Add a pot like a current account or savings, then log what comes in and goes out.",
                    )
                    Button(onClick = { accountDraft = AccountDraft(null, "Current account", "", null) }) { Text("Add an account") }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = navigator::openTax) { Text("UK tax calculator") }
                }
            } else when (tab) {
                0 -> Overview(
                    accounts, balances, entries, quicks,
                    onQuick = logQuick,
                    onQuickHold = { deletingQuick = it },
                    onAccount = { acc -> accountDraft = AccountDraft(acc, acc.name, penceInput(acc.openingPence), acc.color) },
                    onNewAccount = { accountDraft = AccountDraft(null, "", "", null) },
                    onTransfer = { transferOpen = true },
                    onTax = navigator::openTax,
                )
                1 -> Activity(
                    accounts, entries,
                    onOpen = { entry ->
                        if (entry.transferId != null) {
                            deleting = entry
                        } else {
                            entryDraft = EntryDraft(
                                entry, entry.accountId, entry.amountPence > 0, penceInput(kotlin.math.abs(entry.amountPence)),
                                entry.category.orEmpty(), entry.note.orEmpty(), entry.day, entry.historical,
                            )
                        }
                    },
                )
                else -> MoneyCharts(accounts, entries)
            }
        }
        if (accounts.isNotEmpty()) {
            ExtendedFloatingActionButton(
                onClick = { newEntry(earn = false) },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    entryDraft?.let { draft ->
        EntryDialog(
            draft = draft,
            accounts = accounts,
            recent = entries.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() && c != "Transfer" } }.distinct().take(8),
            onDismiss = { entryDraft = null },
            onDelete = draft.source?.let { source -> { deleting = source } },
            onSave = { entry, asQuick ->
                vm.saveEntry(entry)
                if (asQuick) {
                    val label = entry.category?.takeIf { it.isNotBlank() } ?: entry.note?.takeIf { it.isNotBlank() } ?: "Quick add"
                    vm.saveQuick(
                        MoneyQuick(accountId = entry.accountId, name = label, amountPence = entry.amountPence, category = entry.category, note = entry.note)
                    )
                }
                entryDraft = null
            },
        )
    }
    accountDraft?.let { draft ->
        AccountDialog(
            draft = draft,
            onDismiss = { accountDraft = null },
            onDelete = draft.source?.let { source ->
                {
                    vm.deleteAccount(source.id)
                    accountDraft = null
                }
            },
            onSave = { acc ->
                vm.saveAccount(acc)
                accountDraft = null
            },
        )
    }
    if (transferOpen) {
        TransferDialog(
            accounts = accounts,
            onDismiss = { transferOpen = false },
            onSave = { from, to, day, pence, note, historical ->
                vm.transfer(from, to, day, pence, note, historical)
                transferOpen = false
            },
        )
    }
    deletingQuick?.let { quick ->
        ConfirmDialog(
            title = "Remove quick add?",
            body = "${quick.name} ${signedPounds(quick.amountPence)} will no longer show as a shortcut. Entries you already added stay.",
            button = "Remove",
            onDismiss = { deletingQuick = null },
            onConfirm = { vm.deleteQuick(quick.id) },
        )
    }
    deleting?.let { entry ->
        ConfirmDialog(
            title = if (entry.transferId != null) "Delete transfer?" else "Delete entry?",
            body = if (entry.transferId != null) "Both sides of this transfer will be removed." else "This ${pounds(entry.amountPence)} entry will be removed.",
            button = "Delete",
            onDismiss = { deleting = null },
            onConfirm = {
                vm.deleteEntry(entry)
                entryDraft = null
            },
        )
    }
}

@Composable
private fun Overview(
    accounts: List<MoneyAccount>,
    balances: Map<Long, Long>,
    entries: List<MoneyEntry>,
    quicks: List<MoneyQuick>,
    onQuick: (MoneyQuick) -> Unit,
    onQuickHold: (MoneyQuick) -> Unit,
    onAccount: (MoneyAccount) -> Unit,
    onNewAccount: () -> Unit,
    onTransfer: () -> Unit,
    onTax: () -> Unit,
) {
    val month = YearMonth.now()
    val thisMonth = entries.filter { it.transferId == null && YearMonth.from(LocalDate.ofEpochDay(it.day)) == month }
    val earned = thisMonth.filter { it.amountPence > 0 }.sumOf { it.amountPence }
    val spent = -thisMonth.filter { it.amountPence < 0 }.sumOf { it.amountPence }
    val total = balances.values.sum()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            AtlasCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total across all accounts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(pounds(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.size(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("Earned this month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(pounds(earned), color = Earned, fontWeight = FontWeight.Medium)
                        }
                        Column {
                            Text("Spent this month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(pounds(spent), color = Spent, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onNewAccount) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Account")
                }
                OutlinedButton(onClick = onTransfer, enabled = accounts.size >= 2) {
                    Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Transfer")
                }
                OutlinedButton(onClick = onTax) {
                    Icon(Icons.Outlined.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tax calculator")
                }
            }
        }
        item {
            SectionTitle("Quick add")
            if (quicks.isEmpty()) {
                Text(
                    "Turn on Save as quick add when you add an entry and it shows up here for one tap next time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.size(6.dp))
                QuickMoneyRow(quicks, onTap = onQuick, onLongPress = onQuickHold)
                Text(
                    "Tap to add now, hold to remove",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item { SectionTitle("Accounts") }
        items(accounts, key = { it.id }) { acc ->
            val balance = balances[acc.id] ?: 0L
            AtlasCard(Modifier.fillMaxWidth(), onClick = { onAccount(acc) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(acc.color.toItemColor(MaterialTheme.colorScheme.primary)))
                    Spacer(Modifier.width(12.dp))
                    Text(acc.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        pounds(balance),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (balance < 0) Spent else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Activity(accounts: List<MoneyAccount>, entries: List<MoneyEntry>, onOpen: (MoneyEntry) -> Unit) {
    var filter by rememberSaveable { mutableStateOf<Long?>(null) }
    val names = accounts.associate { it.id to it.name }
    val shown = entries.filter { filter == null || it.accountId == filter }
    val grouped = shown.groupBy { it.day }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
            accounts.forEach { acc ->
                FilterChip(selected = filter == acc.id, onClick = { filter = acc.id }, label = { Text(acc.name) })
            }
        }
        if (shown.isEmpty()) {
            EmptyState(Icons.Outlined.AccountBalanceWallet, "Nothing logged", "Tap Add to log spending or earnings. You can pick any past date.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                grouped.forEach { (day, list) ->
                    item(key = "day$day") {
                        Text(
                            LocalDate.ofEpochDay(day).format(dayFormat),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    items(list, key = { it.id }) { entry ->
                        AtlasCard(Modifier.fillMaxWidth(), onClick = { onOpen(entry) }) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.category?.takeIf { it.isNotBlank() } ?: "Uncategorised", style = MaterialTheme.typography.bodyLarge)
                                    val sub = listOfNotNull(names[entry.accountId], if (entry.historical) "past record" else null, entry.note?.takeIf { it.isNotBlank() }).joinToString(" · ")
                                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    signedPounds(entry.amountPence),
                                    color = when {
                                        entry.transferId != null -> MaterialTheme.colorScheme.onSurfaceVariant
                                        entry.amountPence > 0 -> Earned
                                        else -> Spent
                                    },
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountChips(accounts: List<MoneyAccount>, selected: Long, onSelect: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { acc ->
            FilterChip(selected = selected == acc.id, onClick = { onSelect(acc.id) }, label = { Text(acc.name) })
        }
    }
}

@Composable
private fun EntryDialog(
    draft: EntryDraft,
    accounts: List<MoneyAccount>,
    recent: List<String>,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (MoneyEntry, Boolean) -> Unit,
) {
    var asQuick by remember { mutableStateOf(false) }
    var earn by remember { mutableStateOf(draft.earn) }
    var amount by remember { mutableStateOf(draft.amount) }
    var category by remember { mutableStateOf(draft.category) }
    var note by remember { mutableStateOf(draft.note) }
    var day by remember { mutableStateOf(draft.day) }
    var accountId by remember { mutableStateOf(draft.accountId) }
    var picking by remember { mutableStateOf(false) }
    var historical by remember { mutableStateOf(draft.historical) }
    // follows the date until it has been flipped by hand
    var touched by remember { mutableStateOf(draft.source != null) }
    val pence = parsePence(amount)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.source == null) "Add entry" else "Edit entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !earn, onClick = { earn = false }, label = { Text("Spent") })
                    FilterChip(selected = earn, onClick = { earn = true }, label = { Text("Earned") })
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (£)") },
                    singleLine = true,
                    isError = amount.isNotBlank() && pence == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (accounts.size > 1) AccountChips(accounts, accountId) { accountId = it }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                val suggestions = ((if (earn) earnCategories else spendCategories) + recent).distinct()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { name ->
                        FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) })
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(LocalDate.ofEpochDay(day).format(dayFormat))
                }
                PastRecordSwitch(historical) {
                    historical = it
                    touched = true
                }
                if (draft.source == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Save as quick add")
                            Text(
                                "One tap on Home or here to add it again",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = asQuick, onCheckedChange = { asQuick = it })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pence != null,
                onClick = {
                    pence?.let { value ->
                        val base = draft.source ?: MoneyEntry(accountId = accountId, day = day, amountPence = 0)
                        onSave(
                            base.copy(
                                accountId = accountId,
                                day = day,
                                historical = historical,
                                amountPence = if (earn) value else -value,
                                category = category.trim().ifEmpty { null },
                                note = note.trim().ifEmpty { null },
                            ),
                            asQuick,
                        )
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (picking) {
        DayPickerDialog(day, onDismiss = { picking = false }) {
            day = it
            if (!touched) historical = it < LocalDate.now().toEpochDay()
            picking = false
        }
    }
}

// a record from before you started tracking, it is in the lists and charts but the balance stays put
@Composable
private fun PastRecordSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Past record only")
            Text(
                "Keeps the account balance as it is",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AccountDialog(draft: AccountDraft, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (MoneyAccount) -> Unit) {
    var name by remember { mutableStateOf(draft.name) }
    var opening by remember { mutableStateOf(draft.opening) }
    var color by remember { mutableStateOf(draft.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pence = if (opening.isBlank()) 0L else parsePence(opening, allowNegative = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (draft.source == null) "New account" else "Edit account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = { Text("Starting balance (£)") },
                    supportingText = { Text("What was in it before your first entry") },
                    singleLine = true,
                    isError = opening.isNotBlank() && pence == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                ColorRow(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && pence != null,
                onClick = {
                    val base = draft.source ?: MoneyAccount(name = "")
                    onSave(base.copy(name = name.trim(), openingPence = pence ?: 0L, color = color))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Delete account?",
            body = "Every entry in ${draft.name.ifBlank { "this account" }} goes with it.",
            button = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = onDelete,
        )
    }
}

@Composable
private fun TransferDialog(
    accounts: List<MoneyAccount>,
    onDismiss: () -> Unit,
    onSave: (from: Long, to: Long, day: Long, pence: Long, note: String?, historical: Boolean) -> Unit,
) {
    var from by remember { mutableStateOf(accounts.first().id) }
    var to by remember { mutableStateOf(accounts.first { it.id != accounts.first().id }.id) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(LocalDate.now().toEpochDay()) }
    var picking by remember { mutableStateOf(false) }
    var historical by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(false) }
    val pence = parsePence(amount)?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move money") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("From", style = MaterialTheme.typography.labelLarge)
                AccountChips(accounts, from) {
                    from = it
                    if (to == it) to = accounts.first { acc -> acc.id != it }.id
                }
                Text("To", style = MaterialTheme.typography.labelLarge)
                AccountChips(accounts.filter { it.id != from }, to) { to = it }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (£)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(LocalDate.ofEpochDay(day).format(dayFormat))
                }
                PastRecordSwitch(historical) {
                    historical = it
                    touched = true
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = pence != null,
                onClick = { pence?.let { onSave(from, to, day, it, note.trim().ifEmpty { null }, historical) } },
            ) { Text("Move") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) {
        DayPickerDialog(day, onDismiss = { picking = false }) {
            day = it
            if (!touched) historical = it < LocalDate.now().toEpochDay()
            picking = false
        }
    }
}
