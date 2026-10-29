package com.forsakenblank.atlas.sync

import com.forsakenblank.atlas.data.ThemeMode

import com.forsakenblank.atlas.data.AtlasDatabase
import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.data.Countdown
import com.forsakenblank.atlas.data.DayNote
import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Item
import com.forsakenblank.atlas.data.LogEntry
import com.forsakenblank.atlas.data.MoneyAccount
import com.forsakenblank.atlas.data.MoneyEntry
import com.forsakenblank.atlas.data.MoneyQuick
import com.forsakenblank.atlas.data.NoteBody
import com.forsakenblank.atlas.data.SettingsStore
import com.forsakenblank.atlas.data.SheetBody
import com.forsakenblank.atlas.data.Subject
import com.forsakenblank.atlas.data.Task
import com.forsakenblank.atlas.data.Term
import com.forsakenblank.atlas.data.TimetableSlot
import com.forsakenblank.atlas.data.Tracker
import com.forsakenblank.atlas.platform.inTransaction
import com.forsakenblank.atlas.ui.theme.AtlasTheme
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// reads everything as sync records and writes records from another device back in
class SyncStore(
    private val db: AtlasDatabase,
    private val repository: AtlasRepository,
    private val settings: SettingsStore,
) {
    private val dao = db.sync()

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private class Shape<T>(val serializer: KSerializer<T>)

    private val shapes = mapOf(
        Kind.LOG to Shape(LogEntry.serializer()),
        Kind.EVENT to Shape(Event.serializer()),
        Kind.TASK to Shape(Task.serializer()),
        Kind.SUBJECT to Shape(Subject.serializer()),
        Kind.SLOT to Shape(TimetableSlot.serializer()),
        Kind.TERM to Shape(Term.serializer()),
        Kind.COUNTDOWN to Shape(Countdown.serializer()),
        Kind.ACCOUNT to Shape(MoneyAccount.serializer()),
        Kind.ENTRY to Shape(MoneyEntry.serializer()),
        Kind.QUICK to Shape(MoneyQuick.serializer()),
    )

    suspend fun records(): List<SyncRecord> {
        val book = IdBook(dao.allIds())
        val snap = repository.snapshot()
        val out = ArrayList<SyncRecord>(snap.items.size + snap.logs.size + 64)

        val notes = snap.notes.associateBy { it.itemId }
        val sheets = snap.sheets.associateBy { it.itemId }
        val trackers = snap.trackers.associateBy { it.itemId }
        val tagNames = snap.tags.associate { it.id to it.name }
        val tagsByItem = snap.itemTags.groupBy({ it.itemId }, { tagNames[it.tagId] }).mapValues { (_, v) -> v.filterNotNull().sorted() }

        for (item in snap.items) {
            val fields = encode(Item.serializer(), item).toMutableMap()
            fields.remove("id")
            fields["parentId"] = book.uidOrNull(Kind.ITEM, item.parentId).toJson()
            notes[item.id]?.let { fields["note"] = encode(NoteBody.serializer(), it).without("itemId") }
            sheets[item.id]?.let { fields["sheet"] = encode(SheetBody.serializer(), it).without("itemId") }
            // a running timer belongs to the device it was started on
            trackers[item.id]?.let { fields["tracker"] = encode(Tracker.serializer(), it).without("itemId", "runningSince") }
            tagsByItem[item.id]?.takeIf { it.isNotEmpty() }?.let { names -> fields["tags"] = JsonArray(names.map(::JsonPrimitive)) }
            out += SyncRecord(Kind.ITEM, book.uid(Kind.ITEM, item.id), JsonObject(fields))
        }

        fun <T> add(kind: String, rows: List<T>, idOf: (T) -> Long) {
            val shape = shape<T>(kind)
            for (row in rows) {
                val fields = encode(shape.serializer, row).toMutableMap()
                fields.remove("id")
                for ((field, target) in Kind.links[kind].orEmpty()) {
                    val local = (fields[field] as? JsonPrimitive)?.content?.toLongOrNull()
                    fields[field] = book.uidOrNull(target, local).toJson()
                }
                out += SyncRecord(kind, book.uid(kind, idOf(row)), JsonObject(fields))
            }
        }
        add(Kind.LOG, snap.logs) { it.id }
        add(Kind.EVENT, snap.events) { it.id }
        add(Kind.TASK, snap.tasks) { it.id }
        add(Kind.SUBJECT, snap.subjects) { it.id }
        add(Kind.SLOT, snap.slots) { it.id }
        add(Kind.TERM, snap.terms) { it.id }
        add(Kind.COUNTDOWN, snap.countdowns) { it.id }
        add(Kind.ACCOUNT, snap.moneyAccounts) { it.id }
        add(Kind.ENTRY, snap.moneyEntries) { it.id }
        add(Kind.QUICK, snap.moneyQuick) { it.id }

        // a day already names itself, so a day note needs no uid of its own
        for (note in snap.dayNotes) {
            out += SyncRecord(Kind.DAY_NOTE, "day-${note.day}", encode(DayNote.serializer(), note).without("day"))
        }
        for (theme in settings.settings.first().customThemes) {
            out += SyncRecord(Kind.THEME, theme.id, encode(AtlasTheme.serializer(), theme))
        }

        if (book.changed.isNotEmpty()) dao.upsertIds(book.changed)
        return out
    }

    // writes what came from the other device in one go, parents before the rows that point at them
    suspend fun apply(upserts: List<SyncRecord>, deletes: List<RecordRef>, relinks: List<Relink>) {
        val themes = upserts.filter { it.kind == Kind.THEME }
        val themeDeletes = deletes.filter { it.kind == Kind.THEME }.map { it.uid }.toSet()

        db.inTransaction {
            val book = IdBook(dao.allIds())
            // rows both devices made on their own get one shared uid before anything else happens
            for (relink in relinks) {
                book.local(relink.kind, relink.from)?.let { local -> book.link(relink.kind, local, relink.to) }
            }
            val running = db.trackers().all().associate { it.itemId to it.runningSince }

            for (kind in Kind.order.asReversed()) {
                for (ref in deletes.filter { it.kind == kind }) delete(ref, book)
            }
            val byKind = upserts.groupBy { it.kind }
            for (kind in Kind.order) {
                val rows = byKind[kind].orEmpty()
                when (kind) {
                    Kind.ITEM -> parentsFirst(rows).forEach { writeItem(it, book, running) }
                    Kind.DAY_NOTE -> rows.forEach { writeDayNote(it) }
                    Kind.THEME -> Unit
                    else -> rows.forEach { writeRow(it, book) }
                }
            }
            if (book.changed.isNotEmpty()) dao.upsertIds(book.changed)
        }

        if (themes.isNotEmpty() || themeDeletes.isNotEmpty()) {
            val incoming = themes.mapNotNull { runCatching { json.decodeFromJsonElement(AtlasTheme.serializer(), it.fields) }.getOrNull() }
            settings.update { s ->
                val kept = s.customThemes.filter { it.id !in themeDeletes && incoming.none { new -> new.id == it.id } }
                val next = s.copy(customThemes = kept + incoming)
                // Luminescent sends the theme it is using under one fixed id
                val lum = incoming.firstOrNull { it.id == LUMINESCENT_THEME }
                when {
                    lum == null || !s.followLuminescent -> next
                    lum.dark -> next.copy(darkThemeId = lum.id, themeMode = ThemeMode.DARK)
                    else -> next.copy(lightThemeId = lum.id, themeMode = ThemeMode.LIGHT)
                }
            }
        }
    }

    private suspend fun writeItem(record: SyncRecord, book: IdBook, running: Map<Long, Long?>) {
        val f = record.fields
        val local = book.local(Kind.ITEM, record.uid)
        val base = JsonObject(f - setOf("note", "sheet", "tracker", "tags") + mapOf(
            "id" to JsonPrimitive(local ?: 0L),
            "parentId" to book.local(Kind.ITEM, record.text("parentId")).toJson(),
        ))
        val item = json.decodeFromJsonElement(Item.serializer(), base)
        val id = local ?: dao.item(item)
        if (local != null) dao.item(item) else book.link(Kind.ITEM, id, record.uid)

        dao.dropNote(id)
        record.obj("note")?.let { dao.note(json.decodeFromJsonElement(NoteBody.serializer(), it.with("itemId", id))) }
        dao.dropSheet(id)
        record.obj("sheet")?.let { dao.sheet(json.decodeFromJsonElement(SheetBody.serializer(), it.with("itemId", id))) }
        val tracker = record.obj("tracker")
        if (tracker == null) {
            dao.dropTracker(id)
        } else {
            val withRun = tracker.with("itemId", id) + ("runningSince" to running[id].toJson())
            dao.tracker(json.decodeFromJsonElement(Tracker.serializer(), JsonObject(withRun)))
        }
        dao.dropTags(id)
        (f["tags"] as? JsonArray)?.forEach { name ->
            val tag = name.jsonPrimitive.content
            val tagId = dao.tagId(tag) ?: run {
                dao.newTag(tag)
                dao.tagId(tag)
            }
            if (tagId != null) dao.linkTag(id, tagId)
        }
    }

    private suspend fun writeRow(record: SyncRecord, book: IdBook) {
        val shape = shapes[record.kind] ?: return
        val fields = record.fields.toMutableMap()
        for ((field, target) in Kind.links[record.kind].orEmpty()) {
            val local = book.local(target, record.text(field))
            // a log for a tracker that is not here can not be saved, it comes across next time
            if (local == null && Kind.required[record.kind] == field) return
            fields[field] = local.toJson()
        }
        val local = book.local(record.kind, record.uid)
        fields["id"] = JsonPrimitive(local ?: 0L)
        val id = writeTyped(record.kind, JsonObject(fields))
        if (local == null) book.link(record.kind, id, record.uid)
    }

    private suspend fun writeTyped(kind: String, fields: JsonObject): Long = when (kind) {
        Kind.LOG -> dao.log(json.decodeFromJsonElement(LogEntry.serializer(), fields))
        Kind.EVENT -> dao.event(json.decodeFromJsonElement(Event.serializer(), fields))
        Kind.TASK -> dao.task(json.decodeFromJsonElement(Task.serializer(), fields))
        Kind.SUBJECT -> dao.subject(json.decodeFromJsonElement(Subject.serializer(), fields))
        Kind.SLOT -> dao.slot(json.decodeFromJsonElement(TimetableSlot.serializer(), fields))
        Kind.TERM -> dao.term(json.decodeFromJsonElement(Term.serializer(), fields))
        Kind.COUNTDOWN -> dao.countdown(json.decodeFromJsonElement(Countdown.serializer(), fields))
        Kind.ACCOUNT -> dao.account(json.decodeFromJsonElement(MoneyAccount.serializer(), fields))
        Kind.ENTRY -> dao.entry(json.decodeFromJsonElement(MoneyEntry.serializer(), fields))
        Kind.QUICK -> dao.quick(json.decodeFromJsonElement(MoneyQuick.serializer(), fields))
        else -> error("Nothing writes $kind")
    }

    private suspend fun writeDayNote(record: SyncRecord) {
        val day = record.uid.removePrefix("day-").toLongOrNull() ?: return
        dao.dayNote(json.decodeFromJsonElement(DayNote.serializer(), record.fields.with("day", day)))
    }

    private suspend fun delete(ref: RecordRef, book: IdBook) {
        if (ref.kind == Kind.DAY_NOTE) {
            ref.uid.removePrefix("day-").toLongOrNull()?.let { dao.deleteDayNote(it) }
            return
        }
        val local = book.local(ref.kind, ref.uid) ?: return
        when (ref.kind) {
            Kind.ITEM -> dao.deleteItem(local)
            Kind.LOG -> dao.deleteLog(local)
            Kind.EVENT -> dao.deleteEvent(local)
            Kind.TASK -> dao.deleteTask(local)
            Kind.SUBJECT -> dao.deleteSubject(local)
            Kind.SLOT -> dao.deleteSlot(local)
            Kind.TERM -> dao.deleteTerm(local)
            Kind.COUNTDOWN -> dao.deleteCountdown(local)
            Kind.ACCOUNT -> dao.deleteAccount(local)
            Kind.ENTRY -> dao.deleteEntry(local)
            Kind.QUICK -> dao.deleteQuick(local)
        }
        dao.forgetId(ref.kind, local)
    }

    // folders come before what is inside them, so every parent already has a local id
    private fun parentsFirst(rows: List<SyncRecord>): List<SyncRecord> {
        val byUid = rows.associateBy { it.uid }
        val done = LinkedHashMap<String, SyncRecord>()
        fun visit(record: SyncRecord, depth: Int) {
            if (record.uid in done || depth > 64) return
            record.text("parentId")?.let { parent -> byUid[parent]?.let { visit(it, depth + 1) } }
            done[record.uid] = record
        }
        rows.forEach { visit(it, 0) }
        return done.values.toList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> shape(kind: String): Shape<T> = shapes.getValue(kind) as Shape<T>

    private fun <T> encode(serializer: KSerializer<T>, value: T): JsonObject = json.encodeToJsonElement(serializer, value).jsonObject

    private fun JsonObject.without(vararg keys: String): JsonObject = JsonObject(this - keys.toSet())

    private fun JsonObject.with(key: String, value: Long): JsonObject = JsonObject(this + (key to JsonPrimitive(value)))

    private fun String?.toJson(): JsonElement = if (this == null) JsonNull else JsonPrimitive(this)

    private fun Long?.toJson(): JsonElement = if (this == null) JsonNull else JsonPrimitive(this)
}

// a row both devices made separately, the "from" uid is renamed to "to" before anything is written
@kotlinx.serialization.Serializable
data class Relink(val kind: String, val from: String, val to: String)

// the id Luminescent's current theme travels under
const val LUMINESCENT_THEME = "luminescent-current"
