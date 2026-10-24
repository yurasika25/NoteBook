package com.notes.easynotebook.core.storage

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

data class NoteDraft(
    val key: String,
    val noteId: Int?,
    val baseUpdatedAtMs: Long?,
    val title: String,
    val content: String,
    val revision: Long,
    val createdAtMs: Long,
    val updatedAtMs: Long
)

data class NoteDraftSummary(val key: String, val title: String, val updatedAtMs: Long)

sealed class DraftReadResult {
    data class Available(val draft: NoteDraft) : DraftReadResult()
    data object Missing : DraftReadResult()
    data object Unreadable : DraftReadResult()
}

/**
 * Draft files are self-describing and authoritative. Preferences are only an
 * index/tombstone cache, so an AtomicFile that reaches disk remains recoverable
 * even when a metadata commit is interrupted.
 */
class NoteDraftStore(
    context: Context,
    private val storageNamespace: String = DEFAULT_NAMESPACE
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("${PREFERENCES_NAME}_$storageNamespace", Context.MODE_PRIVATE)
    private val draftsDirectory = File(appContext.filesDir, "${DRAFTS_DIRECTORY}_$storageNamespace")

    fun createSnapshot(key: String, noteId: Int?, baseUpdatedAtMs: Long?, title: String, content: String): NoteDraft = synchronized(lock) {
        val revision = maxOf(preferences.getLong(NEXT_REVISION, 0L), lastAllocatedRevision) + 1L
        lastAllocatedRevision = revision
        val now = System.currentTimeMillis()
        val existing = readFile(key, includeContent = false)
        NoteDraft(key, noteId, baseUpdatedAtMs, title, content, revision, existing?.createdAtMs ?: now, now)
    }

    fun save(draft: NoteDraft): Boolean = synchronized(lock) {
        val committed = preferences.getLong(committedRevisionKey(draft.key), 0L)
        val existingRevision = readFile(draft.key, includeContent = false)?.revision ?: 0L
        if (draft.revision <= committed || draft.revision <= existingRevision) return true
        if (!writeFile(draft)) return false
        // A failed index update does not lose the file: directory scanning will recover it.
        preferences.edit()
            .putLong(NEXT_REVISION, draft.revision)
            .putStringSet(DRAFT_KEYS, preferences.getStringSet(DRAFT_KEYS, emptySet()).orEmpty() + draft.key)
            .commit()
    }

    fun read(key: String): DraftReadResult = synchronized(lock) {
        val draft = readFile(key, includeContent = true) ?: return DraftReadResult.Missing
        if (draft.revision <= preferences.getLong(committedRevisionKey(key), 0L)) return DraftReadResult.Missing
        DraftReadResult.Available(draft)
    }

    fun unfinishedNewNotes(): List<NoteDraftSummary> = synchronized(lock) {
        draftFiles().mapNotNull { file ->
            val key = decodeKey(file.name) ?: return@mapNotNull null
            val draft = readFile(key, includeContent = false) ?: return@mapNotNull null
            if (draft.noteId == null && draft.revision > preferences.getLong(committedRevisionKey(key), 0L)) {
                NoteDraftSummary(key, draft.title, draft.updatedAtMs)
            } else null
        }.sortedByDescending { it.updatedAtMs }
    }

    fun markCommitted(key: String, revision: Long): Boolean = synchronized(lock) {
        val existing = readFile(key, includeContent = false)
        if (!preferences.edit().putLong(committedRevisionKey(key), maxOf(preferences.getLong(committedRevisionKey(key), 0L), revision)).commit()) {
            return false
        }
        if (existing == null || existing.revision <= revision) {
            preferences.edit().putStringSet(DRAFT_KEYS, preferences.getStringSet(DRAFT_KEYS, emptySet()).orEmpty() - key).commit()
            contentFile(key).delete()
        }
        true
    }

    private fun writeFile(draft: NoteDraft): Boolean {
        if (!draftsDirectory.exists() && !draftsDirectory.mkdirs()) return false
        return try {
            val atomic = AtomicFile(contentFile(draft.key))
            val output = atomic.startWrite()
            try {
                DataOutputStream(output).apply {
                    writeInt(FILE_MAGIC)
                    writeLong(draft.revision)
                    writeLong(draft.createdAtMs)
                    writeLong(draft.updatedAtMs)
                    writeInt(draft.noteId ?: NO_NOTE_ID)
                    writeLong(draft.baseUpdatedAtMs ?: NO_BASE_TIMESTAMP)
                    writeInt(draft.title.toByteArray(Charsets.UTF_8).size)
                    write(draft.title.toByteArray(Charsets.UTF_8))
                    write(draft.content.toByteArray(Charsets.UTF_8))
                    flush()
                }
                atomic.finishWrite(output)
                true
            } catch (error: Exception) {
                atomic.failWrite(output)
                false
            }
        } catch (error: Exception) { false }
    }

    private fun readFile(key: String, includeContent: Boolean): NoteDraft? {
        return try {
        val file = contentFile(key)
        if (!file.exists()) return null
        DataInputStream(ByteArrayInputStream(AtomicFile(file).readFully())).use { input ->
            if (input.readInt() != FILE_MAGIC) return null
            val revision = input.readLong()
            val created = input.readLong()
            val updated = input.readLong()
            val rawNoteId = input.readInt()
            val rawBase = input.readLong()
            val titleBytes = ByteArray(input.readInt())
            input.readFully(titleBytes)
            val content = if (includeContent) input.readBytes().toString(Charsets.UTF_8) else ""
            NoteDraft(key, rawNoteId.takeIf { it != NO_NOTE_ID }, rawBase.takeIf { it != NO_BASE_TIMESTAMP }, titleBytes.toString(Charsets.UTF_8), content, revision, created, updated)
        }
        } catch (error: Exception) { null }
    }

    private fun draftFiles(): List<File> = draftsDirectory.listFiles()?.filter { it.isFile && !it.name.endsWith(".bak") && !it.name.endsWith(".new") }.orEmpty()
    private fun contentFile(key: String) = File(draftsDirectory, Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING))
    private fun decodeKey(name: String): String? = try { String(Base64.decode(name, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8) } catch (error: Exception) { null }

    companion object {
        private const val PREFERENCES_NAME = "note_editor_drafts"
        private const val DRAFTS_DIRECTORY = "note_editor_drafts"
        private const val DEFAULT_NAMESPACE = "default"
        private const val DRAFT_KEYS = "draft_keys"
        private const val NEXT_REVISION = "next_revision"
        private const val FILE_MAGIC = 0x454E4452
        private const val NO_NOTE_ID = -1
        private const val NO_BASE_TIMESTAMP = Long.MIN_VALUE
        private val lock = Any()
        private var lastAllocatedRevision = 0L
        private fun committedRevisionKey(key: String) = "$key.committed_revision"
    }
}
