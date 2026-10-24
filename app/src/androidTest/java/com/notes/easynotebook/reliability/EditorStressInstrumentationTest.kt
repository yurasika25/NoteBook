package com.notes.easynotebook.reliability

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notes.easynotebook.R
import com.notes.easynotebook.core.storage.DraftReadResult
import com.notes.easynotebook.core.storage.NoteDraftStore
import com.notes.easynotebook.db.DbManagerNoteBook
import com.notes.easynotebook.db.DbResult
import com.notes.easynotebook.main.MainActivity
import com.notes.easynotebook.ui.FragmentAddNote
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Opt-in real-editor stress coverage. Run with the class selector, not the whole suite. */
@RunWith(AndroidJUnit4::class)
class EditorStressInstrumentationTest {

    // FragmentAddNote uses requireContext(); use the same credential-protected store.
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = DbManagerNoteBook(context)
    private val drafts = NoteDraftStore(context)
    private val ownedIds = mutableListOf<Int>()
    @Volatile private var latestCheckpoint = "none"

    @After
    fun tearDown() {
        ownedIds.forEach { manager.removeItemFromDb(it) }
        manager.closeDb()
    }

    @Test
    fun editorRoundTripSmallDocument() = exerciseEditor(4 * 1024)

    @Test
    fun editorRoundTrip100Kb() = exerciseEditor(100 * 1024)

    @Test
    fun editorRoundTrip500Kb() = exerciseEditor(500 * 1024)

    @Ignore("Enable after editorRoundTrip100Kb passes on emulator-5554")
    @Test
    fun editorRoundTrip1Mb() = exerciseEditor(1024 * 1024)

