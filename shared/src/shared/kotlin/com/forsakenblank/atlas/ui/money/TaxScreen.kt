package com.forsakenblank.atlas.ui.money

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.PageScaffold
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.util.LoanPlan
import com.forsakenblank.atlas.util.TaxRegion
import com.forsakenblank.atlas.util.TaxYear
import com.forsakenblank.atlas.util.WorkType
import com.forsakenblank.atlas.util.calculateTax
import com.forsakenblank.atlas.util.formatMoney

private enum class Period(val label: String, val divisor: Double) {
    YEAR("Year", 1.0),
    MONTH("Month", 12.0),
    WEEK("Week", 52.0),
}

@Composable
fun TaxScreen(navigator: AtlasNavigator) {
    var income by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf(TaxRegion.ENGLAND) }
    var work by rememberSaveable { mutableStateOf(WorkType.EMPLOYED) }
    var plan by rememberSaveable { mutableStateOf(LoanPlan.NONE) }
    var postgrad by rememberSaveable { mutableStateOf(false) }
    var period by rememberSaveable { mutableStateOf(Period.YEAR) }

    val gross = income.replace(",", "").replace("£", "").trim().toDoubleOrNull()?.takeIf { it >= 0 }
    val result = gross?.let { calculateTax(it, region, work, plan, postgrad) }

    fun money(v: Double) = formatMoney(v / period.divisor, "£")

    PageScaffold(title = "UK tax ${TaxYear.LABEL}", onBack = navigator::back) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = income,
                onValueChange = { income = it },
                label = { Text("Yearly income before tax (£)") },
                singleLine = true,
                isError = income.isNotBlank() && gross == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Chips(TaxRegion.entries, region, { it.label }) { region = it }
            Chips(WorkType.entries, work, { it.label }) { work = it }
            SectionTitle("Student loan")
            Chips(LoanPlan.entries, plan, { it.label }) { plan = it }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(checked = postgrad, onCheckedChange = { postgrad = it })
                Text("Postgraduate loan")
            }

            if (result == null) {
                Text("Type a yearly income to see what is taken off.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Chips(Period.entries, period, { it.label }) { period = it }
                AtlasCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Take home", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(money(result.takeHome), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${"%.1f".format(result.effectiveRate * 100)}% of your income goes in deductions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AtlasCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Line("Gross income", money(result.gross))
                        Line("Personal allowance", money(result.personalAllowance))
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        result.lines.forEach { l ->
                            Line("${l.label} ${"%.0f".format(l.rate * 100)}% on ${money(l.amount)}", money(l.tax))
                        }
                        Line("Income tax", money(result.incomeTax), bold = true)
                        Line(if (work == WorkType.EMPLOYED) "National Insurance" else "Class 4 National Insurance", money(result.nationalInsurance), bold = true)
                        if (plan != LoanPlan.NONE) Line("Student loan (${plan.label})", money(result.studentLoan), bold = true)
                        if (postgrad) Line("Postgraduate loan", money(result.postgradLoan), bold = true)
                    }
                }
                Text(
                    "An estimate for a single source of income. It leaves out pension, benefits in kind, other allowances and Scottish or Welsh rate changes after ${TaxYear.LABEL}. Check gov.uk for your own case.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = if (bold) FontWeight.Medium else null)
        Text(value, fontWeight = if (bold) FontWeight.Medium else null)
    }
}
