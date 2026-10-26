package com.forsakenblank.atlas.ui.explorer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.atlasViewModel
import com.forsakenblank.atlas.ui.common.icon
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.formatDay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TrashViewModel(private val repo: AtlasRepository) : ViewModel() {
    val items = repo.trash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun restore(item: Item) {
        viewModelScope.launch { repo.restore(item) }
    }

    fun deleteForever(item: Item) {
        viewModelScope.launch { repo.deleteForever(item) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { TrashViewModel(it.repository) }
    val items by vm.items.collectAsStateWithLifecycle()
    val days = LocalSettings.current.trashDays

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = { navigator.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.Delete,
                        "Trash is empty",
                        if (days <= 0) "Things you delete land here until you empty it." else "Things you delete land here for $days days.",
                    )
                }
            }
            items(items, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.name.ifBlank { "Untitled" }) },
                    supportingContent = { Text("Deleted ${formatDay(item.deletedAt ?: 0L).lowercase()}") },
                    leadingContent = { Icon(item.type.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { vm.restore(item) }) {
                                Icon(Icons.Outlined.Restore, contentDescription = "Restore")
                            }
                            IconButton(onClick = { vm.deleteForever(item) }) {
                                Icon(Icons.Outlined.DeleteForever, contentDescription = "Delete forever")
                            }
                        }
                    },
                )
            }
        }
    }
}
