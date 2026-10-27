package com.forsakenblank.atlas

import com.forsakenblank.atlas.data.AtlasRepository
import com.forsakenblank.atlas.platform.buildAtlasDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JournalTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun editingADayFromTheCalendarKeepsItsMood() = runBlocking {
        val db = buildAtlasDatabase(folder.newFolder())
        val repo = AtlasRepository(db)
        try {
            repo.saveJournal(100, "A long walk", mood = 5)
            repo.saveDayNote(100, "A long walk, then rain")
            val note = repo.dayNotes().first().single()
            assertEquals("A long walk, then rain", note.text)
            assertEquals(5, note.mood)

            // a mood alone is still worth keeping, nothing at all is not
            repo.saveDayNote(100, "")
            assertEquals(5, repo.dayNotes().first().single().mood)
            repo.saveJournal(100, "", mood = null)
            assertTrue(repo.dayNotes().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
