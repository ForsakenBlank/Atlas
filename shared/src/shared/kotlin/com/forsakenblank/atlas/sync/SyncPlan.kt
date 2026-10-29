package com.forsakenblank.atlas.sync

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// what happened to one row on each side since the two devices last synced
enum class Change(val conflict: Boolean = false) {
    NEW_THERE,
    NEW_HERE,
    CHANGED_THERE,
    CHANGED_HERE,
    GONE_THERE,
    GONE_HERE,
    // the rest need a decision, both sides did something different to the same row
    BOTH_CHANGED(conflict = true),
    BOTH_NEW(conflict = true),
    CHANGED_HERE_GONE_THERE(conflict = true),
    GONE_HERE_CHANGED_THERE(conflict = true),
}

// which version wins, BOTH keeps the other side's as a copy next to this one
enum class Choice { THEIRS, MINE, BOTH, SKIP }

class PlanItem(
    val kind: String,
    val uid: String,
    val mine: SyncRecord?,
    val theirs: SyncRecord?,
    val change: Change,
    val default: Choice,
) {
    val key: String get() = "$kind/$uid"

    // notes and sheets can keep both versions, merging two edits of the same text by hand is up to you
    val canKeepBoth: Boolean get() = kind == Kind.ITEM && mine != null && theirs != null && mine.obj("tracker") == null && mine.text("type") != "FOLDER"
}

class SyncPlan(
    val items: List<PlanItem>,
    // rows both devices made on their own that turned out to be the same thing
    val relinks: List<Relink>,
    private val base: Map<String, String>,
    private val unchanged: Map<String, String>,
) {
    // works out what each side writes, and what both sides remember as the last synced state
    fun resolve(choices: Map<String, Choice>, copyLabel: String): Resolution {
        val pick = items.associate { it.key to (choices[it.key] ?: it.default) }.toMutableMap()

        // a log whose tracker will not be there is skipped, it would have nowhere to go
        while (true) {
            val hereAfter = existsAfter(pick, here = true)
            val thereAfter = existsAfter(pick, here = false)
            val orphans = items.filter { item ->
                val record = when (pick.getValue(item.key)) {
                    Choice.THEIRS -> item.theirs?.takeIf { it.hash != item.mine?.hash }?.let { it to hereAfter }
                    Choice.MINE -> item.mine?.takeIf { it.hash != item.theirs?.hash }?.let { it to thereAfter }
                    else -> null
                } ?: return@filter false
                val (row, after) = record
                val field = Kind.required[row.kind] ?: return@filter false
                val target = Kind.links.getValue(row.kind).getValue(field)
                val parent = row.text(field) ?: return@filter true
                "$target/$parent" !in after
            }
            if (orphans.isEmpty()) break
            orphans.forEach { pick[it.key] = Choice.SKIP }
        }

        val localUpserts = mutableListOf<SyncRecord>()
        val localDeletes = mutableListOf<RecordRef>()
        val remoteUpserts = mutableListOf<SyncRecord>()
        val remoteDeletes = mutableListOf<RecordRef>()
        val baseline = HashMap(unchanged)

        for (item in items) {
            val ref = RecordRef(item.kind, item.uid)
            when (pick.getValue(item.key)) {
                Choice.THEIRS -> {
                    if (item.theirs != null) localUpserts += item.theirs else localDeletes += ref
                    item.theirs?.let { baseline[item.key] = it.hash }
                }
                Choice.MINE -> {
                    if (item.mine != null) remoteUpserts += item.mine else remoteDeletes += ref
                    item.mine?.let { baseline[item.key] = it.hash }
                }
                Choice.BOTH -> {
                    val mine = item.mine
                    val theirs = item.theirs
                    if (mine != null && theirs != null) {
                        val copy = copyOf(theirs, copyLabel)
                        localUpserts += copy
                        remoteUpserts += mine
                        remoteUpserts += copy
                        baseline[item.key] = mine.hash
                        baseline[copy.key] = copy.hash
                    }
                }
                // left as it was last time, so it shows up again next sync
                Choice.SKIP -> base[item.key]?.let { baseline[item.key] = it }
            }
        }
        return Resolution(localUpserts, localDeletes, remoteUpserts, remoteDeletes, relinks, baseline)
    }

    private fun existsAfter(pick: Map<String, Choice>, here: Boolean): Set<String> {
        val out = HashSet(unchanged.keys)
        for (item in items) {
            val record = when (pick.getValue(item.key)) {
                Choice.THEIRS -> item.theirs
                Choice.MINE -> item.mine
                Choice.BOTH -> item.mine
                Choice.SKIP -> if (here) item.mine else item.theirs
            }
            if (record != null) out += item.key
        }
        return out
    }

    private fun copyOf(record: SyncRecord, label: String): SyncRecord {
        val name = record.text("name") ?: ""
        return record.copy(uid = newUid(), fields = JsonObject(record.fields + ("name" to JsonPrimitive("$name ($label)".trim()))))
    }
}

