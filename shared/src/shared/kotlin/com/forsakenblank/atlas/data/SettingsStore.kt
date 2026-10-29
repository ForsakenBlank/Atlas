package com.forsakenblank.atlas.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.forsakenblank.atlas.ui.theme.AtlasTheme
import com.forsakenblank.atlas.ui.theme.BuiltInThemes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class Transition(val label: String) {
    SLIDE("Slide"),
    FADE("Fade"),
    ZOOM("Zoom"),
    SLIDE_UP("Slide up"),
    SHARED_AXIS("Push"),
    NONE("None"),
}

enum class FontChoice(val label: String) { DEFAULT("Default"), SANS("Sans"), SERIF("Serif"), MONO("Monospace"), CURSIVE("Handwritten") }

enum class CardStyle(val label: String) { FILLED("Filled"), OUTLINED("Outlined"), ELEVATED("Raised"), GLASS("Glass") }

enum class IconStyle(val label: String, val pkg: String) {
    OUTLINED("Outlined (thin lines)", "outlined"),
    ROUNDED("Rounded (soft and filled)", "rounded"),
    SHARP("Sharp (square corners)", "sharp"),
    FILLED("Filled", "filled"),
    TWO_TONE("Two tone", "twotone"),
}

enum class BarLabels(val label: String) { ALWAYS("Always"), SELECTED("Selected tab only"), NEVER("Never") }

enum class ShortcutSize(val label: String, val minWidth: Int) { SMALL("Small", 110), NORMAL("Normal", 156), LARGE("Large", 220) }

enum class LongPress(val label: String) { SELECT("Start selecting"), OPEN("Open the tracker") }

enum class NoteSort(val label: String) { EDITED("Last edited"), CREATED("Date created"), TITLE("Title"), COLOUR("Colour") }

enum class NoteLayout(val label: String) { LIST("List"), GRID("Grid"), COMPACT("Compact") }

enum class TextSize(val label: String, val scale: Float) { SMALL("Small", 0.9f), NORMAL("Normal", 1f), LARGE("Large", 1.15f), HUGE("Huge", 1.3f) }

enum class ExplorerSort(val label: String) { NAME("Name"), EDITED("Last edited"), CREATED("Date created"), TYPE("Type") }

enum class TaskSort(val label: String) { DUE("Due date"), PRIORITY("Priority"), CREATED("Date added"), TITLE("Title") }

enum class CalendarView(val label: String) { MONTH("Month"), WEEK("Week"), AGENDA("Agenda") }

enum class AppIcon(val label: String, val alias: String) {
    ORANGE("Orange and cream", "IconOrange"),
    NAVY("Navy and mint", "IconNavy"),
    MONO("Black and white", "IconMono"),
    PURPLE("Purple and pink", "IconPurple"),
    FOREST("Forest green", "IconForest"),
}

