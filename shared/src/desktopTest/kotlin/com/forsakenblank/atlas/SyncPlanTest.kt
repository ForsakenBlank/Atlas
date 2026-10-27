package com.forsakenblank.atlas

import com.forsakenblank.atlas.sync.Change
import com.forsakenblank.atlas.sync.Choice
import com.forsakenblank.atlas.sync.Kind
import com.forsakenblank.atlas.sync.SyncPlanner
import com.forsakenblank.atlas.sync.SyncRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlanTest {

    private fun record(kind: String, uid: String, vararg fields: Pair<String, Any?>) = SyncRecord(
        kind,
        uid,
        JsonObject(fields.associate { (k, v) -> k to if (v == null) kotlinx.serialization.json.JsonNull else JsonPrimitive(v.toString()) }),
    )

    private fun tracker(uid: String, name: String = "Reading") = record(Kind.ITEM, uid, "type" to "TRACKER", "name" to name, "parentId" to null)

    private fun log(uid: String, tracker: String, at: Long, seconds: Long) =
        record(Kind.LOG, uid, "trackerId" to tracker, "timestamp" to at, "durationSeconds" to seconds, "value" to 1.0)

    private fun baseOf(vararg records: SyncRecord) = records.associate { it.key to it.hash }

    @Test
    fun timeLoggedOnBothDevicesAddsUp() {
        val reading = tracker("t1")
        val before = log("l0", "t1", 1_000, 600)
        val onPc = log("l1", "t1", 2_000, 600)
        val onPhone = log("l2", "t1", 3_000, 300)

        val plan = SyncPlanner.plan(
            mine = listOf(reading, before, onPhone),
            theirsIn = listOf(reading, before, onPc),
            base = baseOf(reading, before),
        )
        val result = plan.resolve(emptyMap(), "copy")

        // the phone gets the pc's 10 minutes and the pc gets the phone's 5, nothing is replaced
        assertEquals(listOf("l1"), result.localUpserts.map { it.uid })
        assertEquals(listOf("l2"), result.remoteUpserts.map { it.uid })
        assertTrue(result.localDeletes.isEmpty() && result.remoteDeletes.isEmpty())
        assertEquals(setOf("item/t1", "log/l0", "log/l1", "log/l2"), result.baseline.keys)
    }

    @Test
    fun deletesOnOneSideReachTheOther() {
        val reading = tracker("t1")
        val gone = log("l1", "t1", 1_000, 60)
        val plan = SyncPlanner.plan(listOf(reading), listOf(reading, gone), baseOf(reading, gone))

        assertEquals(Change.GONE_HERE, plan.items.single().change)
        val result = plan.resolve(emptyMap(), "copy")
        assertEquals(listOf("l1"), result.remoteDeletes.map { it.uid })
        assertTrue("log/l1" !in result.baseline)
    }

    @Test
    fun editsOnBothSidesAreAConflictAndTheNewerOneIsPicked() {
        val old = record(Kind.ITEM, "n1", "type" to "NOTE", "name" to "Shopping", "updated" to 1)
        val mine = record(Kind.ITEM, "n1", "type" to "NOTE", "name" to "Shopping list", "updated" to 5)
        val theirs = record(Kind.ITEM, "n1", "type" to "NOTE", "name" to "Shop", "updated" to 9)

        val item = SyncPlanner.plan(listOf(mine), listOf(theirs), baseOf(old)).items.single()
        assertEquals(Change.BOTH_CHANGED, item.change)
        assertEquals(Choice.THEIRS, item.default)
        assertTrue(item.canKeepBoth)
    }

    @Test
    fun keepingBothMakesACopyOnEachSide() {
        val old = record(Kind.ITEM, "n1", "type" to "NOTE", "name" to "Plan")
        val mine = record(Kind.ITEM, "n1", "type" to "NOTE", "name" to "Plan A")
        val theirs = record(Kind.ITEM, "n1", "type" to "NOTE", "name" to "Plan B")

        val plan = SyncPlanner.plan(listOf(mine), listOf(theirs), baseOf(old))
        val result = plan.resolve(mapOf("item/n1" to Choice.BOTH), "from PC")

        val copy = result.localUpserts.single()
        assertEquals("Plan B (from PC)", copy.text("name"))
        assertEquals(setOf("n1", copy.uid), result.remoteUpserts.map { it.uid }.toSet())
    }

    @Test
    fun theSameStarterTrackerOnBothDevicesBecomesOne() {
        val mine = tracker("mine-uid", "Water")
        val theirs = tracker("their-uid", "Water")
        val theirLog = log("l1", "their-uid", 1_000, 0)

        val plan = SyncPlanner.plan(listOf(mine), listOf(theirs, theirLog), emptyMap())

        assertEquals("mine-uid", plan.relinks.single().to)
        // the tracker matched, only the log is new, and it now points at this device's tracker
        val item = plan.items.single()
        assertEquals(Kind.LOG, item.kind)
        assertEquals("mine-uid", item.theirs!!.text("trackerId"))
    }

    @Test
    fun aLogIsSkippedWhenItsTrackerIsLeftBehind() {
        val newTracker = tracker("t9", "Guitar")
        val newLog = log("l9", "t9", 1_000, 60)
        val plan = SyncPlanner.plan(emptyList(), listOf(newTracker, newLog), emptyMap())

        val result = plan.resolve(mapOf("item/t9" to Choice.SKIP), "copy")
        assertTrue(result.localUpserts.isEmpty())
        assertTrue(result.baseline.isEmpty())
    }

    @Test
    fun fingerprintsIgnoreFieldOrderAndTimestamps() {
        val a = record(Kind.ITEM, "x", "type" to "NOTE", "name" to "A", "updated" to 1)
        val b = SyncRecord(Kind.ITEM, "x", JsonObject(mapOf("updated" to JsonPrimitive(99), "name" to JsonPrimitive("A"), "type" to JsonPrimitive("NOTE"))))
        assertEquals(a.hash, b.hash)
    }
}
