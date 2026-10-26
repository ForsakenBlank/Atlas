@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.forsakenblank.atlas.ui.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.rememberHaptic
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.util.DiceRoll
import com.forsakenblank.atlas.util.DiceSpec
import com.forsakenblank.atlas.util.MAX_PICKED_NUMBERS
import com.forsakenblank.atlas.util.Odds
import com.forsakenblank.atlas.util.chanceFromOneIn
import com.forsakenblank.atlas.util.chanceFromPercent
import com.forsakenblank.atlas.util.chanceOfAll
import com.forsakenblank.atlas.util.chanceOfAtLeastOne
import com.forsakenblank.atlas.util.expectedSuccesses
import com.forsakenblank.atlas.util.formatChance
import com.forsakenblank.atlas.util.formatMoney
import com.forsakenblank.atlas.util.niceNumber
import com.forsakenblank.atlas.util.parseDice
import com.forsakenblank.atlas.util.parseNumberInput
import com.forsakenblank.atlas.util.parseWholeInput
import com.forsakenblank.atlas.util.pickNumbers
import com.forsakenblank.atlas.util.rollDice
import com.forsakenblank.atlas.util.splitIntoTeams
import com.forsakenblank.atlas.util.triesForChance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.launch

private val quickDice = listOf(4, 6, 8, 10, 12, 20, 100)

@Composable
internal fun DiceTool() {
    val buzz = rememberHaptic()
    var count by rememberSaveable { mutableIntStateOf(1) }
    var notation by rememberSaveable { mutableStateOf("") }
    val history = remember { mutableStateListOf<DiceRoll>() }
    val typed = parseDice(notation)

    fun roll(spec: DiceSpec) {
        history.add(0, rollDice(spec))
        while (history.size > 10) history.removeAt(history.lastIndex)
        buzz()
    }

    Stepper(label = "How many dice", value = count, onValueChange = { count = it }, range = 1..20)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        quickDice.forEach { sides ->
            FilledTonalButton(onClick = { roll(DiceSpec(count, sides)) }) { Text("d$sides") }
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = notation,
            onValueChange = { notation = it.take(24) },
            label = { Text("Or type dice, like 2d6+3") },
            isError = notation.isNotBlank() && typed == null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (typed != null) roll(typed) }),
            modifier = Modifier.weight(1f),
        )
        Button(onClick = { if (typed != null) roll(typed) }, enabled = typed != null) { Text("Roll") }
    }

    val last = history.firstOrNull()
    if (last == null) {
        Hint("Tap a die to roll it. Rolls you make here stay until you leave the tool.")
    } else {
        LatestRoll(last)
    }

    if (history.size > 1) {
        SectionTitle("Earlier rolls")
        history.drop(1).forEach { roll ->
            ResultRow(label = "${roll.spec}: ${roll.faces.joinToString(", ")}", value = "${roll.total}")
        }
        TextButton(onClick = { history.clear() }) { Text("Clear rolls") }
    }
}