    private fun exerciseEditor(targetBytes: Int) {
        val original = syntheticText(targetBytes)
        assertWellFormedUtf16("A generated fixture", original)
        val title = "Stress-${targetBytes}-${System.nanoTime()}"
        val id = assertSuccess(manager.insertToDb(title, original, System.currentTimeMillis()))
        ownedIds += id
        val expectedHash = sha256(original)
        val fixtureState = readNoteState(id)
        checkpoint(1, "fixture id=$id bytes=$targetBytes ${fingerprint("A", original)} db=$fixtureState")
        assertEquals(expectedHash, fixtureState.hash)
        assertWellFormedUtf16("B SQLite after insertion", requireNotNull(readNoteContent(id)))
        val beforeMemory = usedMemory()
        logJavaHeap("beforeOpen", beforeMemory)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitResumedActivity(scenario, "launch")
        val loadStart = SystemClock.elapsedRealtime()
        val loadFrames = FrameProbe()
        val initialOpen = with500KbMethodTrace(targetBytes, "editor-500kb-open.trace") {
            scenario.onActivity { loadFrames.start() }
            try {
                val opened = openExistingEditor(scenario, id, "stress-editor")
                val editor = opened.fragment
                val loadMs = SystemClock.elapsedRealtime() - loadStart
                checkpoint(3, "transaction fragmentTag=${editor.tag} noteId=${editor.arguments?.getInt("NOTE_ID_KEY", -1)} attached=${editor.isAdded} ${opened.timing.summary()}")
                checkpoint(4, "editorViewCreated=${editor.view != null} enabled=${editor.requireView().findViewById<EditText>(R.id.descriptionText).isEnabled}")

                var loaded = ""
                scenario.onActivity {
                    loaded = editor.requireView().findViewById<EditText>(R.id.descriptionText).text.toString()
                }
                assertWellFormedUtf16("C editor after load", loaded)
                checkpoint(5, "loaded ${fingerprint("C", loaded)} loadMs=$loadMs")
                assertEquals(original.length, loaded.length)
                assertEquals(expectedHash, sha256(loaded))
                InitialOpen(editor, opened, loadMs)
            } finally {
                scenario.onActivity { loadFrames.stop() }
            }
        }
        val editor = initialOpen.editor
        val opened = initialOpen.opened
        val loadMs = initialOpen.loadMs
        val afterLoadMemory = usedMemory()
        logJavaHeap("afterLoad", afterLoadMemory)
        Log.i(TAG, "LOAD_METRICS totalMs=$loadMs ${opened.timing.summary()} ${loadFrames.summary()}")

        val editStart = SystemClock.elapsedRealtime()
        var edited = ""
        scenario.onActivity {
            val body = editor.requireView().findViewById<EditText>(R.id.descriptionText)
            val cursor = lineBoundaryAtOrBefore(body.text.toString(), body.text.length / 2)
            body.setSelection(cursor)
            val addition = "\n追加🚀"
            body.text.insert(cursor, addition)
            val insertedEnd = cursor + addition.length
            val rocketStart = insertedEnd - Character.charCount(addition.codePointBefore(addition.length))
            body.text.delete(rocketStart, insertedEnd)
            body.scrollTo(0, body.layout?.height ?: 0)
            edited = body.text.toString()
        }
        val editMs = SystemClock.elapsedRealtime() - editStart
        val afterEditMemory = usedMemory()
        logJavaHeap("afterEdit", afterEditMemory)
        assertWellFormedUtf16("D editor after programmatic edit", edited)
        checkpoint(6, "expected ${fingerprint("D", edited)} editMs=$editMs")
        assertTrue("Editor did not retain the synthetic edit", edited.contains("追加"))

        val saveStart = SystemClock.elapsedRealtime()
        var saveHandlerAccepted = false
        var editorDisabledForSave = false
        var preSaveEditorText = ""
        scenario.onActivity {
            val body = editor.requireView().findViewById<EditText>(R.id.descriptionText)
            val saveButton = editor.requireView().findViewById<android.view.View>(R.id.btnSaveNote)
            assertTrue("Save button was not enabled", saveButton.isEnabled)
            // E is captured on the main thread immediately before the production handler reads it.
            preSaveEditorText = body.text.toString()
            assertWellFormedUtf16("E editor immediately before save", preSaveEditorText)
            saveButton.performClick()
            // saveData() disables the controls before launching its IO work.
            saveHandlerAccepted = !saveButton.isEnabled
            editorDisabledForSave = !body.isEnabled
        }
        checkpoint(7, "saveClickDispatched=true ${fingerprint("E", preSaveEditorText)} ${diff("D", edited, "E", preSaveEditorText)}")
        assertTrue("Production save handler did not accept the click", saveHandlerAccepted)
        checkpoint(8, "saveHandlerAccepted=true editorDisabled=$editorDisabledForSave")

        val initialTimestamp = requireNotNull(fixtureState.timestamp)
        var lastObserved = fixtureState
        var updateObserved = false
        var databaseConfirmationMs = -1L
        waitUntil(
            label = "saved note",
            condition = {
                lastObserved = readNoteState(id)
                val observedTimestamp = lastObserved.timestamp
                if (!updateObserved && observedTimestamp != null && observedTimestamp > initialTimestamp) {
                    updateObserved = true
                    databaseConfirmationMs = SystemClock.elapsedRealtime() - saveStart
                    val actual = requireNotNull(readNoteContent(id))
                    assertWellFormedUtf16("G SQLite after save", actual)
                    checkpoint(9, "updateObserved confirmationMs=$databaseConfirmationMs ${fingerprint("G", actual)} ${diff("D", edited, "G", actual)} ${diff("E", preSaveEditorText, "G", actual)}")
                }
                lastObserved.hash == sha256(edited)
            },
            onTimeout = {
                val draftState = draftState("edit_$id")
                val saveUiState = editorUiState(scenario, "stress-editor")
                val actual = readNoteContent(id)
                Log.e(TAG, "SAVE_TIMEOUT checkpoint=$latestCheckpoint expectedHash=${sha256(edited)} " +
                    "expectedChars=${edited.length} initialTimestamp=$initialTimestamp " +
                    "db=$lastObserved draft=$draftState ui=$saveUiState " +
                    "${actual?.let { fingerprint("G", it) + " " + diff("D", edited, "G", it) + " " + diff("E", preSaveEditorText, "G", it) }}")
            }
        )
        val saveMs = SystemClock.elapsedRealtime() - saveStart
        val afterSaveMemory = usedMemory()
        logJavaHeap("afterSave", afterSaveMemory)
        checkpoint(9, "updateObserved db=$lastObserved")
        assertTrue("Updated timestamp did not advance", requireNotNull(lastObserved.timestamp) > initialTimestamp)
        checkpoint(10, "readBack db=$lastObserved saveMs=$saveMs")

        // saveData() pops the editor. Wait for the underlying list and a resumed, non-state-saved Activity
        // before navigating again, exactly as production edit navigation requires.
        val navigationStart = SystemClock.elapsedRealtime()
        waitUntil("post-save resumed activity", condition = {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                    !activity.supportFragmentManager.isStateSaved &&
                    activity.supportFragmentManager.backStackEntryCount == 0
            }
            ready
        })
        val backToListMs = SystemClock.elapsedRealtime() - navigationStart
        checkpoint(11, "navigationReturnedToList=true backToListMs=$backToListMs")
        val reopenStart = SystemClock.elapsedRealtime()
        val reopenFrames = FrameProbe()
        val reopenedResult = with500KbMethodTrace(targetBytes, "editor-500kb-reopen.trace") {
            scenario.onActivity { reopenFrames.start() }
            try {
                val reopenedOpen = openExistingEditor(scenario, id, "stress-reopen")
                val reopenedEditor = reopenedOpen.fragment
                var reopened = ""
                scenario.onActivity {
                    reopened = reopenedEditor.requireView().findViewById<EditText>(R.id.descriptionText).text.toString()
                }
                val reopenMs = SystemClock.elapsedRealtime() - reopenStart
                assertWellFormedUtf16("H editor after reopen", reopened)
                checkpoint(12, "reopened ${fingerprint("H", reopened)} reopenMs=$reopenMs ${reopenedOpen.timing.summary()} ${reopenFrames.summary()}")
                assertEquals(sha256(edited), sha256(reopened))
                assertEquals(edited, reopened)
                ReopenedResult(reopened, reopenedOpen, reopenMs)
            } finally {
                scenario.onActivity { reopenFrames.stop() }
            }
        }
        val reopened = reopenedResult.content
        val reopenMs = reopenedResult.reopenMs
        val afterReopenMemory = usedMemory()
        logJavaHeap("afterReopen", afterReopenMemory)
        assertTrue("Unexpected empty editor", reopened.isNotEmpty())
        println("EDITOR_STRESS utf8Bytes=${original.toByteArray(Charsets.UTF_8).size} utf16Chars=${original.length} " +
            "loadMs=$loadMs editMs=$editMs saveConfirmationMs=$databaseConfirmationMs saveMs=$saveMs " +
            "backToListMs=$backToListMs reopenMs=$reopenMs " +
            "chars=${reopened.length} sha256=${sha256(reopened)} " +
            "heapMiBBefore=${mib(beforeMemory)} heapMiBAfterLoad=${mib(afterLoadMemory)} " +
            "heapMiBAfterEdit=${mib(afterEditMemory)} heapMiBAfterSave=${mib(afterSaveMemory)} " +
            "heapMiBAfterReopen=${mib(afterReopenMemory)}")
        scenario.close()
    }

    private fun awaitEditor(
        scenario: ActivityScenario<MainActivity>,
        expectedTag: String,
        timing: EditorLoadTiming
    ): FragmentAddNote {
        var result: FragmentAddNote? = null
        waitUntil("$expectedTag attached and loaded", condition = {
            scenario.onActivity { activity ->
                result = activity.supportFragmentManager.findFragmentByTag(expectedTag) as? FragmentAddNote
                val now = SystemClock.elapsedRealtime()
                result?.takeIf { it.isAdded }?.let { if (timing.attachedAtMs == 0L) timing.attachedAtMs = now }
                result?.view?.let { if (timing.viewCreatedAtMs == 0L) timing.viewCreatedAtMs = now }
                if (result?.view?.findViewById<EditText>(R.id.descriptionText)?.isEnabled == true && timing.editorReadyAtMs == 0L) {
                    timing.editorReadyAtMs = now
                }
            }
            result?.isAdded == true &&
                result?.view?.findViewById<EditText>(R.id.descriptionText)?.isEnabled == true
        })
        return requireNotNull(result)
    }

    private fun openExistingEditor(
        scenario: ActivityScenario<MainActivity>,
        noteId: Int,
        tag: String
    ): OpenedEditor {
        awaitResumedActivity(scenario, "open-$tag")
        val timing = EditorLoadTiming()
        scenario.onActivity { activity ->
            timing.transactionStartedAtMs = SystemClock.elapsedRealtime()
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.mainContainer, FragmentAddNote.newInstance(noteId), tag)
                .addToBackStack(null)
                .commit()
            timing.transactionCommittedAtMs = SystemClock.elapsedRealtime()
        }
        return OpenedEditor(awaitEditor(scenario, tag, timing), timing)
    }

    private fun readNoteState(id: Int): NoteState = when (val result = manager.readNote(id)) {
        is DbResult.Success -> result.value?.let { note ->
            NoteState("success", note.desc.length, sha256(note.desc), note.time.time)
        } ?: NoteState("missing", -1, null, null)
        is DbResult.Failure -> NoteState("failure(corruption=${result.isCorruption})", -1, null, null)
    }

    private fun readNoteContent(id: Int): String? = when (val result = manager.readNote(id)) {
        is DbResult.Success -> result.value?.desc
        is DbResult.Failure -> null
    }

    private fun fingerprint(label: String, text: String): String =
        "$label(utf16=${text.length}, codePoints=${text.codePointCount(0, text.length)}, " +
            "utf8=${text.toByteArray(Charsets.UTF_8).size}, sha256=${sha256(text)})"

    /** A bounded, escaped difference report that never emits an entire note. */
    private fun diff(leftLabel: String, left: String, rightLabel: String, right: String): String {
        if (left == right) return "$leftLabel=$rightLabel(equal)"
        var prefix = 0
        while (prefix < left.length && prefix < right.length && left[prefix] == right[prefix]) prefix++
        if (prefix > 0 && prefix < left.length && Character.isLowSurrogate(left[prefix]) && Character.isHighSurrogate(left[prefix - 1])) prefix--
        if (prefix > 0 && prefix < right.length && Character.isLowSurrogate(right[prefix]) && Character.isHighSurrogate(right[prefix - 1])) prefix--

        var suffix = 0
        while (suffix < left.length - prefix && suffix < right.length - prefix &&
            left[left.length - 1 - suffix] == right[right.length - 1 - suffix]
        ) suffix++
        val leftEnd = left.length - suffix
        val rightEnd = right.length - suffix
        val leftChanged = left.substring(prefix, leftEnd)
        val rightChanged = right.substring(prefix, rightEnd)
        val firstCodePoint = left.codePointCount(0, prefix)
        val classification = when {
            leftChanged.isEmpty() -> "inserted"
            rightChanged.isEmpty() -> "removed"
            else -> "substituted-or-reordered"
        }
        return "$leftLabel/$rightLabel(firstUtf16=$prefix, firstCodePoint=$firstCodePoint, kind=$classification, " +
            "${leftLabel}ChangedCodePoints=${leftChanged.codePointCount(0, leftChanged.length)}, " +
            "${rightLabel}ChangedCodePoints=${rightChanged.codePointCount(0, rightChanged.length)}, " +
            "$leftLabel=${escapedCodePoints(left, prefix)}, $rightLabel=${escapedCodePoints(right, prefix)})"
    }

    private fun escapedCodePoints(text: String, startUtf16: Int, limit: Int = 8): String {
        var offset = startUtf16.coerceIn(0, text.length)
        if (offset > 0 && offset < text.length && Character.isLowSurrogate(text[offset]) && Character.isHighSurrogate(text[offset - 1])) offset--
        val codePoints = ArrayList<String>(limit)
        repeat(limit) {
            if (offset >= text.length) return@repeat
            val codePoint = text.codePointAt(offset)
            codePoints += "U+%04X".format(codePoint)
            offset += Character.charCount(codePoint)
        }
        return codePoints.joinToString(prefix = "[", postfix = "]")
    }

    /** Returns a line boundary, which is necessarily outside a surrogate pair and any ZWJ cluster. */
    private fun lineBoundaryAtOrBefore(text: String, preferredUtf16Offset: Int): Int {
        val newline = text.lastIndexOf('\n', preferredUtf16Offset.coerceIn(0, text.length))
        return if (newline >= 0) newline + 1 else 0
    }

    private fun assertWellFormedUtf16(label: String, text: String) {
        var index = 0
        while (index < text.length) {
            val unit = text[index]
            when {
                Character.isHighSurrogate(unit) -> {
                    if (index + 1 >= text.length || !Character.isLowSurrogate(text[index + 1])) {
                        throw AssertionError("$label has unpaired high surrogate at utf16=$index ${escapedCodeUnits(text, index)}")
                    }
                    index += 2
                }
                Character.isLowSurrogate(unit) -> {
                    throw AssertionError("$label has unpaired low surrogate at utf16=$index ${escapedCodeUnits(text, index)}")
                }
                else -> index++
            }
        }
    }

    private fun escapedCodeUnits(text: String, startUtf16: Int, radius: Int = 4): String {
        val start = (startUtf16 - radius).coerceAtLeast(0)
        val end = (startUtf16 + radius + 1).coerceAtMost(text.length)
        return (start until end).joinToString(prefix = "[", postfix = "]") { index ->
            "U+%04X".format(text[index].code)
        }
    }

    private fun draftState(key: String): String = when (val result = drafts.read(key)) {
        is DraftReadResult.Available -> "available(revision=${result.draft.revision}, chars=${result.draft.content.length}, sha256=${sha256(result.draft.content)})"
        DraftReadResult.Missing -> "missing"
        DraftReadResult.Unreadable -> "unreadable"
    }

    private fun editorUiState(scenario: ActivityScenario<MainActivity>, tag: String): String {
        var state = "activity-unavailable"
        scenario.onActivity { activity ->
            val fragment = activity.supportFragmentManager.findFragmentByTag(tag) as? FragmentAddNote
            val view = fragment?.view
            state = "lifecycle=${activity.lifecycle.currentState} stateSaved=${activity.supportFragmentManager.isStateSaved} " +
                "fragmentAdded=${fragment?.isAdded} view=${view != null} " +
                "saveEnabled=${view?.findViewById<android.view.View>(R.id.btnSaveNote)?.isEnabled}"
        }
        return state
    }

    private fun awaitResumedActivity(scenario: ActivityScenario<MainActivity>, phase: String) {
        waitUntil("resumed activity ($phase)", condition = {
            var ready = false
            scenario.onActivity { activity ->
                ready = activity.lifecycle.currentState == Lifecycle.State.RESUMED &&
                    !activity.supportFragmentManager.isStateSaved
            }
            ready
        })
        checkpoint(2, "activityResumed phase=$phase")
    }

    private fun checkpoint(number: Int, detail: String) {
        latestCheckpoint = "$number:$detail"
        Log.i(TAG, "CHECKPOINT $latestCheckpoint")
    }

    private fun waitUntil(
        label: String,
        timeoutMs: Long = 15_000,
        condition: () -> Boolean,
        onTimeout: () -> Unit = {}
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            if (condition()) return
            SystemClock.sleep(50)
        }
        onTimeout()
        Log.e(TAG, "TIMEOUT label=$label checkpoint=$latestCheckpoint")
        assertTrue("Timed out waiting for $label", false)
    }

    private fun syntheticText(targetBytes: Int): String {
        val token = "Line Привіт مرحبا 漢字 👨🏽‍💻 • item\n"
        val tokenBytes = token.toByteArray(Charsets.UTF_8).size
        val builder = StringBuilder(targetBytes)
        repeat(targetBytes / tokenBytes) { builder.append(token) }
        repeat(targetBytes - builder.toString().toByteArray(Charsets.UTF_8).size) { builder.append('x') }
        return builder.toString()
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun usedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun mib(bytes: Long): String = "%.2f".format(bytes / (1024.0 * 1024.0))

    /** Java managed-heap usage only; excludes native, graphics, and most process memory. */
    private fun logJavaHeap(phase: String, bytes: Long) {
        Log.i(TAG, "JAVA_HEAP_MIB phase=$phase used=${mib(bytes)}")
    }

    private fun <T> with500KbMethodTrace(targetBytes: Int, filename: String, block: () -> T): T {
        if (targetBytes != 500 * 1024) return block()
        synchronized(METHOD_TRACE_LOCK) {
            check(!testMethodTracingActive) { "Test method tracing already active before $filename" }
            testMethodTracingActive = true
        }
        val traceFile = File(context.filesDir, filename)
        var started = false
        try {
            if (traceFile.exists()) check(traceFile.delete()) { "Cannot replace test-owned trace ${traceFile.absolutePath}" }
            Log.i(TAG, "METHOD_TRACE_START path=${traceFile.absolutePath} intervalUs=1000 bufferBytes=${TRACE_BUFFER_BYTES}")
            Debug.startMethodTracingSampling(traceFile.absolutePath, TRACE_BUFFER_BYTES, 1_000)
            started = true
            return block()
        } finally {
            try {
                if (started) {
                    Debug.stopMethodTracing()
                    Log.i(TAG, "METHOD_TRACE_STOP path=${traceFile.absolutePath} bytes=${traceFile.length()}")
                }
            } finally {
                synchronized(METHOD_TRACE_LOCK) {
                    testMethodTracingActive = false
                }
            }
        }
    }

    private fun <T> assertSuccess(result: DbResult<T>): T {
        assertTrue("Expected database success, got $result", result is DbResult.Success)
        return (result as DbResult.Success).value
    }

    private companion object {
        const val TAG = "EditorStress"
        const val TRACE_BUFFER_BYTES = 64 * 1024 * 1024
        val METHOD_TRACE_LOCK = Any()
        var testMethodTracingActive = false
    }

    private data class NoteState(
        val outcome: String,
        val chars: Int,
        val hash: String?,
        val timestamp: Long?
    )

    private data class OpenedEditor(
        val fragment: FragmentAddNote,
        val timing: EditorLoadTiming
    )

    private data class InitialOpen(
        val editor: FragmentAddNote,
        val opened: OpenedEditor,
        val loadMs: Long
    )

    private data class ReopenedResult(
        val content: String,
        val opened: OpenedEditor,
        val reopenMs: Long
    )

    private class EditorLoadTiming {
        var transactionStartedAtMs = 0L
        var transactionCommittedAtMs = 0L
        var attachedAtMs = 0L
        var viewCreatedAtMs = 0L
        var editorReadyAtMs = 0L

        fun summary(): String = "loadPhasesMs(commitReturn=${delta(transactionCommittedAtMs)}, " +
            "attached=${delta(attachedAtMs)}, view=${delta(viewCreatedAtMs)}, editorEnabled=${delta(editorReadyAtMs)})"

        private fun delta(timestamp: Long): Long = if (timestamp == 0L || transactionStartedAtMs == 0L) -1 else timestamp - transactionStartedAtMs
    }

    /** Test-only Choreographer sampler; missed-frame counts are estimates, not a jank verdict. */
    private class FrameProbe {
        private var active = false
        private var lastFrameNanos = 0L
        private var frames = 0
        private var maxFrameGapNanos = 0L
        private var gapsOver32Ms = 0
        private var estimatedMissedFrames = 0

        private val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                if (lastFrameNanos != 0L) {
                    val gap = frameTimeNanos - lastFrameNanos
                    maxFrameGapNanos = maxOf(maxFrameGapNanos, gap)
                    if (gap >= 32_000_000L) {
                        gapsOver32Ms++
                        estimatedMissedFrames += maxOf(0, (gap / 16_666_667L).toInt() - 1)
                    }
                }
                lastFrameNanos = frameTimeNanos
                frames++
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        fun start() {
            active = true
            Choreographer.getInstance().postFrameCallback(callback)
        }

        fun stop() {
            active = false
            Choreographer.getInstance().removeFrameCallback(callback)
        }

        fun summary(): String = "frames=$frames maxFrameGapMs=${maxFrameGapNanos / 1_000_000.0} " +
            "gapsOver32Ms=$gapsOver32Ms estimatedMissedFrames=$estimatedMissedFrames"
    }
}
