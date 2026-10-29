package com.forsakenblank.atlas.ui.sync

import com.forsakenblank.atlas.sync.Change
import com.forsakenblank.atlas.sync.Kind
import com.forsakenblank.atlas.sync.PlanItem
import com.forsakenblank.atlas.sync.SyncRecord
import com.forsakenblank.atlas.util.formatDuration
import com.forsakenblank.atlas.util.penceInput
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// turns raw sync records into words for the review list
class Labels(private val names: Map<String, String>, private val currency: String, private val peer: String) {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

    // the version that is about to be written somewhere, the one people care about
    private fun shown(item: PlanItem): SyncRecord? = when (item.change) {
        Change.NEW_HERE, Change.CHANGED_HERE, Change.GONE_HERE, Change.CHANGED_HERE_GONE_THERE -> item.mine ?: item.theirs
        else -> item.theirs ?: item.mine
    }

    fun category(item: PlanItem): String = when (item.kind) {
        Kind.ITEM -> if (shown(item)?.obj("tracker") != null) "Trackers" else "Notes, sheets and folders"
        Kind.LOG -> "Tracker logs"
        Kind.EVENT, Kind.COUNTDOWN, Kind.DAY_NOTE -> "Calendar"
        Kind.TASK -> "Tasks"
        Kind.SUBJECT, Kind.SLOT, Kind.TERM -> "Timetable"
        Kind.ACCOUNT, Kind.ENTRY, Kind.QUICK -> "Money"
        Kind.THEME -> "Themes"
        else -> "Other"
    }

    fun title(item: PlanItem): String {
        val r = shown(item) ?: return "Something"
        return when (item.kind) {
            Kind.LOG -> names[r.text("trackerId")]?.ifBlank { null } ?: "A tracker"
            Kind.EVENT, Kind.TASK, Kind.COUNTDOWN -> r.text("title")
            Kind.SLOT -> names[r.text("subjectId")]?.let { "$it class" }
            Kind.ENTRY -> money(r.long("amountPence") ?: 0) + (r.text("category")?.let { " · $it" } ?: "")
            Kind.DAY_NOTE -> item.uid.removePrefix("day-").toLongOrNull()?.let { "Note for ${LocalDate.ofEpochDay(it).format(dayFormat)}" }
            else -> r.text("name")
        }?.ifBlank { null } ?: "Untitled"
    }

    fun detail(item: PlanItem): String {
        val what = when (item.change) {
            Change.NEW_THERE -> "New on the $peer"
            Change.CHANGED_THERE -> "Changed on the $peer"
            Change.GONE_THERE -> "Deleted on the $peer"
            Change.NEW_HERE -> "New on this device"
            Change.CHANGED_HERE -> "Changed on this device"
            Change.GONE_HERE -> "Deleted on this device"
            Change.BOTH_CHANGED -> "Changed on both"
            Change.BOTH_NEW -> "Made on both, a little differently"
            Change.CHANGED_HERE_GONE_THERE -> "Changed here, deleted on the $peer"
            Change.GONE_HERE_CHANGED_THERE -> "Deleted here, changed on the $peer"
        }
        val r = shown(item) ?: return what
        val extra = when (item.kind) {
            Kind.LOG -> logAmount(r)
            Kind.ENTRY -> r.text("note")
            else -> null
        }
        return if (extra.isNullOrBlank()) what else "$what · $extra"
    }

    // tracker logs add up into one line, the thing worth knowing at a glance
    fun groupSummary(group: List<PlanItem>): String {
        val count = if (group.size == 1) "1 item" else "${group.size} items"
        if (group.firstOrNull()?.kind != Kind.LOG) return count
        val seconds = group.sumOf { shown(it)?.long("durationSeconds") ?: 0L }
        val logs = if (group.size == 1) "1 log" else "${group.size} logs"
        return if (seconds > 0) "$logs · ${formatDuration(seconds)} of time" else logs
    }

    private fun logAmount(r: SyncRecord): String? {
        val seconds = r.long("durationSeconds") ?: 0
        if (seconds > 0) return formatDuration(seconds)
        val value = r.text("value")?.toDoubleOrNull() ?: return null
        return if (value == 1.0) null else value.toString().removeSuffix(".0")
    }

    private fun money(pence: Long): String = (if (pence < 0) "-" else "+") + currency + penceInput(kotlin.math.abs(pence))
}
