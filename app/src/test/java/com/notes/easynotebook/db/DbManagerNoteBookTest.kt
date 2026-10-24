package com.notes.easynotebook.db

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class DbManagerNoteBookTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(DbNameClassNoteBook.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DbNameClassNoteBook.DATABASE_NAME)
    }

    @Test
    fun createEditDeleteAndRestartPreserveNotes() {
        val firstManager = DbManagerNoteBook(context)
        val insert = firstManager.insertToDb("Initial", "Original body", 100L)
        val id = assertSuccess(insert)

        assertSuccess(firstManager.updateItem("Updated", "Updated body", id, 200L))
        firstManager.closeDb()

        val restartedManager = DbManagerNoteBook(context)
        val note = assertSuccess(restartedManager.readNote(id))
        requireNotNull(note)
        assertEquals("Updated", note.title)
        assertEquals("Updated body", note.desc)
        assertEquals(200L, note.time.time)

        assertSuccess(restartedManager.removeItemFromDb(id))
        assertTrue(assertSuccess(restartedManager.readDbData()).isEmpty())
        restartedManager.closeDb()
    }

    @Test
    fun longUnicodeNoteRoundTripsWithoutDataLoss() {
        val manager = DbManagerNoteBook(context)
        val content = "Привіт 🚀 漢字\n".repeat(30_000)
        val id = assertSuccess(manager.insertToDb("Unicode", content, 300L))

        val note = requireNotNull(assertSuccess(manager.readNote(id)))
        assertEquals(content, note.desc)
        manager.closeDb()
    }

    @Test
    fun upgradeFromVersionOnePreservesExistingRows() {
        context.openOrCreateDatabase(DbNameClassNoteBook.DATABASE_NAME, Context.MODE_PRIVATE, null).use { database ->
            database.execSQL(DbNameClassNoteBook.CREATE_TABLE)
            database.execSQL(
                "INSERT INTO ${DbNameClassNoteBook.TABLE_NAME} " +
                    "(${DbNameClassNoteBook.COLUMN_NAME_ID}, ${DbNameClassNoteBook.COLUMN_NAME_TITLE}, " +
                    "${DbNameClassNoteBook.COLUMN_NAME_CONTENT}, ${DbNameClassNoteBook.COLUMN_NAME_TIME}) " +
                    "VALUES (7, 'Legacy title', 'Legacy content', 400)"
            )
            database.version = 1
        }

        val manager = DbManagerNoteBook(context)
        val note = requireNotNull(assertSuccess(manager.readNote(7)))
        assertEquals("Legacy title", note.title)
        assertEquals("Legacy content", note.desc)
        assertEquals(400L, note.time.time)
        manager.closeDb()
    }

    @Test
    fun updateOfMissingNoteReturnsFailureInsteadOfReportingSuccess() {
        val manager = DbManagerNoteBook(context)

        val result = manager.updateItem("Missing", "No row", 404, 500L)

        assertTrue(result is DbResult.Failure)
        manager.closeDb()
    }

    @Test
    fun staleEditorUpdateCannotOverwriteNewerNote() {
        val manager = DbManagerNoteBook(context)
        val id = assertSuccess(manager.insertToDb("Initial", "Initial body", 700L))
        assertSuccess(manager.updateItem("Newer", "Newer body", id, 800L, expectedUpdatedAtMs = 700L))

        val staleResult = manager.updateItem("Stale", "Stale body", id, 900L, expectedUpdatedAtMs = 700L)

        assertTrue(staleResult is DbResult.Failure)
        val note = requireNotNull(assertSuccess(manager.readNote(id)))
        assertEquals("Newer body", note.desc)
        manager.closeDb()
    }

    @Test
    fun exactPlainTextRoundTripsAtTenKbThroughFiveMb() {
        val manager = DbManagerNoteBook(context)
        listOf(10 * 1024, 100 * 1024, 500 * 1024, 1024 * 1024, 5 * 1024 * 1024).forEachIndexed { index, bytes ->
            val content = largeText(bytes)
            val id = assertSuccess(manager.insertToDb("Size $bytes", content, 1_000L + index))
            val restored = requireNotNull(assertSuccess(manager.readNote(id))).desc
            assertEquals(content.toByteArray(Charsets.UTF_8).size, restored.toByteArray(Charsets.UTF_8).size)
            assertEquals(sha256(content), sha256(restored))
            assertEquals(content, restored)
        }
        manager.closeDb()
    }

    @Test
    fun listProjectionDoesNotLoadBodiesForHundredsOfNotes() {
        val manager = DbManagerNoteBook(context)
        repeat(200) { index -> assertSuccess(manager.insertToDb("Title $index", "x".repeat(2_000), 2_000L + index)) }
        repeat(3) { index -> assertSuccess(manager.insertToDb("Large $index", "y".repeat(1024 * 1024), 3_000L + index)) }

        val list = assertSuccess(manager.readDbData())

        assertEquals(203, list.size)
        assertTrue(list.all { it.desc.isEmpty() })
        manager.closeDb()
    }

    @Test
    fun complexUnicodeFormattingRoundTripsExactly() {
        val content = """
            \t  Leading spaces and tabs\n
            - nested bullet\n  1. numbered\n    - [ ] checklist\n
            ```kotlin\nval url = \"https://example.com/a?x=1&y=two#frag\"\n```\n
            Cyrillic: Привіт; Arabic: مرحبا بالعالم; CJK: 漢字かな; bidi: abc אבג 123\n
            Emoji: 👨🏽‍💻 👩‍👩‍👧‍👦 🇺🇦 🏳️‍🌈; combining: e\u0301 n\u0303\n
            <xml attr='quotes &amp; backslash\\'>email+tag@example.com</xml>\n\n
        """.trimIndent().replace("\\n", "\n") + "\n\n\t"
        val manager = DbManagerNoteBook(context)
        val id = assertSuccess(manager.insertToDb("Unicode", content, 9_999L))
        val restored = requireNotNull(assertSuccess(manager.readNote(id))).desc

        assertEquals(sha256(content), sha256(restored))
        assertEquals(content, restored)
        manager.closeDb()
    }

    @Test
    fun sqliteChunkBoundariesPreserveSupplementaryCodePoints() {
        val manager = DbManagerNoteBook(context)
        listOf(128 * 1024, 256 * 1024, 384 * 1024).forEachIndexed { index, codePoints ->
            val content = "😀".repeat(codePoints) + "|漢字|e\u0301|"
            val id = assertSuccess(manager.insertToDb("Boundary $index", content, 11_000L + index))
            val restored = requireNotNull(assertSuccess(manager.readNote(id))).desc
            assertEquals(content, restored)
            assertEquals(sha256(content), sha256(restored))
        }
        manager.closeDb()
    }

    private fun largeText(targetBytes: Int): String {
        val token = "Latin Привіт مرحبا 漢字 👨🏽‍💻\n"
        val tokenBytes = token.toByteArray(Charsets.UTF_8).size
        val builder = StringBuilder(targetBytes)
        repeat(targetBytes / tokenBytes) { builder.append(token) }
        repeat(targetBytes - builder.toString().toByteArray(Charsets.UTF_8).size) { builder.append('x') }
        return builder.toString()
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    @Test
    fun failedReadIsNotReportedAsAnEmptyNotebook() {
        val manager = DbManagerNoteBook(context)
        assertSuccess(manager.insertToDb("Existing", "Must not become empty", 600L))
        context.openOrCreateDatabase(DbNameClassNoteBook.DATABASE_NAME, Context.MODE_PRIVATE, null).use {
            it.execSQL("DROP TABLE ${DbNameClassNoteBook.TABLE_NAME}")
        }

        val result = manager.readDbData()

        assertTrue(result is DbResult.Failure)
        manager.closeDb()
    }

    private fun <T> assertSuccess(result: DbResult<T>): T {
        assertTrue("Expected database operation to succeed, got $result", result is DbResult.Success)
        return (result as DbResult.Success).value
    }
}