class Resolution(
    val localUpserts: List<SyncRecord>,
    val localDeletes: List<RecordRef>,
    val remoteUpserts: List<SyncRecord>,
    val remoteDeletes: List<RecordRef>,
    val relinks: List<Relink>,
    val baseline: Map<String, String>,
)

object SyncPlanner {

    // base is what both sides looked like after the last sync, empty the first time
    fun plan(mine: List<SyncRecord>, theirsIn: List<SyncRecord>, base: Map<String, String>): SyncPlan {
        val relinks = matchUp(mine, theirsIn, base)
        val theirs = renamed(theirsIn, relinks)

        val mineByKey = mine.associateBy { it.key }
        val theirsByKey = theirs.associateBy { it.key }
        val items = mutableListOf<PlanItem>()
        val unchanged = HashMap<String, String>()

        for (key in mineByKey.keys + theirsByKey.keys) {
            val m = mineByKey[key]
            val t = theirsByKey[key]
            val b = base[key]
            if (m != null && t != null && m.hash == t.hash) {
                unchanged[key] = m.hash
                continue
            }
            val any = m ?: t ?: continue
            val change = when {
                m != null && t == null -> when {
                    b == null -> Change.NEW_HERE
                    m.hash == b -> Change.GONE_THERE
                    else -> Change.CHANGED_HERE_GONE_THERE
                }
                m == null && t != null -> when {
                    b == null -> Change.NEW_THERE
                    t.hash == b -> Change.GONE_HERE
                    else -> Change.GONE_HERE_CHANGED_THERE
                }
                else -> when (b) {
                    null -> Change.BOTH_NEW
                    m!!.hash -> Change.CHANGED_THERE
                    t!!.hash -> Change.CHANGED_HERE
                    else -> Change.BOTH_CHANGED
                }
            }
            items += PlanItem(any.kind, any.uid, m, t, change, defaultFor(change, m, t))
        }
        items.sortWith(compareBy({ Kind.order.indexOf(it.kind) }, { it.key }))
        return SyncPlan(items, relinks, base, unchanged)
    }

    private fun defaultFor(change: Change, mine: SyncRecord?, theirs: SyncRecord?): Choice = when (change) {
        Change.NEW_THERE, Change.CHANGED_THERE, Change.GONE_THERE -> Choice.THEIRS
        Change.NEW_HERE, Change.CHANGED_HERE, Change.GONE_HERE -> Choice.MINE
        // an edit beats a delete, nothing you typed is thrown away without asking
        Change.CHANGED_HERE_GONE_THERE -> Choice.MINE
        Change.GONE_HERE_CHANGED_THERE -> Choice.THEIRS
        // the newer edit wins, a tie keeps this device's
        Change.BOTH_CHANGED, Change.BOTH_NEW -> {
            val mineAt = mine?.long("updated") ?: 0
            val theirsAt = theirs?.long("updated") ?: 0
            if (theirsAt > mineAt) Choice.THEIRS else Choice.MINE
        }
    }

