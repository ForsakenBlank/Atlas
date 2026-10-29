package com.forsakenblank.atlas

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.forsakenblank.atlas.data.ItemType
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.platform.DeviceKind
import com.forsakenblank.atlas.platform.FileReader
import com.forsakenblank.atlas.platform.FileWriter
import com.forsakenblank.atlas.platform.Platform
import com.forsakenblank.atlas.platform.ReminderHost
import com.forsakenblank.atlas.platform.buildAtlasDatabase
import com.forsakenblank.atlas.platform.settingsDataStore
import com.forsakenblank.atlas.sync.ClientStep
import com.forsakenblank.atlas.util.Reminder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// two whole copies of atlas, one hosting on this machine and one connecting to it
class SyncEndToEndTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val opened = mutableListOf<AtlasApp>()

    @After
    fun tearDown() {
        opened.forEach {
            it.sync.stopHosting()
            it.database.close()
        }
    }

    private fun device(name: String, kind: DeviceKind): AtlasApp {
        val dir = folder.newFolder(name)
        return AtlasApp(TestPlatform(name, kind), buildAtlasDatabase(dir), settingsDataStore(dir)).also { opened += it }
    }

    private suspend fun AtlasApp.trackers() = database.items().all().filter { it.type == ItemType.TRACKER && it.deletedAt == null }

    private suspend fun AtlasApp.secondsLogged() = database.trackers().allLogs().sumOf { it.durationSeconds ?: 0 }

    private suspend fun AtlasApp.notes() = database.items().all().filter { it.type == ItemType.NOTE }.map { it.name }

    private suspend fun syncFrom(phone: AtlasApp, pc: AtlasApp) {
        val code = pc.sync.host.value!!.code
        phone.sync.connect("127.0.0.1", code)
        val review = phone.sync.client.value
        assertTrue("expected a review, got $review", review is ClientStep.Review)
        phone.sync.run(emptyMap())
        val done = phone.sync.client.value
        assertTrue("expected done, got $done", done is ClientStep.Done)
    }

    @Test
    fun timeFromBothDevicesAddsUpAndDeletesTravel() = runBlocking {
        val pc = device("pc", DeviceKind.PC)
        val phone = device("phone", DeviceKind.PHONE)

        // the same tracker made on both before they ever synced, 10 minutes on the pc and 5 on the phone
        val onPc = pc.repository.createTracker("Reading", TrackerKind.TIMER, null, null, null, null, true)
        pc.repository.logDuration(onPc, 1_000, 600)
        val onPhone = phone.repository.createTracker("Reading", TrackerKind.TIMER, null, null, null, null, true)
        phone.repository.logDuration(onPhone, 2_000, 300)
        phone.repository.createNote(null, "Shopping")

        pc.sync.startHosting()
        syncFrom(phone, pc)

        assertEquals(1, pc.trackers().size)
        assertEquals(1, phone.trackers().size)
        assertEquals(900L, pc.secondsLogged())
        assertEquals(900L, phone.secondsLogged())
        assertEquals(listOf("Shopping"), pc.notes())

        // the second time only what changed since goes across
        phone.repository.logDuration(onPhone, 3_000, 120)
        val note = pc.database.items().all().first { it.type == ItemType.NOTE }
        pc.database.items().deleteForever(listOf(note.id))

        syncFrom(phone, pc)

        assertEquals(1020L, pc.secondsLogged())
        assertEquals(1020L, phone.secondsLogged())
        assertTrue(phone.notes().isEmpty())
    }

    @Test
    fun aWrongCodeIsTurnedAway() = runBlocking {
        val pc = device("pc", DeviceKind.PC)
        val phone = device("phone", DeviceKind.PHONE)
        pc.sync.startHosting()
        val wrong = ((pc.sync.host.value!!.code.toInt() + 1) % 1_000_000).toString().padStart(6, '0')

        phone.sync.connect("127.0.0.1", wrong)

        val step = phone.sync.client.value
        assertTrue("expected a failure, got $step", step is ClientStep.Failed && step.text == "Wrong code")
    }
}

private class TestPlatform(override val deviceName: String, override val kind: DeviceKind) : Platform {
    override val hasWallpaperColours = false
    override fun elapsedMillis() = System.nanoTime() / 1_000_000
    override fun vibrate(millis: Long) = Unit
    override fun share(subject: String?, text: String): String? = null
    override val reminders = object : ReminderHost {
        override fun allowed() = true
        override fun canAsk() = false
        override fun openSettings() = Unit
        override fun sendTest() = Unit
        override suspend fun schedule(planned: List<Reminder>, enabled: Boolean) = Unit

        @Composable
        override fun rememberPermissionRequest(onResult: (Boolean) -> Unit): () -> Unit = {}
    }

    @Composable
    override fun wallpaperColours(dark: Boolean): ColorScheme? = null

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit

    @Composable
    override fun KeepScreenOn(on: Boolean) = Unit

    @Composable
    override fun rememberSaveFile(mimeType: String, onPicked: (FileWriter?) -> Unit): (String) -> Unit = {}

    @Composable
    override fun rememberOpenFile(onPicked: (FileReader?) -> Unit): () -> Unit = {}
}
