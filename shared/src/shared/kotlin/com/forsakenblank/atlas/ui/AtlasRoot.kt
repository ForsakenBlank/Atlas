package com.forsakenblank.atlas.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.delay
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.forsakenblank.atlas.data.AppSettings
import com.forsakenblank.atlas.data.BarLabels
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.data.Transition
import com.forsakenblank.atlas.ui.calendar.CalendarScreen
import com.forsakenblank.atlas.ui.calendar.EventEditorScreen
import com.forsakenblank.atlas.ui.common.atlasApp
import com.forsakenblank.atlas.ui.countdowns.CountdownEditorScreen
import com.forsakenblank.atlas.ui.countdowns.CountdownsScreen
import com.forsakenblank.atlas.ui.explorer.ExplorerScreen
import com.forsakenblank.atlas.ui.explorer.TrashScreen
import com.forsakenblank.atlas.ui.focus.FocusScreen
import com.forsakenblank.atlas.ui.home.HomeScreen
import com.forsakenblank.atlas.ui.money.MoneyScreen
import com.forsakenblank.atlas.ui.money.TaxScreen
import com.forsakenblank.atlas.ui.notes.NoteEditorScreen
import com.forsakenblank.atlas.ui.notes.NotesScreen
import com.forsakenblank.atlas.ui.sheets.SheetEditorScreen
import com.forsakenblank.atlas.ui.sheets.SheetsScreen
import com.forsakenblank.atlas.ui.onboarding.StarterPacksScreen
import com.forsakenblank.atlas.ui.search.SearchScreen
import com.forsakenblank.atlas.ui.settings.SettingsCategoryScreen
import com.forsakenblank.atlas.ui.settings.SettingsScreen
import com.forsakenblank.atlas.ui.settings.ThemeEditorScreen
import com.forsakenblank.atlas.ui.settings.ThemesScreen
import com.forsakenblank.atlas.ui.tasks.TasksScreen
import com.forsakenblank.atlas.ui.theme.LocalSettings
import com.forsakenblank.atlas.ui.timetable.SubjectsScreen
import com.forsakenblank.atlas.ui.timetable.TermsScreen
import com.forsakenblank.atlas.ui.timetable.TimetableScreen
import com.forsakenblank.atlas.ui.tools.ToolScreen
import com.forsakenblank.atlas.ui.tools.ToolsScreen
import com.forsakenblank.atlas.ui.track.TrackScreen
import com.forsakenblank.atlas.ui.track.TrackerDetailScreen
import com.forsakenblank.atlas.platform.BackHandler
import com.forsakenblank.atlas.platform.DeviceKind
import com.forsakenblank.atlas.platform.platform
import androidx.savedstate.read
import androidx.compose.material.icons.outlined.Sync
import com.forsakenblank.atlas.ui.sync.SyncScreen
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.filled.AutoStories
import com.forsakenblank.atlas.ui.insights.InsightsScreen
import com.forsakenblank.atlas.ui.habits.HabitsScreen
import com.forsakenblank.atlas.ui.journal.JournalScreen
import com.forsakenblank.atlas.Launch

fun sectionIcon(section: Section, selected: Boolean): ImageVector = when (section) {
    Section.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    Section.NOTES -> if (selected) Icons.Filled.EditNote else Icons.Outlined.EditNote
    Section.SHEETS -> if (selected) Icons.Filled.TableChart else Icons.Outlined.TableChart
    Section.CALENDAR -> if (selected) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth
    Section.TIMETABLE -> if (selected) Icons.Filled.School else Icons.Outlined.School
    Section.TASKS -> if (selected) Icons.Filled.Checklist else Icons.Outlined.Checklist
    Section.COUNTDOWNS -> if (selected) Icons.Filled.Cake else Icons.Outlined.Cake
    Section.MONEY -> if (selected) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
    Section.TRACK -> if (selected) Icons.Filled.Insights else Icons.Outlined.Insights
    Section.FOCUS -> if (selected) Icons.Filled.Timer else Icons.Outlined.Timer
    Section.TOOLS -> if (selected) Icons.Filled.Calculate else Icons.Outlined.Calculate
    Section.JOURNAL -> if (selected) Icons.Filled.AutoStories else Icons.Outlined.AutoStories
    Section.HABITS -> if (selected) Icons.Filled.TaskAlt else Icons.Outlined.TaskAlt
    Section.INSIGHTS -> if (selected) Icons.Filled.Insights else Icons.Outlined.Insights
    Section.EXPLORER -> if (selected) Icons.Filled.FolderOpen else Icons.Outlined.FolderOpen
}

