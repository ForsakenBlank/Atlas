package com.forsakenblank.atlas.data

import com.forsakenblank.atlas.platform.FileReader
import com.forsakenblank.atlas.platform.FileWriter
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Backup(
    val version: Int,
    val exportedAt: Long,
    val items: List<Item>,
    val notes: List<NoteBody>,
    val trackers: List<Tracker>,
    val logs: List<LogEntry>,
    val tags: List<Tag>,
    val itemTags: List<ItemTag>,
    // added in version 2, older backups simply have none
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val subjects: List<Subject> = emptyList(),
    val slots: List<TimetableSlot> = emptyList(),
    val terms: List<Term> = emptyList(),
    // added in version 3
    val sheets: List<SheetBody> = emptyList(),
    val countdowns: List<Countdown> = emptyList(),
    // added in version 4
    val moneyAccounts: List<MoneyAccount> = emptyList(),
    val moneyEntries: List<MoneyEntry> = emptyList(),
    // added in version 5
    val moneyQuick: List<MoneyQuick> = emptyList(),
    // added in version 6
    val dayNotes: List<DayNote> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 6
    }
}

class BackupManager(private val repository: AtlasRepository) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportTo(write: FileWriter) {
        write(json.encodeToString(Backup.serializer(), repository.snapshot()))
    }

    // returns how many items were restored
    suspend fun importFrom(read: FileReader): Int {
        val text = read()
        val backup = json.decodeFromString(Backup.serializer(), text)
        require(backup.version <= Backup.CURRENT_VERSION) { "This backup is from a newer version of Atlas" }
        repository.restoreSnapshot(backup)
        return backup.items.size
    }
}
