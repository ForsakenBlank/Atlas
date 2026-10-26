package com.forsakenblank.atlas.ui.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Numbers
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Toll
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tool(val id: String, val title: String, val blurb: String, val icon: ImageVector) {
    DICE("dice", "Dice", "Roll any dice, even 2d6+3", Icons.Outlined.Casino),
    COIN("coin", "Coin flip", "Heads or tails, with a tally", Icons.Outlined.Toll),
    RANDOM_NUMBER("random-number", "Random number", "Any range, with or without repeats", Icons.Outlined.Numbers),
    PICKER("picker", "Picker", "Pick one, shuffle or make teams", Icons.Outlined.Shuffle),
    ODDS("odds", "Odds converter", "Fractional, decimal, American and %", Icons.Outlined.SwapHoriz),
    STREAK_ODDS("streak-odds", "Streak odds", "Your chances over many tries", Icons.Outlined.Repeat),
    PERCENT("percent", "Percentages", "Percent of, change, add or take off", Icons.Outlined.Percent),
    TIP("tip", "Tip and split", "Share a bill and the tip", Icons.Outlined.Receipt),
    UNITS("units", "Unit converter", "Length, weight, temperature and more", Icons.Outlined.Straighten),
    DATES("dates", "Dates", "Days between, add days, find the weekday", Icons.Outlined.DateRange),
    COUNTER("counter", "Counter", "A tally with big buttons", Icons.Outlined.AddCircle),
    STOPWATCH("stopwatch", "Stopwatch", "Laps with splits", Icons.Outlined.Timer);

    companion object {
        fun fromId(id: String?): Tool? = entries.firstOrNull { it.id == id }
    }
}
