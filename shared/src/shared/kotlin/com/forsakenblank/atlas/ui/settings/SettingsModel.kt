package com.forsakenblank.atlas.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.BarLabels
import com.forsakenblank.atlas.data.CalendarView
import com.forsakenblank.atlas.data.CardStyle
import com.forsakenblank.atlas.data.IconStyle
import com.forsakenblank.atlas.data.ExplorerSort
import com.forsakenblank.atlas.data.FontChoice
import com.forsakenblank.atlas.data.LongPress
import com.forsakenblank.atlas.data.NoteLayout
import com.forsakenblank.atlas.data.NoteSort
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.data.ShortcutSize
import com.forsakenblank.atlas.data.TaskSort
import com.forsakenblank.atlas.data.TextSize
import com.forsakenblank.atlas.data.ThemeMode
import com.forsakenblank.atlas.data.Transition
import com.forsakenblank.atlas.util.NO_REMINDER
import com.forsakenblank.atlas.util.ReminderMinuteOptions
import com.forsakenblank.atlas.util.formatMinuteOfDay
import com.forsakenblank.atlas.util.reminderMinutesLabel
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.forsakenblank.atlas.platform.DeviceKind
import com.forsakenblank.atlas.platform.platform

enum class SettingsCategory(val title: String, val blurb: String, val icon: ImageVector) {
    APPEARANCE("Appearance", "Themes, colours, fonts, shapes and app icon", Icons.Outlined.Palette),
    MOTION("Motion and feel", "Transitions, animation speed and haptics", Icons.Outlined.Animation),
    NAVIGATION("Navigation", "Tabs, bottom bar, sections and start screen", Icons.Outlined.Navigation),
    HOME("Home", "Greeting, cards and shortcut grid", Icons.Outlined.Home),
    NOTES("Notes", "Sorting, layout and the editor", Icons.Outlined.Description),
    TRACK("Track", "Charts, streaks and undo", Icons.Outlined.Insights),
    CALENDAR("Calendar", "Week start, clock and what shows on days", Icons.Outlined.CalendarMonth),
    TIMETABLE("Timetable", "Week A and B, hours and subject folders", Icons.Outlined.School),
    TASKS("Tasks", "Sorting and finished tasks", Icons.Outlined.Checklist),
    REMINDERS("Reminders", "Birthdays, tasks and events", Icons.Outlined.NotificationsActive),
    FOCUS("Focus", "Session and break lengths", Icons.Outlined.Timer),
    EXPLORER("Explorer", "Sorting, layout and trash", Icons.Outlined.FolderOpen),
    TOOLS("Tools", "Units and currency", Icons.Outlined.Calculate),
    PRIVACY("Privacy", "Hide Atlas in recent apps", Icons.Outlined.Lock),
    DATA("Backup and data", "Backups, starter packs and resets", Icons.Outlined.SaveAlt),
    ABOUT("About", "Version and what is new", Icons.Outlined.Info),
}

// things a settings row can ask the screen to do
enum class SettingsAction {
    OPEN_THEMES, NEW_THEME, EDIT_TABS, BACKUP, RESTORE, STARTER_PACKS, EMPTY_TRASH,
    CLEAR_DONE_TASKS, RESET_SETTINGS, PICK_FOCUS_TRACKER, SUBJECTS, TERMS,
    NOTIFICATION_SETTINGS, TEST_REMINDER, SYNC, CAELESTIA_LOOK, APP_ICON,
}

