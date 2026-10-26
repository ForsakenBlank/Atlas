package com.forsakenblank.atlas

import android.app.Application
import com.forsakenblank.atlas.notify.createReminderChannel
import com.forsakenblank.atlas.platform.buildAtlasDatabase
import com.forsakenblank.atlas.platform.settingsDataStore

class AtlasApplication : Application() {

    // everything else lives in the shared AtlasApp, the phone only adds how to reach android
    val atlas by lazy { AtlasApp(AndroidPlatform(this), buildAtlasDatabase(this), settingsDataStore(this)) }

    override fun onCreate() {
        super.onCreate()
        createReminderChannel(this)
        atlas.start()
    }
}
