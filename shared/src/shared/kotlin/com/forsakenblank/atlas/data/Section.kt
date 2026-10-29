package com.forsakenblank.atlas.data

enum class Section(val route: String, val label: String, val blurb: String) {
    HOME("home", "Home", "Today and your shortcuts"),
    NOTES("notes", "Notes", "Writing, tags and pins"),
    JOURNAL("journal", "Journal", "A few lines and a mood each day"),
    SHEETS("sheets", "Sheets", "Spreadsheets with formulas"),
    CALENDAR("calendar", "Calendar", "Events, tasks and logs by day"),
    TIMETABLE("timetable", "Timetable", "Your weekly classes"),
    TASKS("tasks", "Tasks", "To dos with due dates"),
    HABITS("habits", "Habits", "Tick off this week at a glance"),
    COUNTDOWNS("countdowns", "Countdowns", "Birthdays and big dates"),
    MONEY("money", "Money", "Accounts, spending and tax"),
    TRACK("track", "Track", "Trackers, streaks and charts"),
    INSIGHTS("insights", "Insights", "Your week, month and year in numbers"),
    FOCUS("focus", "Focus", "Pomodoro and deep work timer"),
    TOOLS("tools", "Tools", "Dice, odds and calculators"),
    EXPLORER("explorer", "Explorer", "Every item in one folder tree");

    companion object {
        val defaultTabs = listOf(NOTES, CALENDAR, HOME, TRACK, EXPLORER)
        fun fromRoute(route: String?): Section? = entries.firstOrNull { it.route == route }
    }
}
