package com.forsakenblank.atlas

import android.app.Application
import com.forsakenblank.atlas.extras.TodayWidget
import com.forsakenblank.atlas.notify.createReminderChannel
import com.forsakenblank.atlas.platform.buildAtlasDatabase
import com.forsakenblank.atlas.platform.settingsDataStore
import com.forsakenblank.atlas.util.startOfDay
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class AtlasApplication : Application() {

    // everything else lives in the shared AtlasApp, the phone only adds how to reach android
    val atlas by lazy { AtlasApp(AndroidPlatform(this), buildAtlasDatabase(this), settingsDataStore(this)) }

    override fun onCreate() {
        super.onCreate()
        createReminderChannel(this)
        atlas.start()
        keepWidgetFresh()
    }

    // the widget redraws a moment after tasks or logs change, a widget nobody added costs nothing
    @OptIn(FlowPreview::class)
    private fun keepWidgetFresh() {
        atlas.appScope.launch {
            combine(atlas.repository.tasks(), atlas.repository.logsSince(startOfDay()), atlas.repository.trackers()) { _, _, _ -> }
                .debounce(1_500)
                .collect { TodayWidget.refresh(this@AtlasApplication) }
        }
    }
}
