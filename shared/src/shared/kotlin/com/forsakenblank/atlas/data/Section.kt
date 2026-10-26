package com.forsakenblank.atlas.data

enum class Section(val route: String, val label: String, val blurb: String) {
    HOME("home", "Home", "Today and your shortcuts"),
    NOTES("notes", "Notes", "Writing, tags and pins"),
    SHEETS("sheets", "Sheets", "Spreadsheets with formulas"),
    CALENDAR("calendar", "Calendar", "Events, tasks and logs by day"),
    TIMETABLE("timetable", "Timetable", "Your weekly classes"),
    TASKS("tasks", "Tasks", "To dos with due dates"),
    COUNTDOWNS("countdowns", "Countdowns", "Birthdays and big dates"),
    MONEY("money", "Money", "Accounts, spending and tax"),
    TRACK("track", "Track", "Trackers, streaks and charts"),
    FOCUS("focus", "Focus", "Pomodoro and deep work timer"),
    TOOLS("tools", "Tools", "Dice, odds and calculators"),
    EXPLORER("explorer", "Explorer", "Every item in one folder tree");

    companion object {
        val defaultTabs = listOf(NOTES, CALENDAR, HOME, TRACK, EXPLORER)
        fun fromRoute(route: String?): Section? = entries.firstOrNull { it.route == route }
    }
}
