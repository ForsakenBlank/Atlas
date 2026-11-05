package com.forsakenblank.atlas

import com.forsakenblank.atlas.data.Event
import com.forsakenblank.atlas.data.Repeat
import com.forsakenblank.atlas.sync.SyncRecord
import com.forsakenblank.atlas.util.ChartGroup
import com.forsakenblank.atlas.util.MdBlock
import com.forsakenblank.atlas.util.StatRange
import com.forsakenblank.atlas.util.chartBuckets
import com.forsakenblank.atlas.util.continueList
import com.forsakenblank.atlas.util.markdownToPlain
import com.forsakenblank.atlas.util.occursOn
import com.forsakenblank.atlas.util.parseMarkdown
import com.forsakenblank.atlas.util.periodAverages
import com.forsakenblank.atlas.util.setHeading
import com.forsakenblank.atlas.util.startMillis
import com.forsakenblank.atlas.util.statPeriod
import com.forsakenblank.atlas.util.toggleTaskLine
import com.forsakenblank.atlas.util.weekSpans
import com.forsakenblank.atlas.util.withSkipped
import com.forsakenblank.atlas.util.wrapSelection
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class Release103Test {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun allDay(id: Long, from: LocalDate, days: Long) =
        Event(id = id, title = "e$id", startsAt = from.startMillis(), endsAt = from.plusDays(days).startMillis(), allDay = true)

    @Test
    fun oneFridayCanBeTakenOutAndPutBack() {
        val friday = LocalDate.of(2026, 11, 6)
        val weekly = Event(title = "club", startsAt = friday.startMillis(), endsAt = friday.startMillis() + 3_600_000, repeatRule = Repeat.WEEKLY)
        val skipped = weekly.withSkipped(friday.plusWeeks(1), true)
        assertTrue(skipped.occursOn(friday))
        assertFalse(skipped.occursOn(friday.plusWeeks(1)))
        assertTrue(skipped.occursOn(friday.plusWeeks(2)))
        assertEquals(null, skipped.withSkipped(friday.plusWeeks(1), false).skippedDays)
    }

    @Test
    fun multiDayEventsSpanTheirDaysInSeparateLanes() {
        val spans = weekSpans(listOf(allDay(1, monday, 3), allDay(2, monday.plusDays(1), 2), allDay(3, monday.plusDays(5), 4), allDay(4, monday, 1)), monday)
        assertEquals(listOf(0 to 2, 1 to 2, 5 to 6), spans.map { it.startCol to it.endCol })
        assertEquals(listOf(0, 1, 0), spans.map { it.lane })
        assertTrue(spans[2].continuesAfter)
        assertFalse(spans[0].continuesBefore)
    }

    @Test
    fun periodsGoBackAndAveragesCountEmptyDays() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals(LocalDate.of(2026, 9, 1)..LocalDate.of(2026, 9, 30), statPeriod(StatRange.MONTH, 2, today, today))
        assertEquals(LocalDate.of(2026, 1, 1)..today, statPeriod(StatRange.YEAR, 0, today, today))
        assertEquals(3, chartBuckets(LocalDate.of(2026, 1, 15)..LocalDate.of(2026, 3, 2), ChartGroup.MONTH).size)
        val avg = periodAverages(620.0, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        assertEquals(20.0, avg.perDay, 0.001)
        assertEquals(620.0, avg.perMonth, 0.001)
    }

    @Test
    fun markdownReadsItsBlocks() {
        val blocks = parseMarkdown("# Top\n\ntext\n- a\n- [x] b\n> q\n---\n| h1 | h2 |\n| --- | --- |\n| x | y |")
        assertEquals(MdBlock.Heading(1, "Top"), blocks[0])
        assertEquals(MdBlock.Paragraph("text"), blocks[1])
        assertEquals(MdBlock.ListItem("•", "a", 0), blocks[2])
        assertEquals(MdBlock.TaskItem("b", true, 0, 4), blocks[3])
        assertEquals(MdBlock.Rule, blocks[5])
        assertEquals(MdBlock.Table(listOf("h1", "h2"), listOf(listOf("x", "y"))), blocks[6])
    }

    @Test
    fun markdownEdits() {
        assertEquals("a **b** c", wrapSelection("a b c", 2, 3, "**").text)
        assertEquals("a b c", wrapSelection("a **b** c", 4, 5, "**").text)
        assertEquals("## x", setHeading("# x", 1, 1, 2).text)
        assertEquals("x", setHeading("## x", 1, 1, 2).text)
        assertEquals("- [x] a", toggleTaskLine("- [ ] a", 0))
        assertEquals("- a\n- ", continueList("- a", "- a\n", 4)?.text)
        assertEquals("", continueList("- ", "- \n", 3)?.text)
        assertEquals("2. b\n3. ", continueList("2. b", "2. b\n", 5)?.text)
        assertEquals(null, continueList("hi", "hi\n", 3))
    }

    @Test
    fun plainTextKeepsTheWords() {
        assertEquals("Top\nsome bold and it\n- a\n[x] b\nq", markdownToPlain("# Top\nsome **bold** and *it*\n- a\n- [x] b\n> q"))
    }

    @Test
    fun fingerprintsDontChangeForUnusedNewFields() {
        val old = SyncRecord("event", "u", JsonObject(mapOf("title" to JsonPrimitive("a"))))
        val new = SyncRecord("event", "u", JsonObject(mapOf("title" to JsonPrimitive("a"), "skippedDays" to JsonNull)))
        val used = SyncRecord("event", "u", JsonObject(mapOf("title" to JsonPrimitive("a"), "skippedDays" to JsonPrimitive("5"))))
        assertEquals(old.hash, new.hash)
        assertTrue(old.hash != used.hash)
        val oldNote = SyncRecord("item", "u", JsonObject(mapOf("note" to JsonObject(mapOf("text" to JsonPrimitive("x"))))))
        val newNote = SyncRecord("item", "u", JsonObject(mapOf("note" to JsonObject(mapOf("text" to JsonPrimitive("x"), "markdown" to JsonPrimitive(false))))))
        assertEquals(oldNote.hash, newNote.hash)
    }
}
