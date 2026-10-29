@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.forsakenblank.atlas.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.MeasureUnit
import com.forsakenblank.atlas.util.UnitCategory
import com.forsakenblank.atlas.util.addPercent
import com.forsakenblank.atlas.util.convertUnit
import com.forsakenblank.atlas.util.formatMoney
import com.forsakenblank.atlas.util.niceNumber
import com.forsakenblank.atlas.util.parseNumberInput
import com.forsakenblank.atlas.util.percentChange
import com.forsakenblank.atlas.util.percentOf
import com.forsakenblank.atlas.util.splitBill
import com.forsakenblank.atlas.util.subtractPercent
import com.forsakenblank.atlas.util.whatPercent
import kotlin.math.abs

@Composable
internal fun PercentTool() {
    var ofPercent by rememberSaveable { mutableStateOf("20") }
    var ofValue by rememberSaveable { mutableStateOf("150") }
    var part by rememberSaveable { mutableStateOf("") }
    var whole by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var change by rememberSaveable { mutableStateOf("") }

    CalcCard("What is X% of Y?") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(ofPercent, { ofPercent = it }, "X", Modifier.weight(1f), signed = true, suffix = "%")
            NumberField(ofValue, { ofValue = it }, "Y", Modifier.weight(1f), signed = true)
        }
        val x = parseNumberInput(ofPercent)
        val y = parseNumberInput(ofValue)
        if (x != null && y != null) {
            Answer("${niceNumber(x)}% of ${niceNumber(y)} is ${niceNumber(percentOf(x, y))}")
        }
    }

    CalcCard("X is what % of Y?") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(part, { part = it }, "X", Modifier.weight(1f), signed = true)
            NumberField(whole, { whole = it }, "Y", Modifier.weight(1f), signed = true)
        }
        val x = parseNumberInput(part)
        val y = parseNumberInput(whole)
        if (x != null && y != null) {
            val result = whatPercent(x, y)
            Answer(if (result == null) "-" else "${niceNumber(x)} is ${niceNumber(result, 2)}% of ${niceNumber(y)}")
        }
    }

    CalcCard("Percentage change from X to Y") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(from, { from = it }, "From", Modifier.weight(1f), signed = true)
            NumberField(to, { to = it }, "To", Modifier.weight(1f), signed = true)
        }
        val x = parseNumberInput(from)
        val y = parseNumberInput(to)
        if (x != null && y != null) {
            val result = percentChange(x, y)
            Answer(
                when {
                    result == null -> "-"
                    result > 0 -> "Up ${niceNumber(result, 2)}%"
                    result < 0 -> "Down ${niceNumber(abs(result), 2)}%"
                    else -> "No change"
                },
            )
            if (result == null) Hint("A change from zero has no percentage.")
        }
    }

    CalcCard("Y plus or minus X%") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField(change, { change = it }, "X", Modifier.weight(1f), suffix = "%")
            NumberField(amount, { amount = it }, "Y", Modifier.weight(1f), signed = true)
        }
        val x = parseNumberInput(change)
        val y = parseNumberInput(amount)
        if (x != null && y != null) {
            ResultRow("Plus ${niceNumber(x)}%", niceNumber(addPercent(y, x), 2), emphasis = true)
            ResultRow("Minus ${niceNumber(x)}%", niceNumber(subtractPercent(y, x), 2), emphasis = true)
        }
    }
}

@Composable
private fun CalcCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    AtlasCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Answer(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
}

private val tipChoices = listOf(0.0, 10.0, 12.5, 15.0, 20.0)

