package com.forsakenblank.atlas.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.DayNote
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.MoneyAccount
import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.Term
import com.forsakenblank.atlas.data.TimetableSlot
import com.forsakenblank.atlas.data.TrackerWithItem
import com.forsakenblank.atlas.util.occursOn
import com.forsakenblank.atlas.util.onDay
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.toLocalDate
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CalendarData(
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val logs: List<LogEntry> = emptyList(),
    val trackers: List<TrackerWithItem> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val slots: List<TimetableSlot> = emptyList(),
    val terms: List<Term> = emptyList(),
    val countdowns: List<Countdown> = emptyList(),
    val dayNotes: List<DayNote> = emptyList(),
    val money: List<MoneyEntry> = emptyList(),
    val accounts: List<MoneyAccount> = emptyList(),
) {
    private val logsByDay = logs.groupBy { it.timestamp.toLocalDate() }
    private val tasksByDay = tasks.filter { it.due != null }.groupBy { it.due!!.toLocalDate() }
    private val repeatingTasks = tasks.filter { !it.done && it.due != null && it.repeatRule != Repeat.NONE }

    fun eventsOn(day: LocalDate): List<Event> =
        events.filter { it.occursOn(day) }.map { it.onDay(day) }.sortedWith(compareBy({ !it.allDay }, { it.startsAt }))

    fun tasksOn(day: LocalDate): List<Task> = tasksByDay[day].orEmpty().sortedWith(compareBy({ it.done }, { -it.priority }))

    // a repeating task is one row until it gets ticked off, so its later dates are only previews
    fun repeatsOn(day: LocalDate): List<Task> = repeatingTasks.filter { task ->
        val start = task.due!!.toLocalDate()
        day > start && task.repeatRule.occursOn(start, day)
    }

    fun countdownsOn(day: LocalDate): List<Countdown> = countdowns.filter { it.showOnCalendar && it.occursOn(day) }

    private val notesByDay = dayNotes.associateBy { it.day }
    private val moneyByDay = money.groupBy { it.day }
    private val onCalendar = trackers.filter { it.tracker.showOnCalendar }.map { it.item.id }.toSet()

    // everything logged that day, the full day popup uses this
    fun logsOn(day: LocalDate): List<LogEntry> = logsByDay[day].orEmpty()

    // only trackers that are switched on for the calendar, these are the dots and the short line
    fun calendarLogsOn(day: LocalDate): List<LogEntry> = logsOn(day).filter { it.trackerId in onCalendar }

    fun noteOn(day: LocalDate): String? = notesByDay[day.toEpochDay()]?.text

    fun moneyOn(day: LocalDate): List<MoneyEntry> = moneyByDay[day.toEpochDay()].orEmpty()

    fun subject(id: Long?): Subject? = id?.let { wanted -> subjects.firstOrNull { it.id == wanted } }
}

data class Range(val from: LocalDate, val to: LocalDate)

private data class Extras(
    val subjects: List<Subject>,
    val slots: List<TimetableSlot>,
    val terms: List<Term>,
    val countdowns: List<Countdown>,
    val dayNotes: List<DayNote> = emptyList(),
    val money: List<MoneyEntry> = emptyList(),
    val accounts: List<MoneyAccount> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(private val repo: AtlasRepository) : ViewModel() {

    val range = MutableStateFlow(Range(LocalDate.now().minusDays(45), LocalDate.now().plusDays(75)))

    private val planning = combine(repo.subjects(), repo.slots(), repo.terms(), repo.countdowns()) { subjects, slots, terms, countdowns ->
        Extras(subjects, slots, terms, countdowns)
    }

    private val timetable = combine(planning, repo.dayNotes(), repo.moneyEntries(), repo.moneyAccounts()) { base, notes, money, accounts ->
        base.copy(dayNotes = notes, money = money, accounts = accounts)
    }

    val data = range.flatMapLatest { r ->
        val from = r.from.startMillis()
        val to = r.to.plusDays(1).startMillis()
        combine(
            repo.eventsBetween(from, to),
            repo.tasks(),
            repo.logsBetween(from, to),
            repo.trackers(),
            timetable,
        ) { events, tasks, logs, trackers, extras ->
            CalendarData(
                events, tasks, logs, trackers, extras.subjects, extras.slots, extras.terms, extras.countdowns,
                extras.dayNotes, extras.money, extras.accounts,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarData())

    // only reloads when the visible days drift outside what is already loaded
    fun ensure(from: LocalDate, to: LocalDate) {
        val current = range.value
        if (from >= current.from && to <= current.to) return
        range.value = Range(minOf(from, LocalDate.now()).minusDays(14), maxOf(to, LocalDate.now().plusDays(60)).plusDays(14))
    }

    fun saveNote(day: LocalDate, text: String) {
        viewModelScope.launch { repo.saveDayNote(day.toEpochDay(), text) }
    }

    fun setTaskDone(task: Task, done: Boolean) {
        viewModelScope.launch { repo.setTaskDone(task, done) }
    }
}