sealed class Setting(
    val category: SettingsCategory,
    val title: String,
    val summary: String,
    val keywords: String,
) {
    class Toggle(
        category: SettingsCategory,
        title: String,
        summary: String = "",
        keywords: String = "",
        val get: (AppSettings) -> Boolean,
        val set: (AppSettings, Boolean) -> AppSettings,
    ) : Setting(category, title, summary, keywords)

    class Choice<T>(
        category: SettingsCategory,
        title: String,
        summary: String = "",
        keywords: String = "",
        val options: List<T>,
        val label: (T) -> String,
        val get: (AppSettings) -> T,
        val set: (AppSettings, T) -> AppSettings,
        // for labels that follow another setting, like the 12 or 24 hour clock
        val labelWith: (AppSettings, T) -> String = { _, value -> label(value) },
    ) : Setting(category, title, summary, keywords)

    class Range(
        category: SettingsCategory,
        title: String,
        summary: String = "",
        keywords: String = "",
        val min: Float,
        val max: Float,
        val steps: Int,
        val format: (Float) -> String,
        val get: (AppSettings) -> Float,
        val set: (AppSettings, Float) -> AppSettings,
    ) : Setting(category, title, summary, keywords)

    class TextValue(
        category: SettingsCategory,
        title: String,
        summary: String = "",
        keywords: String = "",
        val get: (AppSettings) -> String,
        val set: (AppSettings, String) -> AppSettings,
    ) : Setting(category, title, summary, keywords)

    class Action(
        category: SettingsCategory,
        title: String,
        summary: String = "",
        keywords: String = "",
        val action: SettingsAction,
        val danger: Boolean = false,
    ) : Setting(category, title, summary, keywords)
}

private fun percent(f: Float) = "${(f * 100).roundToInt()}%"
private fun minutes(f: Float) = "${f.roundToInt()} min"
private fun hourLabel(h: Int) = "%02d:00".format(h)

private val sectionToggles = Section.entries.filter { it != Section.HOME }.map { section ->
    Setting.Toggle(
        SettingsCategory.NAVIGATION,
        "Show ${section.label}",
        section.blurb,
        "section feature turn off hide ${section.label}",
        get = { section !in it.hiddenSections },
        set = { s, on -> s.copy(hiddenSections = if (on) s.hiddenSections - section else s.hiddenSections + section) },
    )
}