    // rows made on both devices before they ever synced, like the same starter pack, become one row
    private fun matchUp(mine: List<SyncRecord>, theirs: List<SyncRecord>, base: Map<String, String>): List<Relink> {
        val theirKeys = theirs.mapTo(HashSet()) { it.key }
        val myKeys = mine.mapTo(HashSet()) { it.key }
        val onlyMine = mine.filter { it.key !in theirKeys && it.key !in base }
        val onlyTheirs = theirs.filter { it.key !in myKeys && it.key !in base }
        if (onlyMine.isEmpty() || onlyTheirs.isEmpty()) return emptyList()

        val myNames = Naturals(mine)
        val theirNames = Naturals(theirs)
        val relinks = mutableListOf<Relink>()
        for (kind in Kind.order) {
            val waiting = onlyMine.filter { it.kind == kind }.groupBy { myNames.of(it) }.mapValues { it.value.toMutableList() }
            for (record in onlyTheirs.filter { it.kind == kind }) {
                val name = theirNames.of(record) ?: continue
                val match = waiting[name]?.removeFirstOrNull() ?: continue
                relinks += Relink(kind, record.uid, match.uid)
            }
        }
        return relinks
    }

    // the other side's rows with relinked uids swapped in, including every field pointing at them
    private fun renamed(records: List<SyncRecord>, relinks: List<Relink>): List<SyncRecord> {
        if (relinks.isEmpty()) return records
        val to = relinks.associate { "${it.kind}/${it.from}" to it.to }
        return records.map { record ->
            val uid = to["${record.kind}/${record.uid}"] ?: record.uid
            var fields = record.fields
            for ((field, target) in Kind.links[record.kind].orEmpty()) {
                val old = record.text(field) ?: continue
                to["$target/$old"]?.let { fields = JsonObject(fields + (field to JsonPrimitive(it))) }
            }
            if (uid == record.uid && fields === record.fields) record else SyncRecord(record.kind, uid, fields)
        }
    }

    // what a row is called no matter which device made it, so two copies of the same thing can be spotted
    private class Naturals(records: List<SyncRecord>) {
        private val byKey = records.associateBy { it.key }
        private val memo = HashMap<String, String?>()

        fun of(record: SyncRecord): String? = memo.getOrPut(record.key) { compute(record, 0) }

        private fun parent(kind: String, uid: String?, depth: Int): String =
            uid?.let { byKey["$kind/$it"] }?.let { if (depth > 32) null else compute(it, depth + 1) } ?: "-"

        private fun lower(record: SyncRecord, field: String) = record.text(field)?.trim()?.lowercase().orEmpty()

        private fun compute(r: SyncRecord, depth: Int): String? = when (r.kind) {
            Kind.ITEM -> "${r.text("type")}|${lower(r, "name")}|${parent(Kind.ITEM, r.text("parentId"), depth)}"
            Kind.LOG -> "${parent(Kind.ITEM, r.text("trackerId"), depth)}|${r.text("timestamp")}|${r.text("value")}"
            Kind.SUBJECT -> lower(r, "name")
            Kind.SLOT -> "${parent(Kind.SUBJECT, r.text("subjectId"), depth)}|${r.text("dayOfWeek")}|${r.text("startMinute")}|${r.text("week")}"
            Kind.TERM -> "${lower(r, "name")}|${r.text("startDay")}"
            Kind.EVENT -> "${lower(r, "title")}|${r.text("startsAt")}"
            Kind.TASK -> "${lower(r, "title")}|${r.text("due")}"
            Kind.COUNTDOWN -> "${lower(r, "title")}|${r.text("date")}"
            Kind.ACCOUNT -> lower(r, "name")
            Kind.ENTRY -> "${parent(Kind.ACCOUNT, r.text("accountId"), depth)}|${r.text("day")}|${r.text("amountPence")}|${r.text("created")}"
            Kind.QUICK -> "${parent(Kind.ACCOUNT, r.text("accountId"), depth)}|${lower(r, "name")}"
            else -> null
        }
    }
}
