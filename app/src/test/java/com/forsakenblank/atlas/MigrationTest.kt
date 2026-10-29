package com.forsakenblank.atlas

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.forsakenblank.atlas.data.AtlasDatabase
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.CountdownKind
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.MoneyAccount
import com.forsakenblank.atlas.data.MoneyQuick
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.TimetableSlot
import com.forsakenblank.atlas.data.TrackerKind
import com.forsakenblank.atlas.util.Sheet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// builds a database exactly as version 0.1.0 left it, then opens it with the current schema
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {

    private val name = "migration-test.db"
    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase(name)
    }

    @After
    fun tearDown() {
        app.deleteDatabase(name)
    }

    private fun createVersionOne() {
        val file = app.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        listOf(
            "CREATE TABLE IF NOT EXISTS `items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `parentId` INTEGER, `name` TEXT NOT NULL, `color` INTEGER, `icon` TEXT, `sortOrder` INTEGER NOT NULL, `pinned` INTEGER NOT NULL, `created` INTEGER NOT NULL, `updated` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `deletedAt` INTEGER)",
            "CREATE INDEX IF NOT EXISTS `index_items_parentId` ON `items` (`parentId`)",
            "CREATE INDEX IF NOT EXISTS `index_items_type` ON `items` (`type`)",
            "CREATE TABLE IF NOT EXISTS `note_bodies` (`itemId` INTEGER NOT NULL, `text` TEXT NOT NULL, PRIMARY KEY(`itemId`), FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `trackers` (`itemId` INTEGER NOT NULL, `kind` TEXT NOT NULL, `unit` TEXT, `dailyGoal` INTEGER, `showOnHome` INTEGER NOT NULL, `runningSince` INTEGER, PRIMARY KEY(`itemId`), FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE TABLE IF NOT EXISTS `log_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackerId` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, `value` REAL NOT NULL, `durationSeconds` INTEGER, `note` TEXT, FOREIGN KEY(`trackerId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_trackerId` ON `log_entries` (`trackerId`)",
            "CREATE INDEX IF NOT EXISTS `index_log_entries_timestamp` ON `log_entries` (`timestamp`)",
            "CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `color` INTEGER)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)",
            "CREATE TABLE IF NOT EXISTS `item_tags` (`itemId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`itemId`, `tagId`), FOREIGN KEY(`itemId`) REFERENCES `items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE INDEX IF NOT EXISTS `index_item_tags_tagId` ON `item_tags` (`tagId`)",
            // what a phone that used 0.1.0 for a bit might hold
            "INSERT INTO items VALUES (1, 'FOLDER', NULL, 'Inbox', NULL, NULL, 0, 0, 1, 1, 0, NULL)",
            "INSERT INTO items VALUES (4, 'TRACKER', NULL, 'Cold shower', -10177034, NULL, 0, 0, 1, 1, 0, NULL)",
            "INSERT INTO items VALUES (7, 'NOTE', 1, 'Shopping', NULL, NULL, 0, 1, 1, 1, 0, NULL)",
            "INSERT INTO trackers VALUES (4, 'COUNTER', 'showers', 1, 1, NULL)",
            "INSERT INTO log_entries VALUES (1, 4, 1700000000000, 1.0, NULL, NULL)",
            "INSERT INTO note_bodies VALUES (7, 'milk and #eggs')",
            "INSERT INTO tags VALUES (1, 'eggs', NULL)",
            "INSERT INTO item_tags VALUES (7, 1)",
        ).forEach { db.execSQL(it) }
        db.version = 1
        db.close()
    }

    @Test
    fun versionOneDataSurvivesTheUpgrade() = runBlocking {
        createVersionOne()
        val db = Room.databaseBuilder(app, AtlasDatabase::class.java, name)
            .addMigrations(*AtlasDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        val tracker = db.trackers().get(4)!!
        assertEquals(TrackerKind.COUNTER, tracker.kind)
        assertEquals("showers", tracker.unit)
        assertNull(tracker.aggregate)
        assertEquals(1, db.trackers().allLogs().size)
        assertEquals("milk and #eggs", db.notes().body(7)?.text)
        assertEquals("Shopping", db.items().get(7)?.name)
        assertEquals(1, db.tags().allLinks().size)

        // and the new tables work
        db.events().upsert(Event(title = "Dentist", startsAt = 1, endsAt = 2))
        db.tasks().upsert(Task(title = "Revise"))
        val subjectId = db.timetable().upsertSubject(Subject(name = "Maths"))
        db.timetable().upsertSlot(TimetableSlot(subjectId = subjectId, dayOfWeek = 1, startMinute = 540, endMinute = 600))
        assertEquals(1, db.events().all().size)
        assertEquals(1, db.tasks().allOnce().size)
        assertEquals(1, db.timetable().allSlots().size)

        // slots go when their subject is deleted
        db.timetable().deleteSubject(subjectId)
        assertEquals(0, db.timetable().allSlots().size)
        db.close()
    }

    // runs the real 1 to 2 migration and stops there, like a phone still on 0.2.0
    private fun createVersionTwo() {
        createVersionOne()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AtlasDatabase.MIGRATIONS.first { it.startVersion == 1 && it.endVersion == 2 }.migrate(db)
                    }
                })
                .build()
        )
        val db = helper.writableDatabase
        db.execSQL("INSERT INTO events (id, title, startsAt, endsAt, allDay, repeatRule, created) VALUES (1, 'Dentist', 10, 20, 0, 'NONE', 1)")
        db.execSQL("INSERT INTO tasks (id, title, priority, done, repeatRule, created) VALUES (1, 'Revise', 0, 0, 'NONE', 1)")
        helper.close()
    }

    @Test
    fun versionTwoUpgradesToThree() = runBlocking {
        createVersionTwo()
        val db = Room.databaseBuilder(app, AtlasDatabase::class.java, name)
            .addMigrations(*AtlasDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        // old rows pick up the defaults of the new reminder columns
        val event = db.events().get(1)!!
        assertEquals("Dentist", event.title)
        assertNull(event.reminderMinutes)
        assertTrue(db.tasks().get(1)!!.remind)
        assertEquals("Shopping", db.items().get(7)?.name)

        // a sheet saves, reads back and goes with its item
        val repo = AtlasRepository(db)
        val sheetId = repo.createSheet(parentId = 1, name = "Budget")
        assertEquals(20, repo.sheet(sheetId)!!.rows)
        repo.saveSheet(sheetId, Sheet().withCell(0, 0, "Rent").withCell(0, 1, "=950*12"))
        val sheet = repo.sheet(sheetId)!!
        assertEquals("Rent", sheet.raw(0, 0))
        assertEquals("=950*12", sheet.raw(0, 1))
        assertEquals(1, repo.snapshot().sheets.size)
        db.items().deleteForever(listOf(sheetId))
        assertNull(db.sheets().get(sheetId))

        // and so does a countdown
        val countdownId = repo.saveCountdown(Countdown(title = "Holiday", date = 20_500, kind = CountdownKind.HOLIDAY))
        val countdown = repo.countdown(countdownId)!!
        assertEquals(CountdownKind.HOLIDAY, countdown.kind)
        assertTrue(countdown.remind)
        assertEquals(countdownId, repo.saveCountdown(countdown.copy(title = "Beach")))
        assertEquals("Beach", db.countdowns().allOnce().single().title)

        // money accounts keep a transfer balanced and go with their entries
        val current = repo.saveMoneyAccount(MoneyAccount(name = "Current", openingPence = 10_000))
        val savings = repo.saveMoneyAccount(MoneyAccount(name = "Savings"))
        repo.moneyTransfer(current, savings, day = 20_000, pence = 2_500, note = null)
        assertEquals(2, repo.snapshot().moneyEntries.size)
        assertEquals(0L, repo.snapshot().moneyEntries.sumOf { it.amountPence })
        repo.deleteMoneyEntry(repo.snapshot().moneyEntries.first())
        assertEquals(0, repo.snapshot().moneyEntries.size)

        // a quick add logs a normal entry dated today
        repo.saveMoneyQuick(MoneyQuick(accountId = current, name = "Coffee", amountPence = -350, category = "Food"))
        val logged = repo.logMoneyQuick(repo.snapshot().moneyQuick.single().id)!!
        assertEquals(-350L, logged.amountPence)
        assertEquals(java.time.LocalDate.now().toEpochDay(), logged.day)

        // a note on a day saves, reads back and clears when emptied
        repo.saveDayNote(20_000, "  Dentist went fine ")
        assertEquals("Dentist went fine", repo.snapshot().dayNotes.single().text)
        repo.saveDayNote(20_000, "   ")
        assertEquals(0, repo.snapshot().dayNotes.size)

        repo.deleteMoneyAccount(current)
        assertEquals(1, repo.snapshot().moneyAccounts.size)
        assertEquals(0, repo.snapshot().moneyQuick.size)
        db.close()
    }
}