@Composable
private fun LatestRoll(roll: DiceRoll) {
    AtlasCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(roll.spec.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text("${roll.total}", style = MaterialTheme.typography.displayMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                roll.faces.forEach { face ->
                    // the top number on a die gets its own colour
                    val top = face == roll.spec.sides
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (top) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(
                            modifier = Modifier
                                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$face", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
            val bonus = roll.spec.modifier
            if (bonus != 0) {
                val sign = if (bonus > 0) "+" else "-"
                Hint("Dice ${roll.faces.sum()} $sign ${abs(bonus)}")
            }
        }
    }
}

@Composable
internal fun CoinTool() {
    val settings = LocalSettings.current
    val buzz = rememberHaptic()
    val scope = rememberCoroutineScope()
    var heads by rememberSaveable { mutableIntStateOf(0) }
    var tails by rememberSaveable { mutableIntStateOf(0) }
    var showingHeads by rememberSaveable { mutableStateOf(true) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    var flipping by remember { mutableStateOf(false) }
    // how far the coin has turned in degrees, heads rests at 0 and tails at 180
    val spin = remember { Animatable(if (showingHeads) 0f else 180f) }

    fun land(isHeads: Boolean) {
        showingHeads = isHeads
        flipped = true
        if (isHeads) heads++ else tails++
        buzz()
    }

    fun flip() {
        if (flipping) return
        val isHeads = Random.nextBoolean()
        if (settings.reduceMotion) {
            land(isHeads)
            scope.launch { spin.snapTo(if (isHeads) 0f else 180f) }
            return
        }
        flipping = true
        scope.launch {
            // five whole turns, plus half a turn when it has to land on the other side
            val target = spin.value + 1800f + if (isHeads == showingHeads) 0f else 180f
            val millis = (900 * settings.animationSpeed).roundToInt().coerceIn(200, 3000)
            spin.animateTo(target, tween(durationMillis = millis, easing = FastOutSlowInEasing))
            land(isHeads)
            flipping = false
            spin.snapTo(if (isHeads) 0f else 180f)
        }
    }

    val turn = cos(spin.value * PI.toFloat() / 180f)
    val face = if (flipping) turn >= 0f else showingHeads
    val squash = if (flipping) abs(turn) else 1f
    val coinColour = if (face) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val letterColour = if (face) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(176.dp)
                .graphicsLayer { scaleX = squash }
                .clip(CircleShape)
                .background(coinColour)
                .clickable(enabled = !flipping) { flip() },
            contentAlignment = Alignment.Center,
        ) {
            Text(if (face) "H" else "T", style = MaterialTheme.typography.displayLarge, color = letterColour)
        }
    }
    Text(
        when {
            flipping -> "Flipping"
            !flipped -> "Tap the coin to flip it"
            showingHeads -> "Heads"
            else -> "Tails"
        },
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { flip() }, enabled = !flipping, modifier = Modifier.fillMaxWidth()) { Text("Flip") }

    val total = heads + tails
    ResultCard(title = "This session") {
        ResultRow("Heads", "$heads")
        ResultRow("Tails", "$tails")
        if (total > 0) ResultRow("Heads so far", niceNumber(heads * 100.0 / total, 1) + "%")
    }
    if (total > 0) {
        TextButton(
            onClick = {
                heads = 0
                tails = 0
            },
        ) { Text("Reset the tally") }
    }
}

@Composable
internal fun RandomNumberTool() {
    val buzz = rememberHaptic()
    var minText by rememberSaveable { mutableStateOf("1") }
    var maxText by rememberSaveable { mutableStateOf("100") }
    var countText by rememberSaveable { mutableStateOf("1") }
    var noRepeats by rememberSaveable { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Long>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun generate() {
        val min = parseWholeInput(minText)
        val max = parseWholeInput(maxText)
        val count = parseWholeInput(countText)?.takeIf { it in 1L..MAX_PICKED_NUMBERS.toLong() }?.toInt()
        if (min == null || max == null) {
            problem = "Min and max need to be whole numbers."
            return
        }
        if (min > max) {
            problem = "Min needs to be smaller than max."
            return
        }
        if (count == null) {
            problem = "Choose between 1 and $MAX_PICKED_NUMBERS numbers."
            return
        }
        val picked = pickNumbers(min, max, count, noRepeats)
        if (picked == null) {
            problem = if (noRepeats && max - min + 1 < count) {
                "There are not enough numbers in that range to avoid repeats."
            } else {
                "Keep min and max under a trillion either way."
            }
            return
        }
        problem = null
        results = picked
        buzz()
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(minText, { minText = it }, "Min", Modifier.weight(1f), wholeNumber = true, signed = true)
        NumberField(maxText, { maxText = it }, "Max", Modifier.weight(1f), wholeNumber = true, signed = true)
    }
    NumberField(countText, { countText = it }, "How many numbers", Modifier.fillMaxWidth(), wholeNumber = true)
    SwitchRow("No repeats", noRepeats) { noRepeats = it }
    Button(onClick = { generate() }, modifier = Modifier.fillMaxWidth()) { Text("Pick") }

    val message = problem
    when {
        message != null -> Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        results.size == 1 -> Text(
            "%,d".format(results.first()),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        results.isNotEmpty() -> ResultCard(title = "${results.size} numbers") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                results.forEach { number ->
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            "%,d".format(number),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        else -> Hint("Both ends of the range can come up.")
    }
}

private sealed interface PickResult {
    data class One(val choice: String) : PickResult
    data class Order(val items: List<String>) : PickResult
    data class Teams(val teams: List<List<String>>) : PickResult
}

@Composable
internal fun PickerTool() {
    val buzz = rememberHaptic()
    var text by rememberSaveable { mutableStateOf("") }
    var teamSizeText by rememberSaveable { mutableStateOf("2") }
    var result by remember { mutableStateOf<PickResult?>(null) }
    val options = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val teamSize = parseWholeInput(teamSizeText)?.takeIf { it in 1L..1000L }?.toInt()

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Options, one per line") },
        supportingText = { Text(if (options.size == 1) "1 option" else "${options.size} options") },
        minLines = 5,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                result = PickResult.One(options.random())
                buzz()
            },
            enabled = options.isNotEmpty(),
            modifier = Modifier.weight(1f),
        ) { Text("Pick one") }
        FilledTonalButton(
            onClick = {
                result = PickResult.Order(options.shuffled())
                buzz()
            },
            enabled = options.size > 1,
            modifier = Modifier.weight(1f),
        ) { Text("Shuffle") }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NumberField(teamSizeText, { teamSizeText = it }, "People per team", Modifier.weight(1f), wholeNumber = true)
        FilledTonalButton(
            onClick = {
                if (teamSize != null) {
                    result = PickResult.Teams(splitIntoTeams(options.shuffled(), teamSize))
                    buzz()
                }
            },
            enabled = options.size > 1 && teamSize != null,
        ) { Text("Make teams") }
    }

    when (val shown = result) {
        null -> Hint("Teams are kept as even as possible, so 7 people in teams of 3 gives teams of 3, 2 and 2.")
        is PickResult.One -> ResultCard(title = "The pick") {
            Text(shown.choice, style = MaterialTheme.typography.headlineMedium)
        }
        is PickResult.Order -> ResultCard(title = "Shuffled") {
            shown.items.forEachIndexed { index, item ->
                Text("${index + 1}. $item", style = MaterialTheme.typography.bodyLarge)
            }
        }
        is PickResult.Teams -> shown.teams.forEachIndexed { index, team ->
            ResultCard(title = "Team ${index + 1}") {
                team.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
    }
}

private enum class OddsBox { FRACTIONAL, DECIMAL, AMERICAN, PERCENT }

@Composable
internal fun OddsTool() {
    val symbol = LocalSettings.current.currencySymbol
    var fractional by rememberSaveable { mutableStateOf("5/2") }
    var decimal by rememberSaveable { mutableStateOf("3.5") }
    var american by rememberSaveable { mutableStateOf("+250") }
    var percent by rememberSaveable { mutableStateOf("28.57") }
    var odds by rememberSaveable { mutableStateOf<Double?>(3.5) }

    // the box being typed in keeps its text, the other three follow it
    fun update(from: OddsBox, text: String) {
        val parsed = when (from) {
            OddsBox.FRACTIONAL -> Odds.parseFractional(text)
            OddsBox.DECIMAL -> parseNumberInput(text)?.takeIf { it >= 1 }
            OddsBox.AMERICAN -> parseNumberInput(text)?.let { Odds.fromAmerican(it) }
            OddsBox.PERCENT -> parseNumberInput(text)?.let { Odds.fromPercent(it) }
        }
        odds = parsed
        fractional = if (from == OddsBox.FRACTIONAL) text else parsed?.let { Odds.toFractional(it) }?.toString().orEmpty()
        decimal = if (from == OddsBox.DECIMAL) text else parsed?.let { niceNumber(it, 2) }.orEmpty()
        american = if (from == OddsBox.AMERICAN) text else parsed?.let { Odds.toAmerican(it) }?.let { Odds.formatAmerican(it) }.orEmpty()
        percent = if (from == OddsBox.PERCENT) text else parsed?.let { Odds.toPercent(it) }?.let { niceNumber(it, 2) }.orEmpty()
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OddsField(
            label = "Fractional",
            value = fractional,
            valid = Odds.parseFractional(fractional) != null,
            keyboard = KeyboardType.Ascii,
            onValueChange = { update(OddsBox.FRACTIONAL, it) },
            modifier = Modifier.weight(1f),
        )
        OddsField(
            label = "Decimal",
            value = decimal,
            valid = (parseNumberInput(decimal) ?: 0.0) >= 1,
            keyboard = KeyboardType.Decimal,
            onValueChange = { update(OddsBox.DECIMAL, it) },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        // american odds need a plus or minus sign, which number pads often lack
        OddsField(
            label = "American",
            value = american,
            valid = parseNumberInput(american)?.let { Odds.fromAmerican(it) } != null,
            keyboard = KeyboardType.Ascii,
            onValueChange = { update(OddsBox.AMERICAN, it) },
            modifier = Modifier.weight(1f),
        )
        OddsField(
            label = "Probability",
            value = percent,
            valid = parseNumberInput(percent)?.let { Odds.fromPercent(it) } != null,
            keyboard = KeyboardType.Decimal,
            onValueChange = { update(OddsBox.PERCENT, it) },
            suffix = "%",
            modifier = Modifier.weight(1f),
        )
    }

    val current = odds
    if (current != null) {
        val stake = 10.0
        ResultCard(title = "What that pays") {
            ResultRow("A ${formatMoney(stake, symbol)} bet returns", formatMoney(stake * current, symbol), emphasis = true)
            ResultRow("Profit", formatMoney(stake * (current - 1), symbol))
        }
    }
    Hint("Fractional odds can be typed as 5/2 or evens. American odds are +100 or more, or -100 or less.")
    Hint("Bookmakers build in a margin, so their implied chances add up to more than 100%.")
}

@Composable
private fun OddsField(
    label: String,
    value: String,
    valid: Boolean,
    keyboard: KeyboardType,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        isError = value.isNotBlank() && !valid,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier,
    )
}

private val streakTargets = listOf(0.5, 0.9, 0.99)

@Composable
internal fun StreakOddsTool() {
    var oneInMode by rememberSaveable { mutableStateOf(false) }
    var chanceText by rememberSaveable { mutableStateOf("10") }
    var triesText by rememberSaveable { mutableStateOf("10") }
    val chance = parseNumberInput(chanceText)?.let { if (oneInMode) chanceFromOneIn(it) else chanceFromPercent(it) }
    val tries = parseWholeInput(triesText)?.takeIf { it in 1L..1_000_000_000L }?.toInt()

    // switching how the chance is written keeps the same chance
    fun switchMode(toOneIn: Boolean) {
        if (toOneIn == oneInMode) return
        if (chance != null && chance > 0) {
            chanceText = if (toOneIn) niceNumber(1 / chance, 2) else niceNumber(chance * 100, 4)
        }
        oneInMode = toOneIn
    }

    ChoiceRow(
        options = listOf(false, true),
        selected = oneInMode,
        label = { if (it) "1 in N" else "Percent" },
        onSelect = { switchMode(it) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            value = chanceText,
            onValueChange = { chanceText = it },
            label = "Chance each try",
            modifier = Modifier.weight(1f),
            prefix = if (oneInMode) "1 in " else null,
            suffix = if (oneInMode) null else "%",
        )
        NumberField(
            value = triesText,
            onValueChange = { triesText = it },
            label = "Tries",
            modifier = Modifier.weight(1f),
            wholeNumber = true,
        )
    }

    if (chance == null || tries == null) {
        Hint(if (oneInMode) "Enter 1 in N, where N is 1 or more, and how many tries." else "Enter a chance from 0 to 100% and how many tries.")
        return
    }

    val all = chanceOfAll(chance, tries)
    ResultCard(title = if (tries == 1) "Over 1 try" else "Over ${"%,d".format(tries)} tries") {
        val atLeastOne = chanceOfAtLeastOne(chance, tries)
        ResultRow("At least one success", formatChance(atLeastOne), emphasis = true)
        ResultRow("No successes at all", formatChance(1 - atLeastOne))
        ResultRow("Every single try succeeds", formatChance(all))
        if (all > 0 && all < 0.5) ResultRow("That streak is about", "1 in " + niceNumber(1 / all, 0))
        ResultRow("Successes you can expect", niceNumber(expectedSuccesses(chance, tries), 2))
    }
    ResultCard(title = "Tries for at least one success") {
        streakTargets.forEach { target ->
            val needed = triesForChance(chance, target)
            ResultRow(
                "${niceNumber(target * 100)}% sure",
                when (needed) {
                    null -> "-"
                    1L -> "1 try"
                    else -> "%,d tries".format(needed)
                },
            )
        }
    }
    Hint("Each try is treated as independent, like dice rolls or loot drops with a fixed chance.")
}
