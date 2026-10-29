package com.forsakenblank.atlas.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

// what the other device can hold, from the "accepts" part of its hello. another Atlas sends none and gets everything.
//   kinds: record kinds it keeps, like ["item", "log"]
//   items: item types it keeps, "TRACKER:TIMER" narrows trackers to one kind
class Scope private constructor(private val kinds: Set<String>?, private val items: Set<String>?) {

    fun filter(records: List<SyncRecord>): List<SyncRecord> {
        if (kinds == null && items == null) return records
        val keptItems = records.filter { it.kind == Kind.ITEM && itemAllowed(it) }.mapTo(HashSet()) { it.uid }
        return records.filter { record ->
            when (record.kind) {
                Kind.ITEM -> record.uid in keptItems
                // a log goes wherever its tracker goes
                Kind.LOG -> kindAllowed(Kind.LOG) && record.text("trackerId") in keptItems
                else -> kindAllowed(record.kind)
            }
        }
    }

    private fun kindAllowed(kind: String) = kinds == null || kind in kinds

    private fun itemAllowed(record: SyncRecord): Boolean {
        if (!kindAllowed(Kind.ITEM)) return false
        if (items == null) return true
        val type = record.text("type") ?: return false
        if (type in items) return true
        val trackerKind = (record.obj("tracker")?.get("kind") as? JsonPrimitive)?.contentOrNull ?: return false
        return "$type:$trackerKind" in items
    }

    companion object {
        fun from(accepts: JsonElement?): Scope {
            val obj = accepts as? JsonObject ?: return Scope(null, null)
            fun list(name: String) = (obj[name] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
            return Scope(list("kinds"), list("items"))
        }
    }
}