@Composable
internal fun TipTool() {
    val symbol = LocalSettings.current.currencySymbol
    var billText by rememberSaveable { mutableStateOf("") }
    var tipText by rememberSaveable { mutableStateOf("10") }
    var people by rememberSaveable { mutableIntStateOf(2) }
    var roundUp by rememberSaveable { mutableStateOf(false) }

    NumberField(billText, { billText = it }, "Bill", Modifier.fillMaxWidth(), prefix = symbol)
    NumberField(tipText, { tipText = it }, "Tip", Modifier.fillMaxWidth(), suffix = "%")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tipChoices.forEach { choice ->
            FilterChip(
                selected = parseNumberInput(tipText) == choice,
                onClick = { tipText = niceNumber(choice) },
                label = { Text("${niceNumber(choice)}%") },
            )
        }
    }
    Stepper(label = "People", value = people, onValueChange = { people = it }, range = 1..50)
    SwitchRow("Round up each share", roundUp) { roundUp = it }

    val bill = parseNumberInput(billText)
    val tipPercent = parseNumberInput(tipText)
    val split = if (bill != null && tipPercent != null) splitBill(bill, tipPercent, people, roundUp) else null
    if (bill == null || tipPercent == null || split == null) {
        Hint("Enter the bill and a tip to see who pays what.")
        return
    }
    ResultCard {
        ResultRow("Tip", formatMoney(split.tip, symbol))
        ResultRow("Total", formatMoney(split.total, symbol))
        ResultRow(if (people == 1) "You pay" else "Each person pays", formatMoney(split.share, symbol), emphasis = true)
    }
    val extra = split.tip - bill * tipPercent / 100
    if (roundUp && extra >= 0.005) {
        Hint("Rounding up adds ${formatMoney(extra, symbol)} to the tip.")
    }
}

@Composable
internal fun UnitsTool() {
    val metric = LocalSettings.current.metricUnits
    var category by rememberSaveable { mutableStateOf(UnitCategory.LENGTH) }
    var fromSymbol by rememberSaveable { mutableStateOf(UnitCategory.LENGTH.defaultPair(metric).first.symbol) }
    var toSymbol by rememberSaveable { mutableStateOf(UnitCategory.LENGTH.defaultPair(metric).second.symbol) }
    var valueText by rememberSaveable { mutableStateOf("1") }
    val from = category.unit(fromSymbol)
    val to = category.unit(toSymbol)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UnitCategory.entries.forEach { option ->
            FilterChip(
                selected = option == category,
                onClick = {
                    if (option != category) {
                        val (first, second) = option.defaultPair(metric)
                        category = option
                        fromSymbol = first.symbol
                        toSymbol = second.symbol
                    }
                },
                label = { Text(option.label) },
            )
        }
    }
    NumberField(valueText, { valueText = it }, "Value", Modifier.fillMaxWidth(), signed = true, suffix = from.symbol)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            UnitPicker("From", category.units, from) { fromSymbol = it.symbol }
            UnitPicker("To", category.units, to) { toSymbol = it.symbol }
        }
        IconButton(
            onClick = {
                fromSymbol = to.symbol
                toSymbol = from.symbol
            },
        ) {
            Icon(Icons.Outlined.SwapVert, contentDescription = "Swap units")
        }
    }

    val value = parseNumberInput(valueText)
    if (value == null) {
        Hint("Type a number to convert it.")
        return
    }
    ResultCard {
        Text("${niceNumber(value, 6)} ${from.symbol} is", style = MaterialTheme.typography.bodyMedium)
        Text(
            "${niceNumber(convertUnit(category, value, from, to), 6)} ${to.symbol}",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    ResultCard(title = "In every unit") {
        category.units.filter { it != from }.forEach { unit ->
            ResultRow(unit.name, "${niceNumber(convertUnit(category, value, from, unit), 6)} ${unit.symbol}")
        }
    }
}

@Composable
private fun UnitPicker(label: String, units: List<MeasureUnit>, selected: MeasureUnit, onSelect: (MeasureUnit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            units.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.name) },
                    trailingIcon = { Text(unit.symbol, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    onClick = {
                        onSelect(unit)
                        open = false
                    },
                )
            }
        }
    }
}
