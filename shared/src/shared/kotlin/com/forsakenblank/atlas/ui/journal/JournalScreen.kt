package com.forsakenblank.atlas.ui.journal

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.DayNote
import com.forsakenblank.atlas.ui.AtlasNavigator
import com.forsakenblank.atlas.ui.common.AtlasCard
import com.forsakenblank.atlas.ui.common.SectionTitle
import com.forsakenblank.atlas.ui.common.atlasViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// five steps from a rough day to a great one, the colours run red to green
enum class Mood(val value: Int, val face: String, val label: String, val color: Color) {
    AWFUL(1, "😣", "Awful", Color(0xFFE5484D)),
    LOW(2, "😕", "Low", Color(0xFFF76B15)),
    OKAY(3, "😐", "Okay", Color(0xFFFFC53D)),
    GOOD(4, "🙂", "Good", Color(0xFF46A758)),
    GREAT(5, "😄", "Great", Color(0xFF12A594));

    companion object {
        fun of(value: Int?): Mood? = entries.firstOrNull { it.value == value }
    }
}

class JournalViewModel(private val repo: AtlasRepository) : ViewModel() {

    val entries = repo.dayNotes()
        .map { notes -> notes.sortedByDescending { it.day } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var day by mutableStateOf(LocalDate.now())
        private set
    var text by mutableStateOf("")
        private set
    var mood by mutableStateOf<Int?>(null)
        private set
    private var loadedDay: LocalDate? = null
    private var saving: Job? = null

    // fills the editor the first time a day's entry is seen, later changes come from typing
    fun load(notes: List<DayNote>) {
        if (loadedDay == day) return
        val note = notes.firstOrNull { it.day == day.toEpochDay() }
        text = note?.text.orEmpty()
        mood = note?.mood
        loadedDay = day
    }

    fun open(target: LocalDate, notes: List<DayNote>) {
        flush()
        day = target
        loadedDay = null
        load(notes)
    }

    fun type(value: String) {
        text = value
        saveSoon()
    }

    fun pick(value: Int) {
        mood = if (mood == value) null else value
        saveSoon(now = true)
    }

    private fun saveSoon(now: Boolean = false) {
        saving?.cancel()
        val target = day
        saving = viewModelScope.launch {
            if (!now) delay(600)
            repo.saveJournal(target.toEpochDay(), text, mood)
        }
    }

    fun flush() {
        if (saving?.isActive == true) {
            saving?.cancel()
            val target = day
            val words = text
            val feeling = mood
            viewModelScope.launch { repo.saveJournal(target.toEpochDay(), words, feeling) }
        }
    }

    override fun onCleared() = flush()
}

private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val shortDate = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun JournalScreen(navigator: AtlasNavigator) {
    val vm = atlasViewModel { JournalViewModel(it.repository) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val notes = entries ?: return
    LaunchedEffect(notes) { vm.load(notes) }
    val today = LocalDate.now()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "editor") {
            Editor(vm, isToday = vm.day == today, onBackToToday = { vm.open(today, notes) })
        }
        item(key = "strip") { MoodStrip(notes, today) { vm.open(it, notes) } }
        val past = notes.filter { it.day != vm.day.toEpochDay() }
        if (past.isNotEmpty()) {
            item(key = "title") { SectionTitle("Earlier") }
            items(past, key = { it.day }) { note ->
                EntryRow(note) { vm.open(LocalDate.ofEpochDay(note.day), notes) }
            }
        }
    }
}

@Composable
private fun Editor(vm: JournalViewModel, isToday: Boolean, onBackToToday: () -> Unit) {
    AtlasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (isToday) "Today" else vm.day.format(longDate), style = MaterialTheme.typography.titleLarge)
                    if (isToday) Text(vm.day.format(longDate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isToday) TextButton(onClick = onBackToToday) { Text("Back to today") }
            }
            Text("How did it go?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Mood.entries.forEach { mood ->
                    MoodButton(mood, selected = vm.mood == mood.value, modifier = Modifier.weight(1f)) { vm.pick(mood.value) }
                }
            }
            OutlinedTextField(
                value = vm.text,
                onValueChange = vm::type,
                placeholder = { Text("A few lines about the day…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            )
        }
    }
}

@Composable
private fun MoodButton(mood: Mood, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val back by animateColorAsState(if (selected) mood.color.copy(alpha = 0.25f) else Color.Transparent)
    val edge = if (selected) mood.color else MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .widthIn(max = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(back)
            .border(1.dp, edge, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(mood.face, fontSize = 26.sp)
        Text(mood.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

// the last month as a row of dots, a missed day stays an empty ring
@Composable
private fun MoodStrip(notes: List<DayNote>, today: LocalDate, onOpen: (LocalDate) -> Unit) {
    val byDay = remember(notes) { notes.associateBy { it.day } }
    val days = (29 downTo 0).map { today.minusDays(it.toLong()) }
    val moods = days.mapNotNull { byDay[it.toEpochDay()]?.mood }
    AtlasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last 30 days", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (moods.isNotEmpty()) {
                    val average = moods.average()
                    val closest = Mood.of(Math.round(average).toInt())
                    Text(
                        "${closest?.face.orEmpty()} mostly ${closest?.label?.lowercase().orEmpty()} · ${moods.size} days logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            for (week in days.chunked(10)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { day ->
                        val mood = Mood.of(byDay[day.toEpochDay()]?.mood)
                        val hasText = byDay[day.toEpochDay()]?.text?.isNotBlank() == true
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(mood?.color ?: Color.Transparent)
                                .border(1.dp, if (mood == null) MaterialTheme.colorScheme.outlineVariant else mood.color, CircleShape)
                                .clickable { onOpen(day) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (hasText && mood == null) Box(Modifier.size(5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(note: DayNote, onClick: () -> Unit) {
    val mood = Mood.of(note.mood)
    AtlasCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(mood?.face ?: "📝", fontSize = 24.sp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(LocalDate.ofEpochDay(note.day).format(shortDate), fontWeight = FontWeight.SemiBold)
                if (note.text.isNotBlank()) {
                    Text(note.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

