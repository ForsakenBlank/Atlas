package com.forsakenblank.atlas.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class NoteRow(
    @Embedded val item: Item,
    val text: String?,
)

data class SheetRow(
    @Embedded val item: Item,
    val json: String?,
)

// one row per tracker per local day that has at least one log
data class TrackerDay(
    val trackerId: Long,
    val day: String,
)

data class TrackerWithItem(
    @Embedded val item: Item,
    @Relation(parentColumn = "id", entityColumn = "itemId")
    val tracker: Tracker,
)

@Dao
interface ItemDao {
    @Query(
        """
        SELECT * FROM items
        WHERE parentId IS :parentId AND deletedAt IS NULL AND archived = 0
        ORDER BY type = 'FOLDER' DESC, sortOrder, name COLLATE NOCASE
        """
    )
    fun children(parentId: Long?): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE parentId = :parentId")
    suspend fun childrenOnce(parentId: Long): List<Item>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND name LIKE '%' || :query || '%' ORDER BY updated DESC LIMIT 50")
    fun search(query: String): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE type = 'FOLDER' AND deletedAt IS NULL ORDER BY name COLLATE NOCASE")
    fun folders(): Flow<List<Item>>

    // only the top of each deleted subtree, so a trashed folder shows once
    @Query(
        """
        SELECT * FROM items AS i WHERE i.deletedAt IS NOT NULL
        AND (i.parentId IS NULL OR NOT EXISTS (
            SELECT 1 FROM items AS p WHERE p.id = i.parentId AND p.deletedAt = i.deletedAt
        ))
        ORDER BY i.deletedAt DESC
        """
    )
    fun trash(): Flow<List<Item>>

    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Query("UPDATE items SET deletedAt = :at WHERE id IN (:ids)")
    suspend fun setDeleted(ids: List<Long>, at: Long?)

    @Query("DELETE FROM items WHERE id IN (:ids)")
    suspend fun deleteForever(ids: List<Long>)

    @Query("DELETE FROM items WHERE deletedAt IS NOT NULL")
    suspend fun emptyTrash()

    @Query("DELETE FROM items WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun purgeTrash(before: Long)

    @Query("SELECT * FROM items")
    suspend fun all(): List<Item>

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Item>)

    @Query("DELETE FROM items")
    suspend fun clear()
}

@Dao
interface NoteDao {
    @Query(
        """
        SELECT items.*, note_bodies.text AS text FROM items
        LEFT JOIN note_bodies ON note_bodies.itemId = items.id
        WHERE items.type = 'NOTE' AND items.deletedAt IS NULL AND items.archived = 0
        ORDER BY items.pinned DESC, items.updated DESC
        """
    )
    fun notes(): Flow<List<NoteRow>>

    @Query(
        """
        SELECT items.*, note_bodies.text AS text FROM items
        INNER JOIN item_tags ON item_tags.itemId = items.id
        LEFT JOIN note_bodies ON note_bodies.itemId = items.id
        WHERE item_tags.tagId = :tagId AND items.type = 'NOTE'
        AND items.deletedAt IS NULL AND items.archived = 0
        ORDER BY items.pinned DESC, items.updated DESC
        """
    )
    fun notesWithTag(tagId: Long): Flow<List<NoteRow>>

    @Query("SELECT * FROM note_bodies WHERE itemId = :itemId")
    suspend fun body(itemId: Long): NoteBody?

    @Upsert
    suspend fun upsert(body: NoteBody)

    @Query("SELECT * FROM note_bodies")
    suspend fun all(): List<NoteBody>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bodies: List<NoteBody>)
}

@Dao
interface TrackerDao {
    @Transaction
    @Query(
        """
        SELECT * FROM items WHERE type = 'TRACKER' AND deletedAt IS NULL AND archived = 0
        ORDER BY sortOrder, name COLLATE NOCASE
        """
    )
    fun trackers(): Flow<List<TrackerWithItem>>

    @Transaction
    @Query("SELECT * FROM items WHERE id = :id")
    fun observeTracker(id: Long): Flow<TrackerWithItem?>

    @Query("SELECT * FROM trackers WHERE itemId = :id")
    suspend fun get(id: Long): Tracker?

    @Insert
    suspend fun insert(tracker: Tracker)

    @Update
    suspend fun update(tracker: Tracker)