@Serializable
data class AppSettings(
    // appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val lightThemeId: String = BuiltInThemes.DEFAULT_LIGHT,
    val darkThemeId: String = BuiltInThemes.DEFAULT_DARK,
    val customThemes: List<AtlasTheme> = emptyList(),
    // a sync with Luminescent switches to the theme Luminescent is using
    val followLuminescent: Boolean = false,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val cornerRadius: Int = 16,
    val fontScale: Float = 1f,
    val uiScale: Float = 1f,
    val font: FontChoice = FontChoice.DEFAULT,
    val boldHeadings: Boolean = false,
    val cardStyle: CardStyle = CardStyle.FILLED,
    val iconStyle: IconStyle = IconStyle.OUTLINED,
    val appIcon: AppIcon = AppIcon.ORANGE,

    // navigation
    val tabs: List<Section> = Section.defaultTabs,
    val hiddenSections: Set<Section> = emptySet(),
    val startSection: Section? = Section.HOME, // null opens wherever you left off
    val lastSection: Section = Section.HOME,
    val barLabels: BarLabels = BarLabels.ALWAYS,
    val floatingBar: Boolean = false,
    val showQuickAdd: Boolean = true,

    // motion
    val screenTransition: Transition = Transition.SLIDE,
    val tabTransition: Transition = Transition.FADE,
    val animationSpeed: Float = 1f, // bigger is slower
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    val celebrateGoals: Boolean = true,

    // home
    val userName: String = "",
    val showGreeting: Boolean = true,
    val showNowNext: Boolean = true,
    val showAgenda: Boolean = true,
    val showSectionsRow: Boolean = true,
    val showPinnedNotes: Boolean = true,
    val showCountdowns: Boolean = true,
    val shortcutColumns: Int = 0, // 0 fits as many as the screen allows
    val shortcutSize: ShortcutSize = ShortcutSize.NORMAL,
    val shortcutLongPress: LongPress = LongPress.SELECT,

    // notes
    val noteSort: NoteSort = NoteSort.EDITED,
    val noteLayout: NoteLayout = NoteLayout.LIST,
    val notePreviewLines: Int = 2,
    val showWordCount: Boolean = true,
    val editorTextSize: TextSize = TextSize.NORMAL,
    val pinnedFirst: Boolean = true,
    val showNoteDates: Boolean = true,

    // track
    val showUndo: Boolean = true,
    val chartDays: Int = 14,
    val showStreaksOnCards: Boolean = false,
    val confirmLogDelete: Boolean = false,

    // calendar
    val weekStartsMonday: Boolean = true,
    val use24Hour: Boolean = true,
    val calendarView: CalendarView = CalendarView.MONTH,
    val showTrackerDots: Boolean = true,
    val showTasksOnCalendar: Boolean = true,
    val showClassesOnCalendar: Boolean = true,
    val showWeekNumbers: Boolean = false,
    val defaultEventMinutes: Int = 60,

    // timetable
    val twoWeekTimetable: Boolean = false,
    val weekAStart: Long = 0, // epoch day of a monday in week A
    val timetableWeekends: Boolean = false,
    val timetableStartHour: Int = 8,
    val timetableEndHour: Int = 18,
    val makeSubjectFolders: Boolean = true,

    // tasks
    val showCompletedTasks: Boolean = true,
    val taskSort: TaskSort = TaskSort.DUE,
    val tasksOnHome: Boolean = true,

    // reminders
    val remindersOn: Boolean = true,
    val reminderMinute: Int = 480, // minute of the day for birthdays, tasks and all day events
    val eventReminderMinutes: Int = 15, // -1 means no reminder unless the event asks for one
    val countdownDaysBefore: Int = 1, // 0 only reminds on the day
    val remindTasks: Boolean = true,
    val remindEvents: Boolean = true,
    val remindCountdowns: Boolean = true,
    val askedNotifications: Boolean = false,
    // the address typed last time a sync connected, so it is filled in next time
    val lastSyncAddress: String = "",

    // focus
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
    val autoStartNext: Boolean = false,
    val focusTrackerId: Long? = null,
    val keepScreenOn: Boolean = true,

    // explorer
    val explorerSort: ExplorerSort = ExplorerSort.NAME,
    val foldersFirst: Boolean = true,
    val explorerGrid: Boolean = false,
    val trashDays: Int = 30,
    val confirmTrash: Boolean = false,

    // privacy
    val hideInRecents: Boolean = false,

    // tools
    val metricUnits: Boolean = true,
    val currencySymbol: String = "£",

    val onboarded: Boolean = false,
)

class SettingsStore(private val dataStore: DataStore<Preferences>) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        coerceInputValues = true
    }

    private val blobKey = stringPreferencesKey("app_settings")

    // keys from the first version, read once so nobody loses their theme choice
    private val oldThemeMode = stringPreferencesKey("theme_mode")
    private val oldDynamic = booleanPreferencesKey("dynamic_color")
    private val oldPureBlack = booleanPreferencesKey("pure_black")

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        prefs[blobKey]?.let { decode(it) } ?: AppSettings(
            themeMode = prefs[oldThemeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs[oldDynamic] ?: false,
            pureBlack = prefs[oldPureBlack] ?: false,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { prefs ->
            val current = prefs[blobKey]?.let { decode(it) } ?: AppSettings(
                themeMode = prefs[oldThemeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                dynamicColor = prefs[oldDynamic] ?: false,
                pureBlack = prefs[oldPureBlack] ?: false,
            )
            prefs[blobKey] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    suspend fun reset(keepThemes: Boolean) {
        update { old ->
            if (keepThemes) AppSettings(customThemes = old.customThemes, onboarded = true) else AppSettings(onboarded = true)
        }
    }

    // names this install to other devices when syncing, kept out of the settings blob so it never travels
    private val deviceIdKey = stringPreferencesKey("device_id")

    suspend fun deviceId(): String {
        dataStore.data.first()[deviceIdKey]?.let { return it }
        var id = ""
        dataStore.edit { prefs -> id = prefs[deviceIdKey] ?: java.util.UUID.randomUUID().toString().also { prefs[deviceIdKey] = it } }
        return id
    }

    private fun decode(text: String): AppSettings? = runCatching { json.decodeFromString(AppSettings.serializer(), text) }.getOrNull()
}