fun AppSettings.visibleTabs(): List<Section> = tabs.filter { it !in hiddenSections }.distinct().ifEmpty { listOf(Section.HOME) }

// the fades are staged, the screen you are leaving goes first and the new one only starts
// once it has gone, otherwise both are on screen at once and the old one looks like it is stuck
private fun fadeOutPart(ms: Int) = (ms * 0.35f).toInt().coerceIn(40, 200)

private fun enterFor(transition: Transition, ms: Int, forward: Boolean): EnterTransition {
    val gone = fadeOutPart(ms)
    val rest = (ms - gone).coerceAtLeast(60)
    val lateFade = fadeIn(tween(rest, delayMillis = gone))
    return when (transition) {
        // a full width slide covers the old screen on its own, so it needs no delay
        Transition.SLIDE -> slideInHorizontally(tween(ms)) { if (forward) it else -it / 3 }
        Transition.FADE -> lateFade
        Transition.ZOOM -> scaleIn(tween(rest, delayMillis = gone), initialScale = if (forward) 0.92f else 1.04f) + lateFade
        Transition.SLIDE_UP -> if (forward) slideInVertically(tween(rest, delayMillis = gone)) { it / 4 } + lateFade else lateFade
        Transition.SHARED_AXIS -> slideInHorizontally(tween(rest, delayMillis = gone)) { (if (forward) it else -it) / 6 } + lateFade
        Transition.NONE -> EnterTransition.None
    }
}

private fun exitFor(transition: Transition, ms: Int, forward: Boolean): ExitTransition {
    val gone = fadeOutPart(ms)
    val quickFade = fadeOut(tween(gone))
    return when (transition) {
        Transition.SLIDE -> slideOutHorizontally(tween(ms)) { if (forward) -it / 3 else it }
        Transition.FADE -> quickFade
        Transition.ZOOM -> scaleOut(tween(gone), targetScale = if (forward) 1.04f else 0.92f) + quickFade
        Transition.SLIDE_UP -> if (forward) quickFade else slideOutVertically(tween(gone)) { it / 4 } + quickFade
        Transition.SHARED_AXIS -> slideOutHorizontally(tween(gone)) { (if (forward) -it else it) / 6 } + quickFade
        Transition.NONE -> ExitTransition.None
    }
}

// works out which animation a route change gets from the motion settings
private class Motion(private val settings: () -> AppSettings) {

    private fun pick(scope: AnimatedContentTransitionScope<NavBackStackEntry>, pop: Boolean): Triple<Transition, Int, Boolean> {
        val s = settings()
        val ms = (300 * s.animationSpeed).toInt().coerceIn(60, 1200)
        val tabs = s.visibleTabs()
        val from = Section.fromRoute(scope.initialState.destination.route)
        val to = Section.fromRoute(scope.targetState.destination.route)
        if (s.reduceMotion) return Triple(Transition.NONE, ms, true)
        if (from != null && to != null && from in tabs && to in tabs) {
            // tabs slide the way the bar reads, left to right
            return Triple(s.tabTransition, ms, tabs.indexOf(to) >= tabs.indexOf(from))
        }
        return Triple(s.screenTransition, ms, !pop)
    }

    // switching tabs should never leave the old tab sitting there, so it is gone in a frame
    // and the new one fades or slides in on its own
    private fun isTabSwitch(scope: AnimatedContentTransitionScope<NavBackStackEntry>): Boolean {
        val from = Section.fromRoute(scope.initialState.destination.route)
        val to = Section.fromRoute(scope.targetState.destination.route)
        return from != null && to != null
    }