    @Insert
    suspend fun insertLog(entry: LogEntry): Long

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId ORDER BY timestamp DESC")
    fun logs(trackerId: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE timestamp >= :from ORDER BY timestamp")
    fun logsSince(from: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp")
    fun logsBetween(from: Long, to: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId AND timestamp >= :from AND timestamp < :to")
    suspend fun logsBetweenOnce(trackerId: Long, from: Long, to: Long): List<LogEntry>

    @Query("SELECT * FROM log_entries WHERE trackerId = :trackerId AND timestamp >= :from ORDER BY timestamp DESC")
    suspend fun logsSinceOnce(trackerId: Long, from: Long): List<LogEntry>

    @Query("SELECT DISTINCT trackerId, date(timestamp / 1000, 'unixepoch', 'localtime') AS day FROM log_entries WHERE timestamp >= :from")
    fun loggedDays(from: Long): Flow<List<TrackerDay>>

    @Query("SELECT * FROM trackers")
    suspend fun all(): List<Tracker>

    @Query("SELECT * FROM log_entries")
    suspend fun allLogs(): List<LogEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trackers: List<Tracker>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLogs(logs: List<LogEntry>)
}

@Dao
interface TagDao {
    // tags that are only used by trashed items stay hidden
    @Query(
        """
        SELECT DISTINCT tags.* FROM tags
        INNER JOIN item_tags ON item_tags.tagId = tags.id
        INNER JOIN items ON items.id = item_tags.itemId
        WHERE items.deletedAt IS NULL
        ORDER BY tags.name COLLATE NOCASE
        """
    )
    fun usedTags(): Flow<List<Tag>>

    @Query("SELECT id FROM tags WHERE name = :name")
    suspend fun idFor(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: Tag): Long

    @Query("DELETE FROM item_tags WHERE itemId = :itemId")
    suspend fun clearFor(itemId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(links: List<ItemTag>)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM item_tags)")
    suspend fun dropUnused()

    @Query("SELECT * FROM tags")
    suspend fun all(): List<Tag>

    @Query("SELECT * FROM item_tags")
    suspend fun allLinks(): List<ItemTag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tags: List<Tag>)
}

@Dao
interface EventDao {
    // repeating events can start long before the range, so they are always loaded
    @Query("SELECT * FROM events WHERE (startsAt < :to AND endsAt >= :from) OR repeatRule != 'NONE' ORDER BY startsAt")
    fun eventsBetween(from: Long, to: Long): Flow<List<Event>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: Long): Event?

    @Upsert
    suspend fun upsert(event: Event): Long

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM events")
    suspend fun all(): List<Event>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<Event>)

