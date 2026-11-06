package com.forsakenblank.atlas.sync

import com.forsakenblank.atlas.data.SyncId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

// every kind of row that goes across, in the order they are written so parents exist before children
object Kind {
    const val ACCOUNT = "account"
    const val ITEM = "item"
    const val SUBJECT = "subject"
    const val SLOT = "slot"
    const val TERM = "term"
    const val LOG = "log"
    const val EVENT = "event"
    const val TASK = "task"
    const val COUNTDOWN = "countdown"
    const val ENTRY = "entry"
    const val QUICK = "quick"
    const val DAY_NOTE = "daynote"
    const val THEME = "theme"

    val order = listOf(ACCOUNT, ITEM, SUBJECT, SLOT, TERM, LOG, EVENT, TASK, COUNTDOWN, ENTRY, QUICK, DAY_NOTE, THEME)

    // fields that hold another row's uid, and which kind that row is
    val links: Map<String, Map<String, String>> = mapOf(
        ITEM to mapOf("parentId" to ITEM),
        LOG to mapOf("trackerId" to ITEM),
        TASK to mapOf("subjectId" to SUBJECT),
        SUBJECT to mapOf("folderId" to ITEM),
        SLOT to mapOf("subjectId" to SUBJECT),
        ENTRY to mapOf("accountId" to ACCOUNT),
        QUICK to mapOf("accountId" to ACCOUNT),
    )

    // a row can not be written without the one it points at
    val required: Map<String, String> = mapOf(LOG to "trackerId", SLOT to "subjectId", ENTRY to "accountId", QUICK to "accountId")
}

// one row as every device sees it, ids pointing at other rows are swapped for their uids
@Serializable
data class SyncRecord(val kind: String, val uid: String, val fields: JsonObject) {
    val key: String get() = "$kind/$uid"

    // the same content always gives the same fingerprint, whatever order the fields arrived in
    val hash: String by lazy { fingerprint(this) }

    fun text(name: String): String? = (fields[name] as? JsonPrimitive)?.contentOrNull
    fun long(name: String): Long? = (fields[name] as? JsonPrimitive)?.longOrNull
    fun obj(name: String): JsonObject? = fields[name] as? JsonObject
}

@Serializable
data class RecordRef(val kind: String, val uid: String)

// fields that change on their own or only mean something on the device they are on
private val ignoredInFingerprint = setOf("updated", "created")

// newer fields count only once they are used, so rows from before they existed keep their fingerprint
private fun stableFields(fields: JsonObject): Map<String, JsonElement> {
    val out = fields.filterKeys { it !in ignoredInFingerprint }.toMutableMap()
    if (out["skippedDays"] == JsonNull) out.remove("skippedDays")
    (out["note"] as? JsonObject)?.let { note ->
        if ((note["markdown"] as? JsonPrimitive)?.contentOrNull == "false") out["note"] = JsonObject(note - "markdown")
    }
    return out
}

private fun fingerprint(record: SyncRecord): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(record.kind.toByteArray())
    digest.update(canonical(JsonObject(stableFields(record.fields))).toByteArray())
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest()).take(22)
}

// keys sorted at every level so two devices always write the same text for the same data
internal fun canonical(element: JsonElement): String = when (element) {
    is JsonObject -> element.entries.sortedBy { it.key }.joinToString(",", "{", "}") { (k, v) -> JsonPrimitive(k).toString() + ":" + canonical(v) }
    is kotlinx.serialization.json.JsonArray -> element.joinToString(",", "[", "]") { canonical(it) }
    JsonNull -> "null"
    else -> element.toString()
}

// both sides compare these before trusting that they remember the same last sync
fun baselineDigest(baseline: Map<String, String>): String {
    val text = canonical(JsonObject(baseline.mapValues { JsonPrimitive(it.value) }))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(text.toByteArray()))
}

fun newUid(): String = UUID.randomUUID().toString()

// local ids to uids and back for one sync, new pairs are saved when the sync ends
class IdBook(rows: List<SyncId>) {
    private val toUid = HashMap<String, HashMap<Long, String>>()
    private val toLocal = HashMap<String, HashMap<String, Long>>()
    private val pending = LinkedHashMap<Pair<String, Long>, SyncId>()
    val changed: List<SyncId> get() = pending.values.toList()

    init {
        rows.forEach { put(it.kind, it.localId, it.uid) }
    }

    private fun put(kind: String, local: Long, uid: String) {
        toUid.getOrPut(kind) { HashMap() }[local] = uid
        toLocal.getOrPut(kind) { HashMap() }[uid] = local
    }

    fun uid(kind: String, local: Long): String =
        toUid[kind]?.get(local) ?: newUid().also { link(kind, local, it) }

    fun uidOrNull(kind: String, local: Long?): String? = local?.let { uid(kind, it) }

    fun local(kind: String, uid: String?): Long? = uid?.let { toLocal[kind]?.get(it) }

    fun link(kind: String, local: Long, uid: String) {
        toUid[kind]?.get(local)?.let { old -> toLocal[kind]?.remove(old) }
        // a uid names one row, and a row deleted since the last sync can still be holding this one
        toLocal[kind]?.get(uid)?.takeIf { it != local }?.let { holder ->
            toUid[kind]?.remove(holder)
            pending.remove(kind to holder)
        }
        put(kind, local, uid)
        pending[kind to local] = SyncId(kind, local, uid)
    }
}
