package com.forsakenblank.atlas

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import com.forsakenblank.atlas.data.Backup
import com.forsakenblank.atlas.data.ItemType
import com.forsakenblank.atlas.data.MoneyAccount
import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.TimetableSlot
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.platform.DeviceKind
import com.forsakenblank.atlas.platform.FileReader
import com.forsakenblank.atlas.platform.FileWriter
import com.forsakenblank.atlas.platform.Platform
import com.forsakenblank.atlas.platform.ReminderHost
import com.forsakenblank.atlas.platform.buildAtlasDatabase
import com.forsakenblank.atlas.platform.settingsDataStore
import com.forsakenblank.atlas.sync.Choice
import com.forsakenblank.atlas.sync.ClientStep
import com.forsakenblank.atlas.util.Reminder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

// devices that have synced before and then kept being used, each direction of hosting
class SyncRepeatTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val opened = mutableListOf<AtlasApp>()
    private val backups = HashMap<AtlasApp, Backup>()

    @After
    fun tearDown() {
        opened.forEach {
            it.sync.stopHosting()
            it.database.close()
        }
    }

    private fun device(name: String, kind: DeviceKind): AtlasApp {
        val dir = folder.newFolder(name)
        return AtlasApp(RepeatPlatform(name, kind), buildAtlasDatabase(dir), settingsDataStore(dir)).also { opened += it }
    }

    // the connecting device takes every default, a failure on either side fails the test with its text
    private suspend fun sync(client: AtlasApp, host: AtlasApp, random: Random? = null) {
        host.sync.startHosting(port = 0)
        try {
            val info = host.sync.host.value!!
            client.sync.connect("127.0.0.1:${info.port}", info.code)
            val review = client.sync.client.value
            assertTrue("expected a review, got $review", review is ClientStep.Review)
            val choices = if (random == null) emptyMap() else (review as ClientStep.Review).plan.items
                .filter { random.nextInt(3) == 0 }
                .associate { it.key to if (it.canKeepBoth) Choice.entries.random(random) else listOf(Choice.THEIRS, Choice.MINE, Choice.SKIP).random(random) }
            client.sync.run(choices)
            val done = client.sync.client.value
            assertTrue("expected done, got $done (host: ${host.sync.hostStatus.value?.text})", done is ClientStep.Done)
        } finally {
            host.sync.stopHosting()
        }
    }

    private suspend fun AtlasApp.names() = database.items().all().map { it.name }.sorted()

    private suspend fun AtlasApp.named(name: String) = database.items().all().first { it.name == name }

    private suspend fun both(block: suspend (client: AtlasApp, host: AtlasApp) -> Unit) {
        block(device("phone-a", DeviceKind.PHONE), device("pc-a", DeviceKind.PC))
        block(device("pc-b", DeviceKind.PC), device("phone-b", DeviceKind.PHONE))
    }

    @Test
    fun anEditOfSomethingAlreadySyncedGoesBothWays() = runBlocking {
        both { client, host ->
            val note = client.repository.createNote(null, "Shopping")
            sync(client, host)

            client.repository.saveNote(note, "Shopping", "milk")
            sync(client, host)
            host.repository.saveNote(host.named("Shopping").id, "Shopping", "milk and eggs")
            sync(client, host)

            assertEquals("milk and eggs", client.repository.snapshot().notes.first { it.itemId == note }.text)
        }
    }

    @Test
    fun aTrackerDeletedOnOneSideWhileTheOtherKeptLogging() = runBlocking {
        both { client, host ->
            val tracker = client.repository.createTracker("Reading", TrackerKind.TIMER, null, null, null, null, true)
            client.repository.logDuration(tracker, 1_000, 60)
            sync(client, host)

            host.repository.deleteForever(host.named("Reading"))
            client.repository.logDuration(tracker, 2_000, 60)
            sync(client, host)
            assertEquals(client.names(), host.names())

            // and the same the other way round
            val second = host.repository.createTracker("Water", TrackerKind.TIMER, null, null, null, null, true)
            host.repository.logDuration(second, 1_000, 60)
            sync(client, host)
            client.repository.deleteForever(client.named("Water"))
            host.repository.logDuration(second, 2_000, 60)
            sync(client, host)
            assertEquals(client.names(), host.names())
        }
    }

    @Test
    fun somethingDeletedForeverThenMadeAgainWithTheSameName() = runBlocking {
        both { client, host ->
            client.repository.createNote(null, "Shopping")
            sync(client, host)

            host.repository.deleteForever(host.named("Shopping"))
            host.repository.createNote(null, "Shopping")
            sync(client, host)
            assertEquals(client.names(), host.names())
        }
    }

    // the last sync only half finished, so the two sides remember different things and compare from scratch
    @Test
    fun somethingDeletedForeverAfterTheDevicesForgotEachOther() = runBlocking {
        both { client, host ->
            client.repository.createNote(null, "Shopping")
            val tracker = client.repository.createTracker("Reading", TrackerKind.TIMER, null, null, null, null, true)
            client.repository.logDuration(tracker, 1_000, 60)
            sync(client, host)

            host.repository.deleteForever(host.named("Shopping"))
            host.repository.deleteForever(host.named("Reading"))
            host.repository.createNote(null, "Shopping")
            val again = host.repository.createTracker("Reading", TrackerKind.TIMER, null, null, null, null, true)
            host.repository.logDuration(again, 1_000, 60)
            val peer = client.settingsStore.deviceId()
            host.sync.forget(peer)
            sync(client, host)
            assertEquals(client.names(), host.names())
        }
    }

    // random use of both devices, every sync in between has to work
    @Test
    fun randomUseKeepsSyncing() = runBlocking {
        repeat(40) { seed ->
            val random = Random(seed)
            val one = device("one-$seed", DeviceKind.PHONE)
            val two = device("two-$seed", DeviceKind.PC)
            var made = 0
            repeat(10) { round ->
                repeat(8) {
                    use(if (random.nextBoolean()) one else two, random, made++)
                }
                // a sync that only half finished leaves the two remembering different things
                if (random.nextInt(3) == 0) one.sync.forget(two.settingsStore.deviceId())
                val (client, host) = if (random.nextBoolean()) one to two else two to one
                try {
                    sync(client, host, random)
                } catch (e: AssertionError) {
                    throw AssertionError("seed $seed round $round: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun use(app: AtlasApp, random: Random, n: Int) {
        val r = app.repository
        val items = app.database.items().all()
        val trackers = items.filter { it.type == ItemType.TRACKER }
        val notes = items.filter { it.type == ItemType.NOTE }
        val snapshot = r.snapshot()
        val accounts = snapshot.moneyAccounts
        val subjects = snapshot.subjects
        when (random.nextInt(18)) {
            0 -> r.createNote(null, "Note ${n % 5}")
            1 -> r.createTracker("Tracker ${n % 3}", TrackerKind.TIMER, null, null, null, null, true)
            2 -> trackers.randomOrNull(random)?.let { r.logDuration(it.id, n * 1_000L, 60) }
            3 -> notes.randomOrNull(random)?.let { r.saveNote(it.id, it.name, "text $n #tag${n % 3}") }
            4 -> notes.randomOrNull(random)?.let { r.deleteForever(it) }
            5 -> trackers.randomOrNull(random)?.let { r.deleteForever(it) }
            6 -> r.saveMoneyAccount(MoneyAccount(name = "Account ${n % 3}"))
            7 -> accounts.randomOrNull(random)?.let { r.saveMoneyEntry(MoneyEntry(accountId = it.id, day = 20_000L + n, amountPence = 100)) }
            8 -> r.createFolder("Folder ${n % 3}", items.filter { it.type == ItemType.FOLDER }.randomOrNull(random)?.id)
            9 -> items.filter { it.id != 1L }.randomOrNull(random)?.let { r.deleteForever(it) }
            10 -> r.createSheet(null, "Sheet ${n % 3}")
            11 -> r.saveSubject(Subject(name = "Subject ${n % 3}"), makeFolder = random.nextBoolean())
            12 -> subjects.randomOrNull(random)?.let { r.saveSlot(TimetableSlot(subjectId = it.id, dayOfWeek = 1 + n % 7, startMinute = n, endMinute = n + 60)) }
            13 -> r.saveTask(Task(title = "Task ${n % 3}", subjectId = subjects.randomOrNull(random)?.id))
            14 -> items.filter { it.id != 1L }.randomOrNull(random)?.let { r.moveToTrash(it) }
            15 -> app.database.items().all().filter { it.deletedAt != null }.randomOrNull(random)?.let { r.restore(it) }
            16 -> if (accounts.size >= 2) r.moneyTransfer(accounts[0].id, accounts[1].id, 20_000L + n, 500, "move")
            // going back to a backup puts old rows back under their old ids
            17 -> if (random.nextInt(3) == 0) backups[app]?.let { r.restoreSnapshot(it) } else backups[app] = snapshot
        }
    }
}

private class RepeatPlatform(override val deviceName: String, override val kind: DeviceKind) : Platform {
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
