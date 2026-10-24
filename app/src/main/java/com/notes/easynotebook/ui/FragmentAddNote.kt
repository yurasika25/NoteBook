package com.notes.easynotebook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.notes.easynotebook.R
import com.notes.easynotebook.base.BaseFragment
import com.notes.easynotebook.core.storage.NoteDraft
import com.notes.easynotebook.core.storage.NoteDraftStore
import com.notes.easynotebook.core.storage.DraftReadResult
import com.notes.easynotebook.databinding.FragmentAddNoteBinding
import com.notes.easynotebook.db.DbManagerNoteBook
import com.notes.easynotebook.db.DbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class FragmentAddNote : BaseFragment() {

    companion object {
        private const val NOTE_ID_KEY = "NOTE_ID_KEY"
        private const val DRAFT_KEY = "DRAFT_KEY"
        private const val RECOVERY_AS_NEW_KEY = "RECOVERY_AS_NEW_KEY"
        private const val DRAFT_DEBOUNCE_MS = 500L

        fun newInstance(noteId: Int? = null, recoveryDraftKey: String? = null): FragmentAddNote =
            FragmentAddNote().apply {
                arguments = Bundle().apply {
                    noteId?.let { putInt(NOTE_ID_KEY, it) }
                    putString(DRAFT_KEY, recoveryDraftKey ?: noteId?.let { "edit_$it" } ?: "new_${UUID.randomUUID()}")
                    putBoolean(RECOVERY_AS_NEW_KEY, recoveryDraftKey != null && noteId == null)
                }
            }
    }

    private val isRecoveryAsNew: Boolean get() = arguments?.getBoolean(RECOVERY_AS_NEW_KEY, false) == true
    private val noteId: Int?
        get() = if (isRecoveryAsNew) null else {
            arguments?.getInt(NOTE_ID_KEY, arguments?.getInt("I_ID_KEY", -1) ?: -1)
                ?.takeIf { it > 0 }
        }
    private val draftKey: String
        get() = requireArguments().getString(DRAFT_KEY) ?: noteId?.let { "edit_$it" } ?: "new_restored"

    private lateinit var dbManagerNoteBook: DbManagerNoteBook
    private lateinit var draftStore: NoteDraftStore
    private var _binding: FragmentAddNoteBinding? = null
    private val binding get() = _binding!!
    private var draftWriteJob: Job? = null
    private var isSaving = false
    private var isRestoringDraft = false
    private var isEditorDirty = false
    private var baseUpdatedAtMs: Long? = null
    private var hasReportedDraftWriteFailure = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Do not re-parcel large legacy text arguments after an app update.
        arguments?.remove("I_TITLE_KEY")
        arguments?.remove("I_DESK_KEY")
        dbManagerNoteBook = DbManagerNoteBook(requireContext())
        draftStore = NoteDraftStore(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.titleText.isSaveEnabled = false
        binding.descriptionText.isSaveEnabled = false
        setEditorEnabled(false)
        binding.tbAddNote.title = if (noteId == null) getString(R.string.new_note) else getString(R.string.edit_note)
        binding.titleText.doAfterTextChanged { markDraftDirty() }
        binding.descriptionText.doAfterTextChanged { markDraftDirty() }
        loadEditor()

        if (noteId == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                delay(300)
                if (isAdded) showKeyBoard()
            }
        }

        binding.btnSaveNote.setOnClickListener { saveData() }
        binding.tbAddNote.setNavigationOnClickListener {
            flushDraftAsync()
            hideKeyBoard()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun loadEditor() {
        viewLifecycleOwner.lifecycleScope.launch {
            val id = noteId
            val draftResult = withContext(Dispatchers.IO) { draftStore.read(draftKey) }
            val draft = (draftResult as? DraftReadResult.Available)?.draft
            if (draftResult is DraftReadResult.Unreadable) {
                showToast(R.string.draft_recovery_failed)
            }
            if (id == null) {
                draft?.let(::showDraft)
                setEditorEnabled(true)
                return@launch
            }

            when (val result = withContext(Dispatchers.IO) { dbManagerNoteBook.readNote(id) }) {
                is DbResult.Success -> {
                    val note = result.value
                    if (note == null) {
                        draft?.let(::showDraft) ?: showToast(R.string.note_load_failed)
                    } else {
                        baseUpdatedAtMs = note.time.time
                        if (draft?.baseUpdatedAtMs == note.time.time) {
                            showDraft(draft)
                        } else {
                            showDraft(NoteDraft("", null, null, note.title, note.desc, 0L, 0L, 0L))
                            draft?.let(::offerStaleDraftRecovery)
                        }
                    }
                }
                is DbResult.Failure -> {
                    draft?.let(::showDraft) ?: showToast(R.string.note_load_failed)
                }
            }
            setEditorEnabled(true)
        }
    }

    private fun offerStaleDraftRecovery(draft: NoteDraft) {
        if (!isAdded || parentFragmentManager.isStateSaved) return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.recovered_draft_title)
            .setMessage(R.string.recovered_draft_message)
            .setNegativeButton(R.string.keep_current_note, null)
            .setPositiveButton(R.string.open_recovery_copy) { _, _ ->
                if (!isAdded || parentFragmentManager.isStateSaved) return@setPositiveButton
                parentFragmentManager.beginTransaction()
                    .replace(R.id.mainContainer, newInstance(recoveryDraftKey = draft.key))
                    .addToBackStack(null)
                    .commit()
            }
            .show()
    }

    private fun showDraft(draft: NoteDraft) {
        isRestoringDraft = true
        binding.titleText.setText(draft.title)
        binding.descriptionText.setText(draft.content)
        isRestoringDraft = false
        isEditorDirty = false
    }

    private fun markDraftDirty() {
        if (!isRestoringDraft && !isSaving) {
            isEditorDirty = true
            queueDraftPersistence()
        }
    }

    private fun queueDraftPersistence() {
        draftWriteJob?.cancel()
        val snapshot = snapshotCurrentDraft()
        draftWriteJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            persistDraft(snapshot, showFailure = true)
        }
    }

    private fun snapshotCurrentDraft(): NoteDraft = draftStore.createSnapshot(
        key = draftKey,
        noteId = noteId,
        baseUpdatedAtMs = baseUpdatedAtMs,
        title = binding.titleText.text.toString(),
        content = binding.descriptionText.text.toString()
    )

    private suspend fun persistDraft(draft: NoteDraft, showFailure: Boolean): Boolean {
        val didPersist = withContext(Dispatchers.IO) { draftStore.save(draft) }
        if (didPersist) {
            hasReportedDraftWriteFailure = false
        } else if (showFailure && !hasReportedDraftWriteFailure && isAdded) {
            hasReportedDraftWriteFailure = true
            showToast(R.string.draft_save_failed)
        }
        return didPersist
    }

    private fun flushDraftAsync() {
        if (!isEditorDirty || isSaving || _binding == null) return
        draftWriteJob?.cancel()
        val snapshot = snapshotCurrentDraft()
        lifecycleScope.launch {
            persistDraft(snapshot, showFailure = false)
        }
    }

    private fun saveData() {
        if (isSaving) return
        val title = binding.titleText.text.toString().trim()
        val content = binding.descriptionText.text.toString()
        if (title.isEmpty() || content.trim().isEmpty()) {
            showToast(R.string.f_toast)
            return
        }

        isSaving = true
        setEditorEnabled(false)
        draftWriteJob?.cancel()
        val snapshot = snapshotCurrentDraft()
        viewLifecycleOwner.lifecycleScope.launch {
            val saveResult = withContext(NonCancellable + Dispatchers.IO) {
                if (!draftStore.save(snapshot)) return@withContext SaveResult.DraftWriteFailed
                val databaseResult = if (noteId == null) {
                    dbManagerNoteBook.insertToDb(title, content, System.currentTimeMillis())
                } else {
                    dbManagerNoteBook.updateItem(
                        title,
                        content,
                        noteId!!,
                        System.currentTimeMillis(),
                        expectedUpdatedAtMs = baseUpdatedAtMs
                    )
                }
                if (databaseResult !is DbResult.Success) return@withContext SaveResult.DatabaseWriteFailed
                SaveResult.Saved(draftStore.markCommitted(draftKey, snapshot.revision))
            }
            when (saveResult) {
                is SaveResult.Saved -> {
                    if (!isAdded) return@launch
                    showToast(if (saveResult.cleanedUp) R.string.save_t else R.string.save_with_draft_cleanup_failed)
                    hideKeyBoard()
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
                SaveResult.DraftWriteFailed -> saveFailed(R.string.draft_save_failed)
                SaveResult.DatabaseWriteFailed -> saveFailed(R.string.save_failed)
            }
        }
    }

    private fun saveFailed(message: Int) {
        isSaving = false
        setEditorEnabled(true)
        showToast(message)
    }

    private fun setEditorEnabled(enabled: Boolean) {
        binding.titleText.isEnabled = enabled
        binding.descriptionText.isEnabled = enabled
        binding.btnSaveNote.isEnabled = enabled
        if (enabled && noteId == null) binding.titleText.requestFocus()
    }

    override fun onPause() {
        flushDraftAsync()
        super.onPause()
    }

    override fun onDestroyView() {
        flushDraftAsync()
        draftWriteJob?.cancel()
        _binding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbManagerNoteBook.closeDb()
    }

    private sealed class SaveResult {
        data class Saved(val cleanedUp: Boolean) : SaveResult()
        data object DraftWriteFailed : SaveResult()
        data object DatabaseWriteFailed : SaveResult()
    }
}