    @Query("DELETE FROM events")
    suspend fun clear()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done, due IS NULL, due, priority DESC, created")
    fun all(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE done = 0 AND due IS NOT NULL AND due < :before ORDER BY due, priority DESC")
    fun openDueBefore(before: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE due >= :from AND due < :to ORDER BY done, priority DESC")
    fun dueBetween(from: Long, to: Long): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Upsert
    suspend fun upsert(task: Task): Long

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tasks WHERE done = 1")
    suspend fun clearDone()

    @Query("SELECT * FROM tasks")
    suspend fun allOnce(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tasks: List<Task>)

    @Query("DELETE FROM tasks")
    suspend fun clear()
}

@Dao
interface TimetableDao {
    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    fun subjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun subject(id: Long): Subject?

    @Upsert
    suspend fun upsertSubject(subject: Subject): Long

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubject(id: Long)

    @Query("SELECT * FROM timetable_slots ORDER BY dayOfWeek, startMinute")
    fun slots(): Flow<List<TimetableSlot>>

    @Upsert
    suspend fun upsertSlot(slot: TimetableSlot): Long

    @Query("DELETE FROM timetable_slots WHERE id = :id")
    suspend fun deleteSlot(id: Long)

    @Query("SELECT * FROM terms ORDER BY startDay")
    fun terms(): Flow<List<Term>>

    @Upsert
    suspend fun upsertTerm(term: Term): Long

    @Query("DELETE FROM terms WHERE id = :id")
    suspend fun deleteTerm(id: Long)

    @Query("SELECT * FROM subjects")
    suspend fun allSubjects(): List<Subject>

    @Query("SELECT * FROM timetable_slots")
    suspend fun allSlots(): List<TimetableSlot>

    @Query("SELECT * FROM terms")
    suspend fun allTerms(): List<Term>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<Subject>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlots(slots: List<TimetableSlot>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerms(terms: List<Term>)

    @Query("DELETE FROM subjects")
    suspend fun clearSubjects()

    @Query("DELETE FROM terms")
    suspend fun clearTerms()
}

@Dao
interface SheetDao {
    @Query(
        """
        SELECT items.*, sheets.json AS json FROM items
        LEFT JOIN sheets ON sheets.itemId = items.id
        WHERE items.type = 'SHEET' AND items.deletedAt IS NULL AND items.archived = 0
        ORDER BY items.updated DESC
        """
    )
    fun sheets(): Flow<List<SheetRow>>

    @Query("SELECT * FROM sheets WHERE itemId = :itemId")
    suspend fun get(itemId: Long): SheetBody?

    @Query("SELECT * FROM sheets WHERE itemId = :itemId")
    fun observe(itemId: Long): Flow<SheetBody?>

    @Upsert
    suspend fun upsert(body: SheetBody)

    @Query("SELECT * FROM sheets")
    suspend fun all(): List<SheetBody>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bodies: List<SheetBody>)
}

@Dao
interface CountdownDao {
    @Query("SELECT * FROM countdowns ORDER BY date")
    fun all(): Flow<List<Countdown>>

    @Query("SELECT * FROM countdowns WHERE id = :id")
    suspend fun get(id: Long): Countdown?

    @Upsert
    suspend fun upsert(c: Countdown): Long

    @Query("DELETE FROM countdowns WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM countdowns")
    suspend fun allOnce(): List<Countdown>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(countdowns: List<Countdown>)

    @Query("DELETE FROM countdowns")
    suspend fun clear()
}

@Dao
interface MoneyDao {
    @Query("SELECT * FROM money_accounts ORDER BY sortOrder, id")
    fun accounts(): Flow<List<MoneyAccount>>

    @Query("SELECT * FROM money_entries ORDER BY day DESC, id DESC")
    fun entries(): Flow<List<MoneyEntry>>

    @Query("SELECT * FROM money_accounts WHERE id = :id")
    suspend fun account(id: Long): MoneyAccount?

    @Query("SELECT * FROM money_entries WHERE id = :id")
    suspend fun entry(id: Long): MoneyEntry?

    @Upsert
    suspend fun upsertAccount(account: MoneyAccount): Long

    @Upsert
    suspend fun upsertEntry(entry: MoneyEntry): Long

    @Query("DELETE FROM money_accounts WHERE id = :id")
    suspend fun deleteAccount(id: Long)

    @Query("DELETE FROM money_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    // deleting one half of a transfer takes the other half with it
    @Query("DELETE FROM money_entries WHERE transferId = :transferId")
    suspend fun deleteTransfer(transferId: Long)

    @Query("SELECT * FROM money_quick ORDER BY sortOrder, id")
    fun quick(): Flow<List<MoneyQuick>>

    @Query("SELECT * FROM money_quick WHERE id = :id")
    suspend fun quickOne(id: Long): MoneyQuick?

    @Upsert
    suspend fun upsertQuick(quick: MoneyQuick): Long

    @Query("DELETE FROM money_quick WHERE id = :id")
    suspend fun deleteQuick(id: Long)

    @Query("SELECT * FROM money_quick")
    suspend fun allQuick(): List<MoneyQuick>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuick(quick: List<MoneyQuick>)

    @Query("SELECT * FROM money_accounts")
    suspend fun allAccounts(): List<MoneyAccount>

    @Query("SELECT * FROM money_entries")
    suspend fun allEntries(): List<MoneyEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<MoneyAccount>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<MoneyEntry>)

    @Query("DELETE FROM money_accounts")
    suspend fun clear()
}

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_notes")
    fun all(): Flow<List<DayNote>>

    @Upsert
    suspend fun upsert(note: DayNote)

    @Query("DELETE FROM day_notes WHERE day = :day")
    suspend fun delete(day: Long)

    @Query("SELECT * FROM day_notes")
    suspend fun allOnce(): List<DayNote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<DayNote>)

    @Query("DELETE FROM day_notes")
    suspend fun clear()
}
