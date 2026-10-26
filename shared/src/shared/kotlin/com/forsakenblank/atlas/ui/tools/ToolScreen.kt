@file:OptIn(ExperimentalMaterial3Api::class)

package com.forsakenblank.atlas.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.EmptyState
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.util.parseNumberInput
import com.forsakenblank.atlas.util.parseWholeInput

@Composable
fun ToolScreen(toolId: String, navigator: AtlasNavigator) {
    val tool = Tool.fromId(toolId)
    PageScaffold(title = tool?.title ?: "Tools", onBack = { navigator.back() }) { padding ->
        if (tool == null) {
            EmptyState(
                icon = Icons.Outlined.Build,
                title = "Tool not found",
                body = "This tool is not here any more. Go back and pick another one.",
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (tool) {
                    Tool.DICE -> DiceTool()
                    Tool.COIN -> CoinTool()
                    Tool.RANDOM_NUMBER -> RandomNumberTool()
                    Tool.PICKER -> PickerTool()
                    Tool.ODDS -> OddsTool()
                    Tool.STREAK_ODDS -> StreakOddsTool()
                    Tool.PERCENT -> PercentTool()
                    Tool.TIP -> TipTool()
                    Tool.UNITS -> UnitsTool()
                    Tool.DATES -> DatesTool()
                    Tool.COUNTER -> CounterTool()
                    Tool.STOPWATCH -> StopwatchTool()
                }
            }
        }
    }
}

// bits shared by the tools

@Composable
internal fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    wholeNumber: Boolean = false,
    signed: Boolean = false,
    prefix: String? = null,
    suffix: String? = null,
) {
    val bad = value.isNotBlank() && (if (wholeNumber) parseWholeInput(value) == null else parseNumberInput(value) == null)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        // number keyboards often have no minus key, so signed boxes get a button for it
        trailingIcon = if (signed) {
            {
                IconButton(onClick = { onValueChange(flipSign(value)) }) {
                    Text("±", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            null
        },
        isError = bad,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (wholeNumber) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier,
    )
}

private fun flipSign(text: String): String {
    val clean = text.trim()
    return if (clean.startsWith("-")) clean.removePrefix("-") else "-$clean"
}

@Composable
internal fun ResultCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    AtlasCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            content()
        }
    }
}

@Composable
internal fun ResultRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (emphasis) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (emphasis) MaterialTheme.colorScheme.primary else Color.Unspecified,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
internal fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun Stepper(label: String, value: Int, onValueChange: (Int) -> Unit, range: IntRange) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)) },
            enabled = value > range.first,
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "One fewer")
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp),
        )
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)) },
            enabled = value < range.last,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "One more")
        }
    }
}

@Composable
internal fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label(option), maxLines = 1)
            }
        }
    }
}
