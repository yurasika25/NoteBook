package com.notes.easynotebook.core.storage

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class NoteDraftStoreTest {

    private lateinit var context: Context
    private lateinit var store: NoteDraftStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("note_editor_drafts", Context.MODE_PRIVATE).edit().clear().commit()
        store = NoteDraftStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("note_editor_drafts", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun delayedOlderWriteCannotReplaceNewerDraft() {
        val first = store.createSnapshot("new_one", null, null, "Title", "first")
        val second = store.createSnapshot("new_one", null, null, "Title", "second")

        assertTrue(store.save(second))
        assertTrue(store.save(first))

        assertEquals("second", requireNotNull((store.read("new_one") as? DraftReadResult.Available)?.draft).content)
    }

    @Test
    fun committedTombstonePreventsDelayedWriteFromRecreatingDraft() {
        val snapshot = store.createSnapshot("edit_7", 7, 100L, "Title", "body")
        assertTrue(store.save(snapshot))
        assertTrue(store.markCommitted("edit_7", snapshot.revision))

        assertTrue(store.save(snapshot))
        assertTrue(store.read("edit_7") is DraftReadResult.Missing)
    }

    @Test
    fun multipleUnfinishedNewNotesRemainIndependentlyRecoverable() {
        val first = store.createSnapshot("new_first", null, null, "First", "one")
        val second = store.createSnapshot("new_second", null, null, "Second", "two")
        val edit = store.createSnapshot("edit_9", 9, 900L, "Edit", "three")
        assertTrue(store.save(first))
        assertTrue(store.save(second))
        assertTrue(store.save(edit))

        val recovered = store.unfinishedNewNotes()

        assertEquals(setOf("new_first", "new_second"), recovered.map { it.key }.toSet())
    }

    @Test
    fun longUnicodeDraftPreservesTextAndBaseVersion() {
        val content = "Привіт 🚀 漢字\n".repeat(30_000)
        val snapshot = store.createSnapshot("edit_8", 8, 808L, "Unicode", content)

        assertTrue(store.save(snapshot))
        val restored = requireNotNull((store.read("edit_8") as? DraftReadResult.Available)?.draft)

        assertEquals(content, restored.content)
        assertEquals(808L, restored.baseUpdatedAtMs)
        assertEquals(8, restored.noteId)
    }

    @Test
    fun newerEditAfterCommittedSaveCanCreateNewDraftButOldOneCannot() {
        val beforeSave = store.createSnapshot("edit_3", 3, 300L, "Old", "old")
        assertTrue(store.save(beforeSave))
        assertTrue(store.markCommitted("edit_3", beforeSave.revision))

        val afterSave = store.createSnapshot("edit_3", 3, 301L, "New", "new")
        assertTrue(store.save(afterSave))

        val restored = requireNotNull((store.read("edit_3") as? DraftReadResult.Available)?.draft)
        assertEquals("new", restored.content)
        assertTrue(restored.revision > beforeSave.revision)
    }

    @Test
    fun draftSurvivesMissingPreferencesIndexBecauseAtomicFileIsAuthoritative() {
        val snapshot = store.createSnapshot("new_index_loss", null, null, "Recovered", "important body")
        assertTrue(store.save(snapshot))
        context.getSharedPreferences("note_editor_drafts", Context.MODE_PRIVATE).edit().clear().commit()

        val recovered = (store.read("new_index_loss") as? DraftReadResult.Available)?.draft

        assertEquals("important body", requireNotNull(recovered).content)
        assertEquals("new_index_loss", store.unfinishedNewNotes().single().key)
    }

    @Test
    fun fiveMegabyteUnicodeDraftRoundTripsWithExactHash() {
        val content = largePlainText(5 * 1024 * 1024)
        val snapshot = store.createSnapshot("new_five_mb", null, null, "Large", content)

        assertTrue(store.save(snapshot))
        val restored = requireNotNull((store.read("new_five_mb") as? DraftReadResult.Available)?.draft).content

        assertEquals(content.toByteArray(Charsets.UTF_8).size, restored.toByteArray(Charsets.UTF_8).size)
        assertEquals(sha256(content), sha256(restored))
        assertEquals(content, restored)
    }

    private fun largePlainText(targetBytes: Int): String {
        val token = "Привіт 🚀 漢字 عربى\n"
        val tokenBytes = token.toByteArray(Charsets.UTF_8).size
        val builder = StringBuilder(targetBytes)
        repeat(targetBytes / tokenBytes) { builder.append(token) }
        repeat(targetBytes - builder.toString().toByteArray(Charsets.UTF_8).size) { builder.append('x') }
        return builder.toString()
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
