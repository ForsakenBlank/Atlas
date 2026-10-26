package com.forsakenblank.atlas.data

data class StarterTracker(
    val name: String,
    val kind: TrackerKind,
    val color: Int,
    val goal: Int? = null,
    val unit: String? = null,
    val aggregate: Aggregate? = null,
)

data class StarterPack(
    val id: String,
    val title: String,
    val description: String,
    val color: Int,
    val trackers: List<StarterTracker> = emptyList(),
    val notes: List<Pair<String, String>> = emptyList(),
    val tasks: List<String> = emptyList(),
)

private fun argb(hex: Long) = hex.toInt()

val StarterPacks = listOf(
    StarterPack(
        id = "welcome",
        title = "Getting started",
        description = "A short note on how Atlas fits together",
        color = argb(0xFFF4A259),
        notes = listOf(
            "Welcome to Atlas" to """
                Everything you make lives in the Explorer, so notes, folders and trackers can sit side by side.

                Trackers are one tap logs. Make them in Track and they show up on Home. Hold one to see its streaks, chart and history.

                Add #tags anywhere in a note to group it, like this one is tagged #atlas.

                Settings (the gear at the top) has themes, a theme creator, transitions and a lot more.
            """.trimIndent(),
        ),
    ),
    StarterPack(
        id = "student",
        title = "Student",
        description = "Study timer, homework check, reading and revision notes",
        color = argb(0xFF7986CB),
        trackers = listOf(
            StarterTracker("Study", TrackerKind.TIMER, argb(0xFF7986CB), goal = 120),
            StarterTracker("Homework done", TrackerKind.YES_NO, argb(0xFF4DB6AC)),
            StarterTracker("Reading", TrackerKind.TIMER, argb(0xFFFFB74D), goal = 30),
        ),
        notes = listOf(
            "Lecture notes" to "Module:\nDate:\n\nKey points\n- \n\nQuestions to follow up\n- \n\n#uni",
            "Revision plan" to "Exams coming up\n- \n\nTopics to cover\n- \n\n#uni",
        ),
        tasks = listOf("Fill in my timetable", "Add exam dates to the calendar"),
    ),
    StarterPack(
        id = "fitness",
        title = "Fitness",
        description = "Gym days, water, steps and weight",
        color = argb(0xFFE57373),
        trackers = listOf(
            StarterTracker("Gym", TrackerKind.YES_NO, argb(0xFFE57373)),
            StarterTracker("Water", TrackerKind.COUNTER, argb(0xFF64B5F6), goal = 8, unit = "glasses"),
            StarterTracker("Steps", TrackerKind.NUMBER, argb(0xFF81C784), goal = 10000, unit = "steps", aggregate = Aggregate.SUM),
            StarterTracker("Weight", TrackerKind.NUMBER, argb(0xFF90A4AE), unit = "kg", aggregate = Aggregate.LAST),
        ),
        notes = listOf("Workout plan" to "Push\n- \n\nPull\n- \n\nLegs\n- \n\n#gym"),
    ),
    StarterPack(
        id = "wellbeing",
        title = "Wellbeing",
        description = "Mood, sleep and meditation",
        color = argb(0xFFBA68C8),
        trackers = listOf(
            StarterTracker("Mood", TrackerKind.RATING, argb(0xFFFFD54F)),
            StarterTracker("Sleep", TrackerKind.NUMBER, argb(0xFF7986CB), goal = 8, unit = "hours", aggregate = Aggregate.LAST),
            StarterTracker("Meditation", TrackerKind.TIMER, argb(0xFF4DB6AC), goal = 10),
        ),
        notes = listOf("Gratitude list" to "Three good things today\n1. \n2. \n3. \n\n#journal"),
    ),
    StarterPack(
        id = "habits",
        title = "Habits",
        description = "Cold showers, no sugar, screen free time",
        color = argb(0xFF4DB6AC),
        trackers = listOf(
            StarterTracker("Cold shower", TrackerKind.COUNTER, argb(0xFF64B5F6), goal = 1),
            StarterTracker("No sugar", TrackerKind.YES_NO, argb(0xFFF06292)),
            StarterTracker("Screen free", TrackerKind.TIMER, argb(0xFF81C784), goal = 60),
        ),
    ),
    StarterPack(
        id = "work",
        title = "Work",
        description = "Deep work timer, coffee count and a meeting template",
        color = argb(0xFF90A4AE),
        trackers = listOf(
            StarterTracker("Deep work", TrackerKind.TIMER, argb(0xFF7986CB), goal = 240),
            StarterTracker("Coffee", TrackerKind.COUNTER, argb(0xFFA1887F), unit = "cups"),
        ),
        notes = listOf("Meeting notes" to "Meeting:\nWho:\n\nNotes\n- \n\nActions\n- \n\n#work"),
        tasks = listOf("Plan this week"),
    ),
    StarterPack(
        id = "money",
        title = "Money",
        description = "Daily spending and no spend days",
        color = argb(0xFF81C784),
        trackers = listOf(
            StarterTracker("Spending", TrackerKind.NUMBER, argb(0xFF81C784), unit = "£", aggregate = Aggregate.SUM),
            StarterTracker("No spend day", TrackerKind.YES_NO, argb(0xFFFFB74D)),
        ),
        notes = listOf("Budget" to "Monthly budget\n\nIncome:\nRent:\nFood:\nFun:\nSavings:\n\n#money"),
    ),
)