    fun enter(scope: AnimatedContentTransitionScope<NavBackStackEntry>, pop: Boolean): EnterTransition {
        val (t, ms, forward) = pick(scope, pop)
        if (isTabSwitch(scope) && t != Transition.NONE) {
            return fadeIn(tween((ms * 0.5f).toInt().coerceIn(60, 220)))
        }
        return enterFor(t, ms, forward)
    }

    fun exit(scope: AnimatedContentTransitionScope<NavBackStackEntry>, pop: Boolean): ExitTransition {
        val (t, ms, forward) = pick(scope, pop)
        if (isTabSwitch(scope) && t != Transition.NONE) return fadeOut(tween(1))
        return exitFor(t, ms, forward)
    }
}

@Composable
fun AtlasRoot() = BoxWithConstraints(Modifier.fillMaxSize()) {
    // a pc window or a tablet gets every section in a rail down the side instead of five tabs at the bottom
    AtlasRootContent(wide = maxWidth >= 840.dp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AtlasRootContent(wide: Boolean) {
    val settings = LocalSettings.current
    val app = atlasApp()
    val nav = rememberNavController()
    val navigator = remember(nav) { AtlasNavigator(nav) }
    val snackbar = remember { SnackbarHostState() }
    // shortcuts, the widget and the quick settings tile land here
    LaunchedEffect(Unit) {
        app.launches.collect { launch ->
            app.launches.resetReplayCache()
            when (launch) {
                Launch.NEW_NOTE -> navigator.openNote(app.repository.createNote(parentId = null))
                Launch.TASKS -> navigator.openSection(Section.TASKS, isTab = false)
                Launch.JOURNAL -> navigator.openSection(Section.JOURNAL, isTab = false)
                Launch.HABITS -> navigator.openSection(Section.HABITS, isTab = false)
                Launch.SYNC -> navigator.openSync()
                Launch.FOCUS -> {
                    navigator.openSection(Section.FOCUS, isTab = false)
                    if (!app.focusTimer.state.value.running) app.focusTimer.start()
                }
            }
        }
    }
    // on the pc, escape and the mouse back button step back a page like the phone's back gesture
    if (platform().kind == DeviceKind.PC) BackHandler { navigator.back() }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tabs = settings.visibleTabs()
    val navTabs = if (wide) Section.entries.filter { it !in settings.hiddenSections } else tabs
    val section = Section.fromRoute(route)
    val onTab = section != null && section in navTabs

    var sectionsMenu by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(settings)
    val motion = remember { Motion { latest } }

    // the start screen is picked once, a section that is not a tab opens on top of the first tab
    val wanted = rememberSaveable {
        (settings.startSection ?: settings.lastSection).takeIf { it !in settings.hiddenSections } ?: Section.HOME
    }
    val startTab = rememberSaveable { if (wanted in tabs) wanted else if (Section.HOME in tabs) Section.HOME else tabs.first() }
    var openedStart by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!openedStart && wanted != startTab) navigator.openSection(wanted, isTab = false)
        openedStart = true
    }
    LaunchedEffect(section) {
        // saving rebuilds the whole theme tree, so it waits until the page change has finished
        delay(700)
        if (section != null && settings.startSection == null && section != settings.lastSection) {
            app.updateSettings { it.copy(lastSection = section) }
        }
    }

    CompositionLocalProvider(LocalSnackbar provides snackbar) {
        Row(Modifier.fillMaxSize()) {
            if (wide) SideRail(navTabs, section, onSync = navigator::openSync) { navigator.openSection(it, isTab = true) }
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    if (section != null) {
                        TopAppBar(
                            title = { Text(if (section == Section.HOME) "Atlas" else section.label) },
                            navigationIcon = {
                                if (!onTab) {
                                    IconButton(onClick = navigator::back) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                // every section that is not a tab stays one tap away from anywhere
                                val others = Section.entries.filter { it !in navTabs && it !in settings.hiddenSections && it != section }
                                if (others.isNotEmpty()) {
                                    Box {
                                        IconButton(onClick = { sectionsMenu = true }) {
                                            Icon(Icons.Outlined.Apps, contentDescription = "All sections")
                                        }
                                        DropdownMenu(expanded = sectionsMenu, onDismissRequest = { sectionsMenu = false }) {
                                            others.forEach { other ->
                                                DropdownMenuItem(
                                                    text = { Text(other.label) },
                                                    leadingIcon = { Icon(sectionIcon(other, selected = false), contentDescription = null) },
                                                    onClick = {
                                                        sectionsMenu = false
                                                        navigator.openSection(other, isTab = false)
                                                    },
                                                )
                                            }
                                        }
                                    }
                                }
                                if (!wide) {
                                    IconButton(onClick = navigator::openSync) {
                                        Icon(Icons.Outlined.Sync, contentDescription = "Sync")
                                    }
                                }
                                IconButton(onClick = navigator::openSearch) {
                                    Icon(Icons.Outlined.Search, contentDescription = "Search")
                                }
                                IconButton(onClick = navigator::openSettings) {
                                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                                }
                            },
                        )
                    }
                },
                bottomBar = {
                    if (!wide && onTab && tabs.size > 1) {
                        BottomBar(tabs = tabs, current = section, settings = settings) { navigator.openSection(it, isTab = true) }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
                // the bars handle system insets, pages bring their own scaffold
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = startTab.route,
                    modifier = Modifier
                        .padding(padding)
                        .then(if (section != null && (wide || !(onTab && tabs.size > 1))) Modifier.navigationBarsPadding() else Modifier),
                    enterTransition = { motion.enter(this, pop = false) },
                    exitTransition = { motion.exit(this, pop = false) },
                    popEnterTransition = { motion.enter(this, pop = true) },
                    popExitTransition = { motion.exit(this, pop = true) },
                ) {
                    composable(Section.HOME.route) { Page { HomeScreen(navigator) } }
                    composable(Section.NOTES.route) { Page { NotesScreen(navigator) } }
                    composable(Section.SHEETS.route) { Page { SheetsScreen(navigator) } }
                    composable(Section.CALENDAR.route) { Page { CalendarScreen(navigator) } }
                    composable(Section.TIMETABLE.route) { Page { TimetableScreen(navigator) } }
                    composable(Section.TASKS.route) { Page { TasksScreen(navigator) } }
                    composable(Section.COUNTDOWNS.route) { Page { CountdownsScreen(navigator) } }
                    composable(Section.MONEY.route) { Page { MoneyScreen(navigator) } }
                    composable(Routes.TAX) { Page { TaxScreen(navigator) } }
                    composable(Section.TRACK.route) { Page { TrackScreen(navigator) } }
                    composable(Section.JOURNAL.route) { Page { JournalScreen(navigator) } }
                    composable(Section.HABITS.route) { Page { HabitsScreen(navigator) } }
                    composable(Section.INSIGHTS.route) { Page { InsightsScreen(navigator) } }
                    composable(Section.FOCUS.route) { Page { FocusScreen(navigator) } }
                    composable(Section.TOOLS.route) { Page { ToolsScreen(navigator) } }
                    composable(Section.EXPLORER.route) { Page { ExplorerScreen(navigator) } }

                    composable(Routes.NOTE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        NoteEditorScreen(entry.long("id"), navigator)
                    }
                    composable(Routes.SHEET, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        SheetEditorScreen(entry.long("id"), navigator)
                    }
                    composable(Routes.TRACKER, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        TrackerDetailScreen(entry.long("id"), navigator)
                    }
                    composable(Routes.SETTINGS) { SettingsScreen(navigator) }
                    composable(Routes.SETTINGS_CATEGORY, arguments = listOf(navArgument("category") { type = NavType.StringType })) { entry ->
                        SettingsCategoryScreen(entry.string("category").orEmpty(), navigator)
                    }
                    composable(Routes.THEMES) { ThemesScreen(navigator) }
                    composable(Routes.THEME_EDITOR, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        ThemeEditorScreen(entry.string("id") ?: "new", navigator)
                    }
                    composable(Routes.TRASH) { TrashScreen(navigator) }
                    composable(Routes.SYNC) { SyncScreen(navigator) }
                    composable(Routes.SEARCH) { SearchScreen(navigator) }
                    composable(
                        Routes.EVENT,
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("day") {
                                type = NavType.LongType
                                defaultValue = 0L
                            },
                        ),
                    ) { entry ->
                        EventEditorScreen(entry.long("id"), entry.long("day"), navigator)
                    }
                    composable(Routes.SUBJECTS) { SubjectsScreen(navigator) }
                    composable(Routes.TERMS) { TermsScreen(navigator) }
                    composable(Routes.STARTER_PACKS) { StarterPacksScreen(navigator) }
                    composable(Routes.TOOL, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                        ToolScreen(entry.string("id").orEmpty(), navigator)
                    }
                    composable(
                        Routes.COUNTDOWN,
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("kind") {
                                type = NavType.StringType
                                defaultValue = "EVENT"
                            },
                            navArgument("countUp") {
                                type = NavType.BoolType
                                defaultValue = false
                            },
                            navArgument("day") {
                                type = NavType.LongType
                                defaultValue = 0L
                            },
                        ),
                    ) { entry ->
                        CountdownEditorScreen(
                            id = entry.long("id"),
                            kind = entry.string("kind").orEmpty(),
                            countUp = entry.flag("countUp"),
                            day = entry.long("day"),
                            navigator = navigator,
                        )
                    }
                }
            }
        }
    }
}

