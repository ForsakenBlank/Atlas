package com.forsakenblank.atlas.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.data.Section
import com.forsakenblank.atlas.ui.settings.SettingsCategory

// one snackbar host for the whole app so "Logged. Undo" survives tab switches
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

object Routes {
    const val NOTE = "note/{id}"
    const val SHEET = "sheet/{id}"
    const val TRACKER = "tracker/{id}"
    const val SETTINGS = "settings"
    const val SETTINGS_CATEGORY = "settings/{category}"
    const val THEMES = "themes"
    const val THEME_EDITOR = "theme/{id}"
    const val TRASH = "trash"
    const val SEARCH = "search"
    const val EVENT = "event/{id}?day={day}"
    const val SUBJECTS = "subjects"
    const val TERMS = "terms"
    const val STARTER_PACKS = "starter-packs"
    const val TOOL = "tool/{id}"
    const val TAX = "tax"
    const val SYNC = "sync"
    const val COUNTDOWN = "countdown/{id}?kind={kind}&countUp={countUp}&day={day}"
}

class AtlasNavigator(private val nav: NavHostController) {
    fun openNote(id: Long) = nav.navigate("note/$id")
    fun openSheet(id: Long) = nav.navigate("sheet/$id")
    fun openTracker(id: Long) = nav.navigate("tracker/$id")
    fun openSettings() = nav.navigate(Routes.SETTINGS)
    fun openSettingsCategory(category: SettingsCategory) = nav.navigate("settings/${category.name}")
    fun openThemes() = nav.navigate(Routes.THEMES)
    fun openThemeEditor(id: String) = nav.navigate("theme/$id")
    fun openTrash() = nav.navigate(Routes.TRASH)
    fun openSearch() = nav.navigate(Routes.SEARCH) { launchSingleTop = true }
    fun openEvent(id: Long?, day: Long? = null) = nav.navigate("event/${id ?: 0}?day=${day ?: 0}")
    fun openSubjects() = nav.navigate(Routes.SUBJECTS)
    fun openTerms() = nav.navigate(Routes.TERMS)
    fun openStarterPacks() = nav.navigate(Routes.STARTER_PACKS)
    fun openTool(id: String) = nav.navigate("tool/$id")
    fun openTax() = nav.navigate(Routes.TAX) { launchSingleTop = true }
    fun openSync() = nav.navigate(Routes.SYNC) { launchSingleTop = true }

    // a new one can start as a birthday, an anniversary or a days since, on a given epoch day
    fun openCountdown(id: Long?, kind: CountdownKind? = null, countUp: Boolean = false, day: Long? = null) =
        nav.navigate("countdown/${id ?: 0}?kind=${(kind ?: CountdownKind.EVENT).name}&countUp=$countUp&day=${day ?: 0}")

    // tabs keep their own back stacks, other sections open on top like a page
    fun openSection(section: Section, isTab: Boolean) {
        if (isTab) {
            nav.navigate(section.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            nav.navigate(section.route) { launchSingleTop = true }
        }
    }

    // never pops the last page, the pc would be left with an empty window
    fun back() {
        if (nav.previousBackStackEntry != null) nav.popBackStack()
    }
}