val AllSettings: List<Setting> = listOf(
    // appearance
    Setting.Action(SettingsCategory.APPEARANCE, "Themes", "Pick from 25 built in themes or your own", "colour color theme palette dark light", SettingsAction.OPEN_THEMES),
    Setting.Action(SettingsCategory.APPEARANCE, "Create a theme", "Build your own theme with the colour picker", "custom colour color creator editor", SettingsAction.NEW_THEME),
    Setting.Action(SettingsCategory.APPEARANCE, "Caelestia look", "One tap: the Caelestia theme, glass cards, round corners, a floating bar and smooth push transitions", "caelestia preset style glass round modern minimal", SettingsAction.CAELESTIA_LOOK),
    Setting.Toggle(
        SettingsCategory.APPEARANCE, "Follow Luminescent's theme", "After a sync with Luminescent, Atlas switches to the theme Luminescent is using", "luminescent pc sync theme match",
        get = { it.followLuminescent }, set = { s, v -> s.copy(followLuminescent = v) },
    ),
    Setting.Choice(
        SettingsCategory.APPEARANCE, "Light or dark", "Which mode Atlas uses", "dark mode night light system",
        options = ThemeMode.entries, label = { it.name.lowercase().replaceFirstChar(Char::uppercase).replace("System", "Follow the system") },
        get = { it.themeMode }, set = { s, v -> s.copy(themeMode = v) },
    ),
    Setting.Toggle(
        SettingsCategory.APPEARANCE, "Wallpaper colours", "Use Material You colours from your wallpaper instead of the theme", "material you dynamic",
        get = { it.dynamicColor }, set = { s, v -> s.copy(dynamicColor = v) },
    ),
    Setting.Toggle(
        SettingsCategory.APPEARANCE, "Pure black", "True black backgrounds on dark themes, good for OLED screens", "amoled oled black",
        get = { it.pureBlack }, set = { s, v -> s.copy(pureBlack = v) },
    ),
    Setting.Choice(
        SettingsCategory.APPEARANCE, "Font", "The typeface used across the app", "typeface font family serif mono",
        options = FontChoice.entries, label = { it.label }, get = { it.font }, set = { s, v -> s.copy(font = v) },
    ),
    Setting.Toggle(
        SettingsCategory.APPEARANCE, "Bold headings", "Make titles and headings heavier", "font weight",
        get = { it.boldHeadings }, set = { s, v -> s.copy(boldHeadings = v) },
    ),
    Setting.Range(
        SettingsCategory.APPEARANCE, "Text size", "Scales every bit of text", "font size bigger smaller",
        min = 0.8f, max = 1.4f, steps = 11, format = ::percent, get = { it.fontScale }, set = { s, v -> s.copy(fontScale = v) },
    ),
    Setting.Range(
        SettingsCategory.APPEARANCE, "Interface size", "Makes everything bigger or more compact", "density compact zoom scale",
        min = 0.8f, max = 2f, steps = 11, format = ::percent, get = { it.uiScale }, set = { s, v -> s.copy(uiScale = v) },
    ),
    Setting.Range(
        SettingsCategory.APPEARANCE, "Corner roundness", "From sharp corners to round pills", "shape radius rounded corners",
        min = 0f, max = 32f, steps = 15, format = { "${it.roundToInt()} dp" }, get = { it.cornerRadius.toFloat() }, set = { s, v -> s.copy(cornerRadius = v.roundToInt()) },
    ),
    Setting.Choice(
        SettingsCategory.APPEARANCE, "Card style", "How cards are drawn", "card border shadow outline",
        options = CardStyle.entries, label = { it.label }, get = { it.cardStyle }, set = { s, v -> s.copy(cardStyle = v) },
    ),
    Setting.Choice(
        SettingsCategory.APPEARANCE, "Icon style", "How the icons across Atlas are drawn", "icons symbols outlined rounded sharp filled minimal",
        options = IconStyle.entries, label = { it.label }, get = { it.iconStyle }, set = { s, v -> s.copy(iconStyle = v) },
    ),
    Setting.Action(SettingsCategory.APPEARANCE, "App icon", "Pick the colours of the Atlas icon on your home screen or taskbar", "launcher icon logo colour", SettingsAction.APP_ICON),

    // motion
    Setting.Choice(
        SettingsCategory.MOTION, "Page transition", "Animation when opening notes, settings and other pages", "animation menu transition open",
        options = Transition.entries, label = { it.label }, get = { it.screenTransition }, set = { s, v -> s.copy(screenTransition = v) },
    ),
    Setting.Choice(
        SettingsCategory.MOTION, "Tab transition", "Animation when switching tabs on the bottom bar", "animation menu transition tabs",
        options = Transition.entries, label = { it.label }, get = { it.tabTransition }, set = { s, v -> s.copy(tabTransition = v) },
    ),
    Setting.Range(
        SettingsCategory.MOTION, "Animation speed", "Lower is snappier, higher is slower and smoother", "speed duration fast slow",
        min = 0.25f, max = 2f, steps = 6, format = { "${"%.2f".format(it).trimEnd('0').trimEnd('.')}x" },
        get = { it.animationSpeed }, set = { s, v -> s.copy(animationSpeed = v) },
    ),
    Setting.Toggle(
        SettingsCategory.MOTION, "Reduce motion", "Turns off transitions and most animations", "accessibility animation off",
        get = { it.reduceMotion }, set = { s, v -> s.copy(reduceMotion = v) },
    ),
    Setting.Toggle(
        SettingsCategory.MOTION, "Haptics", "A small buzz when you log or tick something", "vibration vibrate feedback",
        get = { it.haptics }, set = { s, v -> s.copy(haptics = v) },
    ),
    Setting.Toggle(
        SettingsCategory.MOTION, "Celebrate goals", "Say so when you hit a daily goal", "confetti celebration goal",
        get = { it.celebrateGoals }, set = { s, v -> s.copy(celebrateGoals = v) },
    ),

    // navigation
    Setting.Action(SettingsCategory.NAVIGATION, "Bottom bar tabs", "Choose and reorder up to 5 tabs", "tabs order reorder bottom bar menu", SettingsAction.EDIT_TABS),
    Setting.Choice(
        SettingsCategory.NAVIGATION, "Open Atlas on", "The screen you see when the app starts", "start launch open first screen",
        options = listOf<Section?>(null) + Section.entries, label = { it?.label ?: "Where I left off" },
        get = { it.startSection }, set = { s, v -> s.copy(startSection = v) },
    ),
    Setting.Choice(
        SettingsCategory.NAVIGATION, "Tab labels", "When to show words under the tab icons", "labels bottom bar text",
        options = BarLabels.entries, label = { it.label }, get = { it.barLabels }, set = { s, v -> s.copy(barLabels = v) },
    ),
    Setting.Toggle(
        SettingsCategory.NAVIGATION, "Floating bottom bar", "A rounded bar that floats above the content", "pill bottom bar style",
        get = { it.floatingBar }, set = { s, v -> s.copy(floatingBar = v) },
    ),
    Setting.Toggle(
        SettingsCategory.NAVIGATION, "Quick add button", "The + button on Home for new notes, tasks, events and more", "fab plus add",
        get = { it.showQuickAdd }, set = { s, v -> s.copy(showQuickAdd = v) },
    ),
) + sectionToggles + listOf(
    // home
    Setting.TextValue(
        SettingsCategory.HOME, "Your name", "Used in the greeting", "name greeting personal",
        get = { it.userName }, set = { s, v -> s.copy(userName = v) },
    ),
    Setting.Toggle(SettingsCategory.HOME, "Greeting card", "Date, greeting and what is left today", "today card", get = { it.showGreeting }, set = { s, v -> s.copy(showGreeting = v) }),
    Setting.Toggle(SettingsCategory.HOME, "Now and next", "Your current and next class or event", "timetable class event", get = { it.showNowNext }, set = { s, v -> s.copy(showNowNext = v) }),
    Setting.Toggle(SettingsCategory.HOME, "Today's agenda", "Events and tasks due today", "agenda events tasks", get = { it.showAgenda }, set = { s, v -> s.copy(showAgenda = v) }),
    Setting.Toggle(SettingsCategory.HOME, "Sections row", "Quick links to Tasks, Focus, Tools and the rest", "sections links", get = { it.showSectionsRow }, set = { s, v -> s.copy(showSectionsRow = v) }),
    Setting.Toggle(SettingsCategory.HOME, "Pinned notes", "Notes you pin show on Home", "pinned notes", get = { it.showPinnedNotes }, set = { s, v -> s.copy(showPinnedNotes = v) }),
    Setting.Toggle(SettingsCategory.HOME, "Countdowns", "Pinned and upcoming birthdays and dates", "countdown birthday upcoming", get = { it.showCountdowns }, set = { s, v -> s.copy(showCountdowns = v) }),
    Setting.Choice(
        SettingsCategory.HOME, "Shortcut size", "How big tracker buttons are", "shortcut button size grid",
        options = ShortcutSize.entries, label = { it.label }, get = { it.shortcutSize }, set = { s, v -> s.copy(shortcutSize = v) },
    ),
    Setting.Choice(
        SettingsCategory.HOME, "Shortcut columns", "Fixed number of columns, or fit to the screen", "grid columns",
        options = listOf(0, 1, 2, 3, 4), label = { if (it == 0) "Fit to screen" else "$it" },
        get = { it.shortcutColumns }, set = { s, v -> s.copy(shortcutColumns = v) },
    ),
    Setting.Choice(
        SettingsCategory.HOME, "Long press a shortcut", "Select several to remove, or jump to the tracker", "hold press select delete remove",
        options = LongPress.entries, label = { it.label }, get = { it.shortcutLongPress }, set = { s, v -> s.copy(shortcutLongPress = v) },
    ),

    // notes
    Setting.Choice(SettingsCategory.NOTES, "Sort notes by", keywords = "order sort", options = NoteSort.entries, label = { it.label }, get = { it.noteSort }, set = { s, v -> s.copy(noteSort = v) }),
    Setting.Choice(SettingsCategory.NOTES, "Notes layout", "List, grid or a compact one line list", "view grid list", options = NoteLayout.entries, label = { it.label }, get = { it.noteLayout }, set = { s, v -> s.copy(noteLayout = v) }),
    Setting.Choice(
        SettingsCategory.NOTES, "Preview lines", "How much of each note shows in the list", "preview lines",
        options = listOf(0, 1, 2, 3, 5), label = { if (it == 0) "None" else "$it" }, get = { it.notePreviewLines }, set = { s, v -> s.copy(notePreviewLines = v) },
    ),
    Setting.Toggle(SettingsCategory.NOTES, "Pinned notes first", get = { it.pinnedFirst }, set = { s, v -> s.copy(pinnedFirst = v) }),
    Setting.Toggle(SettingsCategory.NOTES, "Show dates on notes", get = { it.showNoteDates }, set = { s, v -> s.copy(showNoteDates = v) }),
    Setting.Toggle(SettingsCategory.NOTES, "Word count", "Words and characters under the editor", "count words", get = { it.showWordCount }, set = { s, v -> s.copy(showWordCount = v) }),
    Setting.Choice(SettingsCategory.NOTES, "Editor text size", keywords = "font size editor", options = TextSize.entries, label = { it.label }, get = { it.editorTextSize }, set = { s, v -> s.copy(editorTextSize = v) }),

    // track
    Setting.Toggle(SettingsCategory.TRACK, "Undo after logging", "Show an Undo button after each tap", "undo snackbar", get = { it.showUndo }, set = { s, v -> s.copy(showUndo = v) }),
    Setting.Toggle(SettingsCategory.TRACK, "Streaks on buttons", "Show the current streak on each tracker button", "streak flame", get = { it.showStreaksOnCards }, set = { s, v -> s.copy(showStreaksOnCards = v) }),
    Setting.Choice(
        SettingsCategory.TRACK, "Chart length", "How many days the tracker chart shows", "chart days history",
        options = listOf(7, 14, 30, 60, 90), label = { "$it days" }, get = { it.chartDays }, set = { s, v -> s.copy(chartDays = v) },
    ),
    Setting.Toggle(SettingsCategory.TRACK, "Confirm before deleting logs", get = { it.confirmLogDelete }, set = { s, v -> s.copy(confirmLogDelete = v) }),

    // calendar
    Setting.Toggle(SettingsCategory.CALENDAR, "Week starts on Monday", "Turn off to start on Sunday", "first day week sunday monday", get = { it.weekStartsMonday }, set = { s, v -> s.copy(weekStartsMonday = v) }),
    Setting.Toggle(SettingsCategory.CALENDAR, "24 hour clock", "Turn off for am and pm", "time clock 12 hour", get = { it.use24Hour }, set = { s, v -> s.copy(use24Hour = v) }),
    Setting.Choice(SettingsCategory.CALENDAR, "Default view", keywords = "month week agenda", options = CalendarView.entries, label = { it.label }, get = { it.calendarView }, set = { s, v -> s.copy(calendarView = v) }),
    Setting.Toggle(SettingsCategory.CALENDAR, "Tracker logs on days", "Dots on days you logged something", "tracker dots heatmap", get = { it.showTrackerDots }, set = { s, v -> s.copy(showTrackerDots = v) }),
    Setting.Toggle(SettingsCategory.CALENDAR, "Tasks on days", "Tasks show on their due date", "tasks due", get = { it.showTasksOnCalendar }, set = { s, v -> s.copy(showTasksOnCalendar = v) }),
    Setting.Toggle(SettingsCategory.CALENDAR, "Classes on days", "Timetable classes show in the day list", "timetable classes", get = { it.showClassesOnCalendar }, set = { s, v -> s.copy(showClassesOnCalendar = v) }),
    Setting.Toggle(SettingsCategory.CALENDAR, "Week numbers", get = { it.showWeekNumbers }, set = { s, v -> s.copy(showWeekNumbers = v) }),
    Setting.Choice(
        SettingsCategory.CALENDAR, "Default event length", keywords = "event duration", options = listOf(15, 30, 45, 60, 90, 120),
        label = { if (it < 60) "$it min" else "${it / 60}h${if (it % 60 != 0) " ${it % 60}m" else ""}" },
        get = { it.defaultEventMinutes }, set = { s, v -> s.copy(defaultEventMinutes = v) },
    ),

    // timetable
    Setting.Action(SettingsCategory.TIMETABLE, "Subjects", "Names, colours, teachers and rooms", "subject class module", SettingsAction.SUBJECTS),
    Setting.Action(SettingsCategory.TIMETABLE, "Terms", "Term dates, so classes only show during term", "term semester holiday dates", SettingsAction.TERMS),
    Setting.Toggle(SettingsCategory.TIMETABLE, "Two week timetable", "Alternate between week A and week B", "week a b rotation fortnight", get = { it.twoWeekTimetable }, set = { s, v -> s.copy(twoWeekTimetable = v) }),
    Setting.Toggle(SettingsCategory.TIMETABLE, "Show weekends", get = { it.timetableWeekends }, set = { s, v -> s.copy(timetableWeekends = v) }),
    Setting.Choice(
        SettingsCategory.TIMETABLE, "Day starts at", keywords = "hours start", options = (5..12).toList(), label = ::hourLabel,
        get = { it.timetableStartHour }, set = { s, v -> s.copy(timetableStartHour = v) },
    ),
    Setting.Choice(
        SettingsCategory.TIMETABLE, "Day ends at", keywords = "hours end", options = (14..23).toList(), label = ::hourLabel,
        get = { it.timetableEndHour }, set = { s, v -> s.copy(timetableEndHour = v) },
    ),
    Setting.Toggle(SettingsCategory.TIMETABLE, "Folder for each subject", "New subjects get a folder in the Explorer for notes", "explorer folder", get = { it.makeSubjectFolders }, set = { s, v -> s.copy(makeSubjectFolders = v) }),

    // tasks
    Setting.Choice(SettingsCategory.TASKS, "Sort tasks by", keywords = "order", options = TaskSort.entries, label = { it.label }, get = { it.taskSort }, set = { s, v -> s.copy(taskSort = v) }),
    Setting.Toggle(SettingsCategory.TASKS, "Show finished tasks", get = { it.showCompletedTasks }, set = { s, v -> s.copy(showCompletedTasks = v) }),
    Setting.Toggle(SettingsCategory.TASKS, "Tasks on Home", "Tasks due today show in the Home agenda", "home agenda", get = { it.tasksOnHome }, set = { s, v -> s.copy(tasksOnHome = v) }),
    Setting.Action(SettingsCategory.TASKS, "Clear finished tasks", "Deletes every ticked task", "delete done", SettingsAction.CLEAR_DONE_TASKS, danger = true),

    // reminders
    Setting.Toggle(
        SettingsCategory.REMINDERS, "Reminders", "Turn off to stop every reminder from Atlas", "notifications notify alerts remind",
        get = { it.remindersOn }, set = { s, v -> s.copy(remindersOn = v) },
    ),
    Setting.Choice(
        SettingsCategory.REMINDERS, "Morning reminder time", "When birthdays, tasks due and all day events remind you", "morning time notification",
        options = (12..20).map { it * 30 } + listOf(660, 720), label = { formatMinuteOfDay(it) },
        get = { it.reminderMinute }, set = { s, v -> s.copy(reminderMinute = v) },
        labelWith = { s, v -> formatMinuteOfDay(v, s.use24Hour) },
    ),
    Setting.Toggle(
        SettingsCategory.REMINDERS, "Task reminders", "On the morning a task is due", "task due homework notification",
        get = { it.remindTasks }, set = { s, v -> s.copy(remindTasks = v) },
    ),
    Setting.Toggle(
        SettingsCategory.REMINDERS, "Event reminders", "Before an event starts, or on the morning of an all day one", "calendar event notification",
        get = { it.remindEvents }, set = { s, v -> s.copy(remindEvents = v) },
    ),
    Setting.Choice(
        SettingsCategory.REMINDERS, "Before events", "How early events remind you. Each event can change this", "event minutes early alert",
        options = listOf(NO_REMINDER) + ReminderMinuteOptions, label = ::reminderMinutesLabel,
        get = { it.eventReminderMinutes }, set = { s, v -> s.copy(eventReminderMinutes = v) },
    ),
    Setting.Toggle(
        SettingsCategory.REMINDERS, "Countdown reminders", "Birthdays, anniversaries and other big dates", "countdown birthday anniversary notification",
        get = { it.remindCountdowns }, set = { s, v -> s.copy(remindCountdowns = v) },
    ),
    Setting.Choice(
        SettingsCategory.REMINDERS, "Early countdown reminder", "An extra reminder before the day itself", "countdown birthday early days before",
        options = listOf(0, 1, 2, 3, 7),
        label = {
            when (it) {
                0 -> "Only on the day"
                1 -> "1 day before"
                7 -> "A week before"
                else -> "$it days before"
            }
        },
        get = { it.countdownDaysBefore }, set = { s, v -> s.copy(countdownDaysBefore = v) },
    ),
    Setting.Action(
        SettingsCategory.REMINDERS, "Notification settings", "Sound, vibration and whether Atlas can notify you", "notifications sound vibrate allow blocked permission",
        SettingsAction.NOTIFICATION_SETTINGS,
    ),
    Setting.Action(SettingsCategory.REMINDERS, "Send a test reminder", "Check that reminders get through", "test notification", SettingsAction.TEST_REMINDER),

    // focus
    Setting.Range(SettingsCategory.FOCUS, "Focus length", keywords = "pomodoro session", min = 5f, max = 90f, steps = 16, format = ::minutes, get = { it.focusMinutes.toFloat() }, set = { s, v -> s.copy(focusMinutes = v.roundToInt()) }),
    Setting.Range(SettingsCategory.FOCUS, "Short break", keywords = "pomodoro break", min = 1f, max = 30f, steps = 28, format = ::minutes, get = { it.shortBreakMinutes.toFloat() }, set = { s, v -> s.copy(shortBreakMinutes = v.roundToInt()) }),
    Setting.Range(SettingsCategory.FOCUS, "Long break", keywords = "pomodoro break", min = 5f, max = 60f, steps = 10, format = ::minutes, get = { it.longBreakMinutes.toFloat() }, set = { s, v -> s.copy(longBreakMinutes = v.roundToInt()) }),
    Setting.Choice(
        SettingsCategory.FOCUS, "Sessions before a long break", keywords = "pomodoro", options = (2..8).toList(), label = { "$it" },
        get = { it.sessionsBeforeLongBreak }, set = { s, v -> s.copy(sessionsBeforeLongBreak = v) },
    ),
    Setting.Toggle(SettingsCategory.FOCUS, "Start the next one automatically", "Breaks and sessions roll on without a tap", "auto start", get = { it.autoStartNext }, set = { s, v -> s.copy(autoStartNext = v) }),
    Setting.Toggle(SettingsCategory.FOCUS, "Keep the screen on", "While the focus screen is open", "screen awake", get = { it.keepScreenOn }, set = { s, v -> s.copy(keepScreenOn = v) }),
    Setting.Action(SettingsCategory.FOCUS, "Log focus time to a tracker", "Finished sessions get saved to a timer tracker", "tracker log study", SettingsAction.PICK_FOCUS_TRACKER),

    // explorer
    Setting.Choice(SettingsCategory.EXPLORER, "Sort by", keywords = "order", options = ExplorerSort.entries, label = { it.label }, get = { it.explorerSort }, set = { s, v -> s.copy(explorerSort = v) }),
    Setting.Toggle(SettingsCategory.EXPLORER, "Folders first", get = { it.foldersFirst }, set = { s, v -> s.copy(foldersFirst = v) }),
    Setting.Toggle(SettingsCategory.EXPLORER, "Grid view", "Show items as tiles instead of a list", "grid tiles list", get = { it.explorerGrid }, set = { s, v -> s.copy(explorerGrid = v) }),
    Setting.Choice(
        SettingsCategory.EXPLORER, "Keep trash for", keywords = "trash delete days", options = listOf(7, 14, 30, 60, 0),
        label = { if (it == 0) "Forever" else "$it days" }, get = { it.trashDays }, set = { s, v -> s.copy(trashDays = v) },
    ),
    Setting.Toggle(SettingsCategory.EXPLORER, "Confirm before moving to trash", get = { it.confirmTrash }, set = { s, v -> s.copy(confirmTrash = v) }),
    Setting.Action(SettingsCategory.EXPLORER, "Empty trash now", "Deletes everything in the trash for good", "trash delete", SettingsAction.EMPTY_TRASH, danger = true),

    // tools
    Setting.Toggle(SettingsCategory.TOOLS, "Metric units first", "Turn off to start converters in imperial", "units metric imperial", get = { it.metricUnits }, set = { s, v -> s.copy(metricUnits = v) }),
    Setting.TextValue(SettingsCategory.TOOLS, "Currency symbol", "Used by the tip and bill splitter", "money currency", get = { it.currencySymbol }, set = { s, v -> s.copy(currencySymbol = v.take(3)) }),

    // privacy
    Setting.Toggle(
        SettingsCategory.PRIVACY, "Hide in recent apps", "Blanks Atlas in the app switcher. This also blocks screenshots", "secure screenshot recents",
        get = { it.hideInRecents }, set = { s, v -> s.copy(hideInRecents = v) },
    ),

    // data
    Setting.Action(SettingsCategory.DATA, "Sync with another device", "Swap changes with your phone or PC over Wi-Fi", "sync wifi pc phone computer merge", SettingsAction.SYNC),
    Setting.Action(SettingsCategory.DATA, "Back up now", "Save everything to a file you choose", "backup export save", SettingsAction.BACKUP),
    Setting.Action(SettingsCategory.DATA, "Restore from backup", "Replaces what is in the app with a backup file", "restore import", SettingsAction.RESTORE),
    Setting.Action(SettingsCategory.DATA, "Starter packs", "Add ready made trackers and notes for study, fitness and more", "starter pack template", SettingsAction.STARTER_PACKS),
    Setting.Action(SettingsCategory.DATA, "Reset all settings", "Puts every setting back to default. Your notes and data stay", "reset default", SettingsAction.RESET_SETTINGS, danger = true),
)

// these only mean something on a phone, the pc version leaves them out
private val PhoneOnly = setOf("Wallpaper colours", "Hide in recent apps", "Haptics", "Keep the screen on")

@Composable
fun visibleSettings(): List<Setting> {
    val pc = platform().kind == DeviceKind.PC
    return remember(pc) { if (pc) AllSettings.filter { it.title !in PhoneOnly } else AllSettings }
}

fun Setting.matches(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    return listOf(title, summary, keywords, category.title).any { it.lowercase().contains(q) }
}
