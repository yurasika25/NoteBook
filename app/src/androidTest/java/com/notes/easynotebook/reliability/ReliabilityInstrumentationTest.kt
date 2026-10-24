package com.notes.easynotebook.reliability

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notes.easynotebook.core.storage.DraftReadResult
import com.notes.easynotebook.core.storage.NoteDraftStore
import com.notes.easynotebook.db.DbManagerNoteBook
import com.notes.easynotebook.db.DbResult
import com.notes.easynotebook.main.MainActivity
import com.notes.easynotebook.ui.FragmentAddNote
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ReliabilityInstrumentationTest {

    private lateinit var context: Context
    private lateinit var manager: DbManagerNoteBook
    private lateinit var drafts: NoteDraftStore
    private lateinit var testDatabase: String
    private lateinit var testNamespace: String

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        context = target.createDeviceProtectedStorageContext()
        val runId = System.currentTimeMillis().toString()
        testDatabase = "instrumentation_reliability_$runId.db"
        testNamespace = "instrumentation_reliability_$runId"
        manager = DbManagerNoteBook(context, testDatabase)
        drafts = NoteDraftStore(context, testNamespace)
    }

    @After
    fun tearDown() {
        manager.closeDb()
        context.deleteDatabase(testDatabase)
        context.getSharedPreferences("note_editor_drafts_$testNamespace", Context.MODE_PRIVATE)
            .edit().clear().commit()
        File(context.filesDir, "note_editor_drafts_$testNamespace").deleteRecursively()
    }

    @Test
    fun sqliteCrudUnicodeAndReopenRoundTrip() {
        val content = complexText()
        val id = assertSuccess(manager.insertToDb("Unicode", content, 10L))
        val first = requireNotNull(assertSuccess(manager.readNote(id)))
        assertEquals(content, first.desc)

        assertSuccess(manager.updateItem("Updated", content + "\nupdated", id, 20L, 10L))
        manager.closeDb()
        manager = DbManagerNoteBook(context, testDatabase)
        assertEquals(content + "\nupdated", requireNotNull(assertSuccess(manager.readNote(id))).desc)
        assertSuccess(manager.removeItemFromDb(id))
        assertTrue(assertSuccess(manager.readDbData()).isEmpty())
    }

    @Test
    fun largeNotesRoundTripFrom100KbThrough5Mb() {
        listOf(100 * 1024, 500 * 1024, 1024 * 1024, 5 * 1024 * 1024).forEachIndexed { index, bytes ->
            val content = sizedText(bytes)
            val id = assertSuccess(manager.insertToDb("Size $bytes", content, 100L + index))
            val restored = requireNotNull(assertSuccess(manager.readNote(id))).desc
            assertEquals(content.toByteArray(Charsets.UTF_8).size, restored.toByteArray(Charsets.UTF_8).size)
            assertEquals(sha256(content), sha256(restored))
            assertEquals(content, restored)
        }
    }

    @Test
    fun sqliteChunkBoundariesPreserveEmojiCodePoints() {
        listOf(128 * 1024, 256 * 1024, 384 * 1024).forEachIndexed { index, count ->
            val content = "😀".repeat(count) + "|漢字|e\u0301|"
            val id = assertSuccess(manager.insertToDb("Boundary $index", content, 200L + index))
            assertEquals(content, requireNotNull(assertSuccess(manager.readNote(id))).desc)
        }
    }

    @Test
    fun atomicDraftRecoversWhenMetadataIndexIsUnavailable() {
        val snapshot = drafts.createSnapshot("new_runtime", null, null, "Recovered", complexText())
        assertTrue(drafts.save(snapshot))
        context.getSharedPreferences("note_editor_drafts_$testNamespace", Context.MODE_PRIVATE)
            .edit().clear().commit()

        val recovered = (drafts.read("new_runtime") as DraftReadResult.Available).draft
        assertEquals(snapshot.content, recovered.content)
        assertEquals("new_runtime", drafts.unfinishedNewNotes().single().key)
    }

    @Test
    fun multipleDraftsAndCommittedTombstoneRemainSafe() {
        val first = drafts.createSnapshot("new_first", null, null, "First", "one")
        val second = drafts.createSnapshot("new_second", null, null, "Second", "two")
        assertTrue(drafts.save(first))
        assertTrue(drafts.save(second))
        assertEquals(2, drafts.unfinishedNewNotes().size)
        assertTrue(drafts.markCommitted("new_first", first.revision))
        assertEquals(1, drafts.unfinishedNewNotes().size)
        assertTrue(drafts.save(first))
        assertTrue(drafts.read("new_first") !is DraftReadResult.Available)
    }

    @Test
    fun staleEditorUpdateIsRejectedAndSuccessfulCommitCleansDraft() {
        val id = assertSuccess(manager.insertToDb("Initial", "initial", 700L))
        assertSuccess(manager.updateItem("Newer", "newer", id, 800L, expectedUpdatedAtMs = 700L))
        assertTrue(manager.updateItem("Stale", "stale", id, 900L, expectedUpdatedAtMs = 700L) is DbResult.Failure)
        assertEquals("newer", requireNotNull(assertSuccess(manager.readNote(id))).desc)

        val draft = drafts.createSnapshot("edit_$id", id, 800L, "Newer", "newer")
        assertTrue(drafts.save(draft))
        assertTrue(drafts.markCommitted(draft.key, draft.revision))
        assertTrue(drafts.read(draft.key) !is DraftReadResult.Available)
    }

    @Test
    fun activityRecreationRestoresDraftFromPrivateStorage() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .replace(com.notes.easynotebook.R.id.mainContainer, FragmentAddNote.newInstance())
                .commitNow()
        }
        scenario.onActivity { activity ->
            val editor = activity.supportFragmentManager.fragments.last() as FragmentAddNote
            editor.requireView().findViewById<android.widget.EditText>(com.notes.easynotebook.R.id.titleText)
                .setText("Recovered title")
            editor.requireView().findViewById<android.widget.EditText>(com.notes.easynotebook.R.id.descriptionText)
                .setText("Recovered body 🚀 漢字")
        }
        SystemClock.sleep(750)
        scenario.recreate()
        scenario.onActivity { activity ->
            val editor = activity.supportFragmentManager.fragments.last() as FragmentAddNote
            assertEquals("Recovered title", editor.requireView()
                .findViewById<android.widget.EditText>(com.notes.easynotebook.R.id.titleText).text.toString())
            assertEquals("Recovered body 🚀 漢字", editor.requireView()
                .findViewById<android.widget.EditText>(com.notes.easynotebook.R.id.descriptionText).text.toString())
        }
        scenario.close()
    }

    @Test
    fun largeBodiesAreExcludedFromFragmentArgumentsAndSavedViewState() {
        val fragment = FragmentAddNote.newInstance(42)
        val arguments = fragment.requireArguments()
        assertTrue(arguments.keySet().none { it.contains("TITLE", ignoreCase = true) || it.contains("DESK", ignoreCase = true) })
        assertTrue(arguments.keySet().all { arguments.get(it) !is String || (arguments.getString(it)?.length ?: 0) < 1_000 })

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            activity.supportFragmentManager.beginTransaction()
                .replace(com.notes.easynotebook.R.id.mainContainer, fragment).commitNow()
            assertTrue(fragment.requireView().findViewById<android.view.View>(com.notes.easynotebook.R.id.descriptionText).isSaveEnabled.not())
            assertTrue(fragment.requireView().findViewById<android.view.View>(com.notes.easynotebook.R.id.titleText).isSaveEnabled.not())
        }
        scenario.close()
    }

    @Test
    fun activityScenarioRecreateDoesNotCrash() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.recreate()
        scenario.close()
    }

    private fun complexText(): String = """
        \t  Привіт مرحبا 漢字\n
        - nested bullet\n  1. numbered\n    - [ ] checklist\n
        ```kotlin\nval url = \"https://example.com/a?x=1\"\n```\n
        👨🏽‍💻 👩‍👩‍👧‍👦 🇺🇦 🏳️‍🌈 e\u0301 אבג 123\\path\n
    """.trimIndent().replace("\\n", "\n") + "\n\n\t"

    private fun sizedText(targetBytes: Int): String {
        val token = "Привіт عربى 漢字 👨🏽‍💻\n"
        val tokenBytes = token.toByteArray(Charsets.UTF_8).size
        val builder = StringBuilder(targetBytes)
        repeat(targetBytes / tokenBytes) { builder.append(token) }
        repeat(targetBytes - builder.toString().toByteArray(Charsets.UTF_8).size) { builder.append('x') }
        return builder.toString()
    }

    private fun <T> assertSuccess(result: DbResult<T>): T {
        assertTrue("Expected success, got $result", result is DbResult.Success)
        return (result as DbResult.Success).value
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

}