// every section down the side, scrolls when the window is short
@Composable
private fun SideRail(sections: List<Section>, current: Section?, onSync: () -> Unit, onOpen: (Section) -> Unit) {
    NavigationRail(
        header = {
            FloatingActionButton(onClick = onSync, elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()) {
                Icon(Icons.Outlined.Sync, contentDescription = "Sync")
            }
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sections.forEach { item ->
                val selected = item == current
                NavigationRailItem(
                    selected = selected,
                    onClick = { if (!selected) onOpen(item) },
                    icon = { Icon(sectionIcon(item, selected), contentDescription = item.label) },
                    label = { Text(item.label, maxLines = 1) },
                )
            }
        }
    }
}

// tab pages have no scaffold of their own, without a solid back the old page shows through the new one
@Composable
private fun Page(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
}

@Composable
private fun BottomBar(tabs: List<Section>, current: Section?, settings: AppSettings, onOpen: (Section) -> Unit) {
    val bar: @Composable () -> Unit = {
        NavigationBar(
            containerColor = if (settings.floatingBar) Color.Transparent else NavigationBarDefaults.containerColor,
            tonalElevation = if (settings.floatingBar) 0.dp else NavigationBarDefaults.Elevation,
            windowInsets = if (settings.floatingBar) WindowInsets(0, 0, 0, 0) else NavigationBarDefaults.windowInsets,
        ) {
            tabs.forEach { tab ->
                val selected = tab == current
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) onOpen(tab) },
                    icon = { Icon(sectionIcon(tab, selected), contentDescription = tab.label) },
                    label = if (settings.barLabels == BarLabels.NEVER) null else {
                        { Text(tab.label, maxLines = 1) }
                    },
                    alwaysShowLabel = settings.barLabels == BarLabels.ALWAYS,
                )
            }
        }
    }
    if (settings.floatingBar) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            bar()
        }
    } else {
        bar()
    }
}

// route arguments read the same way on the phone and the pc
private fun NavBackStackEntry.long(name: String): Long = arguments?.read { if (contains(name)) getLong(name) else null } ?: 0L

private fun NavBackStackEntry.string(name: String): String? = arguments?.read { if (contains(name)) getString(name) else null }

private fun NavBackStackEntry.flag(name: String): Boolean = arguments?.read { if (contains(name)) getBoolean(name) else null } ?: false
