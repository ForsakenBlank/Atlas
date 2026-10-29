package com.forsakenblank.atlas

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.ItemType
import com.forsakenblank.atlas.data.MoneyAccount
import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.data.MoneyQuick
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.data.StarterPacks
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.Term
import com.forsakenblank.atlas.data.TimetableSlot
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
    fun aFullPhoneSyncsToANewPc() = runBlocking {
        val pc = device("pc", DeviceKind.PC)
        val phone = device("phone", DeviceKind.PHONE)
        val r = phone.repository

        // one of everything a phone that has been used for a while would have
        StarterPacks.forEach { r.addStarterPack(it) }
        val school = r.createFolder("School", null)
        val maths = r.createFolder("Maths", school)
        val note = r.createNote(maths, "Homework")
        r.saveNote(note, "Homework", "Page 12 #maths #school")
        val sheet = r.createSheet(school, "Grades")
        val reading = r.createTracker("Reading", TrackerKind.TIMER, school, null, 30, null, true)
        r.logDuration(reading, 1_000, 600)
        val water = r.createTracker("Water", TrackerKind.NUMBER, null, null, 8, "glasses", true)
        r.logValue(water, 3.0, "morning")
        r.saveSubject(Subject(name = "Physics", teacher = "Ms Lee"), makeFolder = true)
        val physics = phone.database.timetable().allSubjects().first { it.name == "Physics" }
        r.saveSlot(TimetableSlot(subjectId = physics.id, dayOfWeek = 1, startMinute = 540, endMinute = 600))
        r.saveTask(Task(title = "Lab report", subjectId = physics.id, due = 20_000))
        r.saveTerm(Term(name = "Autumn", startDay = 20_000, endDay = 20_090))
        r.saveEvent(Event(title = "Dentist", startsAt = 1_800_000_000_000, endsAt = 1_800_000_360_000, repeatRule = Repeat.YEARLY))
        r.saveCountdown(Countdown(title = "Birthday", date = 10_000, kind = CountdownKind.BIRTHDAY, yearly = true))
        val bank = r.saveMoneyAccount(MoneyAccount(name = "Bank", openingPence = 10_000))
        val cash = r.saveMoneyAccount(MoneyAccount(name = "Cash"))
        r.saveMoneyEntry(MoneyEntry(accountId = bank, day = 20_000, amountPence = -450, category = "Food"))
        r.moneyTransfer(bank, cash, 20_001, 2_000, "cash out")
        r.saveMoneyQuick(MoneyQuick(accountId = cash, name = "Coffee", amountPence = -300))
        r.saveDayNote(20_000, "A good day")
        r.saveJournal(20_001, "Tired", 2)

        pc.sync.startHosting()
        syncFrom(phone, pc)
        assertTrue("host status: ${pc.sync.hostStatus.value}", pc.sync.hostStatus.value?.text.orEmpty().startsWith("Synced with"))

        suspend fun AtlasApp.counts() = repository.snapshot().let {
            listOf(it.items.size, it.notes.size, it.sheets.size, it.trackers.size, it.logs.size, it.events.size, it.tasks.size, it.subjects.size,
                it.slots.size, it.terms.size, it.countdowns.size, it.moneyAccounts.size, it.moneyEntries.size, it.moneyQuick.size, it.dayNotes.size)
        }
        assertEquals(phone.counts(), pc.counts())

        // and a second time with nothing new
        syncFrom(phone, pc)
        assertEquals(phone.counts(), pc.counts())
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
