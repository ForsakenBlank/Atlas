package com.forsakenblank.atlas.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert

// every row that syncs gets a name that is the same on every device, ids from autoincrement are not
@Entity(tableName = "sync_ids", primaryKeys = ["kind", "localId"], indices = [Index(value = ["kind", "uid"], unique = true)])
data class SyncId(
    val kind: String,
    val localId: Long,
    val uid: String,
)

// what everything looked like right after the last sync with one other device,
// so the next sync can tell what changed on each side since then
@Entity(tableName = "sync_peers")
data class SyncPeer(
    @androidx.room.PrimaryKey val peerId: String,
    val name: String,
    val kind: String,
    val lastSync: Long,
    val address: String? = null,
    val baseline: String = "{}", // json of uid to fingerprint
)

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_ids")
    suspend fun allIds(): List<SyncId>

    @Upsert
    suspend fun upsertIds(ids: List<SyncId>)

    @Query("DELETE FROM sync_ids WHERE kind = :kind AND localId = :localId")
    suspend fun forgetId(kind: String, localId: Long)

    @Query("DELETE FROM sync_ids WHERE kind = :kind AND uid = :uid")
    suspend fun forgetUid(kind: String, uid: String)

    @Query("SELECT * FROM sync_peers ORDER BY lastSync DESC")
    fun peers(): kotlinx.coroutines.flow.Flow<List<SyncPeer>>

    @Query("SELECT * FROM sync_peers WHERE peerId = :peerId")
    suspend fun peer(peerId: String): SyncPeer?

    @Upsert
    suspend fun savePeer(peer: SyncPeer)

    @Query("DELETE FROM sync_peers WHERE peerId = :peerId")
    suspend fun forgetPeer(peerId: String)

    // sync writes rows straight in, with ids it already knows
    @Upsert suspend fun item(item: Item): Long
    @Upsert suspend fun note(body: NoteBody)
    @Upsert suspend fun sheet(body: SheetBody)
    @Upsert suspend fun tracker(tracker: Tracker)
    @Upsert suspend fun log(entry: LogEntry): Long
    @Upsert suspend fun event(event: Event): Long
    @Upsert suspend fun task(task: Task): Long
    @Upsert suspend fun subject(subject: Subject): Long
    @Upsert suspend fun slot(slot: TimetableSlot): Long
    @Upsert suspend fun term(term: Term): Long
    @Upsert suspend fun countdown(countdown: Countdown): Long
    @Upsert suspend fun account(account: MoneyAccount): Long
    @Upsert suspend fun entry(entry: MoneyEntry): Long
    @Upsert suspend fun quick(quick: MoneyQuick): Long
    @Upsert suspend fun dayNote(note: DayNote)

    @Query("DELETE FROM trackers WHERE itemId = :itemId") suspend fun dropTracker(itemId: Long)
    @Query("DELETE FROM note_bodies WHERE itemId = :itemId") suspend fun dropNote(itemId: Long)
    @Query("DELETE FROM sheets WHERE itemId = :itemId") suspend fun dropSheet(itemId: Long)
    @Query("DELETE FROM item_tags WHERE itemId = :itemId") suspend fun dropTags(itemId: Long)
    @Query("SELECT id FROM tags WHERE name = :name") suspend fun tagId(name: String): Long?
    @Query("INSERT OR IGNORE INTO tags (name) VALUES (:name)") suspend fun newTag(name: String)
    @Query("INSERT OR IGNORE INTO item_tags (itemId, tagId) VALUES (:itemId, :tagId)") suspend fun linkTag(itemId: Long, tagId: Long)

    @Query("DELETE FROM items WHERE id = :id") suspend fun deleteItem(id: Long)
    @Query("DELETE FROM log_entries WHERE id = :id") suspend fun deleteLog(id: Long)
    @Query("DELETE FROM events WHERE id = :id") suspend fun deleteEvent(id: Long)
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun deleteTask(id: Long)
    @Query("DELETE FROM subjects WHERE id = :id") suspend fun deleteSubject(id: Long)
    @Query("DELETE FROM timetable_slots WHERE id = :id") suspend fun deleteSlot(id: Long)
    @Query("DELETE FROM terms WHERE id = :id") suspend fun deleteTerm(id: Long)
    @Query("DELETE FROM countdowns WHERE id = :id") suspend fun deleteCountdown(id: Long)
    @Query("DELETE FROM money_accounts WHERE id = :id") suspend fun deleteAccount(id: Long)
    @Query("DELETE FROM money_entries WHERE id = :id") suspend fun deleteEntry(id: Long)
    @Query("DELETE FROM money_quick WHERE id = :id") suspend fun deleteQuick(id: Long)
    @Query("DELETE FROM day_notes WHERE day = :day") suspend fun deleteDayNote(day: Long)
}
