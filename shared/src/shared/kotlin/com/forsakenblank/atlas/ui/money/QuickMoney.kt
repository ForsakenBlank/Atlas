package com.forsakenblank.atlas.ui.money

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.forsakenblank.atlas.data.MoneyQuick
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.LocalSnackbar
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.signedPounds
import kotlinx.coroutines.launch

// one tap adds the saved entry dated today and offers an undo
@Composable
fun rememberQuickLogger(): (MoneyQuick) -> Unit {
    val repo = atlasApp().repository
    val snackbar = LocalSnackbar.current
    val showUndo = LocalSettings.current.showUndo
    val scope = rememberCoroutineScope()
    return { quick ->
        scope.launch {
            val entry = repo.logMoneyQuick(quick.id)
            if (entry != null) {
                snackbar.currentSnackbarData?.dismiss()
                val message = "${quick.name} ${signedPounds(quick.amountPence)} added"
                if (showUndo) {
                    val result = snackbar.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) repo.deleteMoneyEntry(entry)
                } else {
                    snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
                }
            }
        }
    }
}

@Composable
fun QuickMoneyRow(quicks: List<MoneyQuick>, onTap: (MoneyQuick) -> Unit, onLongPress: ((MoneyQuick) -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        quicks.forEach { quick ->
            AtlasCard(onClick = { onTap(quick) }, onLongClick = onLongPress?.let { press -> { press(quick) } }) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(quick.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                    Text(
                        signedPounds(quick.amountPence),
                        color = if (quick.amountPence < 0) Spent else Earned,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// shown on home so a coffee or a bus fare is one tap away, tapping the title opens the money section
@Composable
fun HomeQuickMoney(navigator: AtlasNavigator, isTab: Boolean) {
    val repo = atlasApp().repository
    val quicks by remember { repo.moneyQuick() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val log = rememberQuickLogger()
    if (quicks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AtlasCard(Modifier.fillMaxWidth(), onClick = { navigator.openSection(Section.MONEY, isTab) }) {
            SectionTitle("Quick money", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        QuickMoneyRow(quicks, onTap = log, onLongPress = { navigator.openSection(Section.MONEY, isTab) })
    }
}
