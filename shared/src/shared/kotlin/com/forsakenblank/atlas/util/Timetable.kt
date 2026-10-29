package com.forsakenblank.atlas.util

import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Term
import com.forsakenblank.atlas.data.TimetableSlot
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class ClassSlot(val slot: TimetableSlot, val subject: Subject) {
    val room: String? get() = slot.room ?: subject.room
}

fun LocalDate.monday(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

// 0 when the timetable is the same every week, otherwise 1 for week A and 2 for week B
fun AppSettings.weekOf(date: LocalDate): Int {
    if (!twoWeekTimetable) return 0
    val anchor = LocalDate.ofEpochDay(weekAStart).monday()
    val weeks = ChronoUnit.WEEKS.between(anchor, date.monday())
    return if (Math.floorMod(weeks, 2L) == 0L) 1 else 2
}

// makes the week holding this date week A or B
fun AppSettings.withWeek(date: LocalDate, week: Int): AppSettings {
    val monday = date.monday()
    val anchor = if (week == 2) monday.minusWeeks(1) else monday
    return copy(weekAStart = anchor.toEpochDay())
}

fun weekLabel(week: Int): String = when (week) {
    1 -> "Week A"
    2 -> "Week B"
    else -> "Every week"
}

// no terms set up means classes run all year
fun inTerm(date: LocalDate, terms: List<Term>): Boolean =
    terms.isEmpty() || terms.any { date.toEpochDay() in it.startDay..it.endDay }

fun classesOn(
    date: LocalDate,
    slots: List<TimetableSlot>,
    subjects: List<Subject>,
    terms: List<Term>,
    settings: AppSettings,
): List<ClassSlot> {
    if (!inTerm(date, terms)) return emptyList()
    val week = settings.weekOf(date)
    val byId = subjects.associateBy { it.id }
    return slots
        .filter { it.dayOfWeek == date.dayOfWeek.value && (week == 0 || it.week == 0 || it.week == week) }
        .mapNotNull { slot -> byId[slot.subjectId]?.let { ClassSlot(slot, it) } }
        .sortedBy { it.slot.startMinute }
}

data class NowNext(val now: ClassSlot?, val next: ClassSlot?)

// the class happening right now and the next one later today
fun nowAndNext(today: List<ClassSlot>, minute: Int): NowNext = NowNext(
    now = today.firstOrNull { minute in it.slot.startMinute until it.slot.endMinute },
    next = today.firstOrNull { it.slot.startMinute > minute },
)
