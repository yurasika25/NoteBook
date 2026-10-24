package com.notes.easynotebook.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.notes.easynotebook.R
import com.notes.easynotebook.adapter.NoteBookAdapter
import com.notes.easynotebook.base.BaseFragment
import com.notes.easynotebook.core.storage.NoteDraftStore
import com.notes.easynotebook.core.storage.RatePrefs
import com.notes.easynotebook.databinding.FrgMainListBinding
import com.notes.easynotebook.db.DbManagerNoteBook
import com.notes.easynotebook.db.DbResult
import com.notes.easynotebook.`fun`.applySystemBarsInsets
import com.notes.easynotebook.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FragmentMainList : BaseFragment() {

    private val ratePrompter by lazy {
        RatePrompter(RatePrefs(requireContext().applicationContext))
    }
    private val draftStore by lazy { NoteDraftStore(requireContext()) }

    private lateinit var dbManagerNoteBook: DbManagerNoteBook
    private lateinit var binding: FrgMainListBinding
    private var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private val noteBookAdapter = NoteBookAdapter { item ->
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.mainContainer, FragmentAddNote.newInstance(item.id))
            .addToBackStack(null)
            .commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbManagerNoteBook = DbManagerNoteBook(requireContext())
        checkForAppUpdate()
    }

    private fun checkForAppUpdate() {
        val appUpdateManager = AppUpdateManagerFactory.create(requireContext())
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                try {
                    activityResultLauncher?.let {
                        appUpdateManager.startUpdateFlowForResult(
                            appUpdateInfo,
                            it,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                        )
                    }
                } catch (e: Exception) {
                    Log.w("FragmentMainList", "Unable to start in-app update", e)
                }
            }
        }

        try {
            activityResultLauncher = registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result: ActivityResult ->
                if (result.resultCode != AppCompatActivity.RESULT_OK) {
                    Log.d("FragmentMainList", "Update flow failed")
                }
            }
        } catch (e: Exception) {
            Log.w("FragmentMainList", "Unable to register update result", e)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FrgMainListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        binding.idFlotEditFragment.setOnClickListener {
            showNewNoteOptions()
        }
        binding.tbMain.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.menu_password) {
                (requireActivity() as MainActivity).goToPasscodeLockFragment()
            }
            true
        }
        binding.mainContainer.applySystemBarsInsets()
    }

    private fun showNewNoteOptions() {
        viewLifecycleOwner.lifecycleScope.launch {
            val unfinishedDrafts = withContext(Dispatchers.IO) { draftStore.unfinishedNewNotes() }
            if (unfinishedDrafts.isEmpty()) {
                (requireActivity() as MainActivity).goToFragmentAddNote()
                return@launch
            }
            val labels = unfinishedDrafts.map { draft ->
                draft.title.ifBlank { getString(R.string.untitled_recovered_draft) }
            }.toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.unfinished_notes_title)
                .setItems(labels) { _, index ->
                    (requireActivity() as MainActivity).goToFragmentAddNote(unfinishedDrafts[index].key)
                }
                .setNegativeButton(R.string.start_new_note) { _, _ ->
                    (requireActivity() as MainActivity).goToFragmentAddNote()
                }
                .show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dbManagerNoteBook.closeDb()
    }

    override fun onResume() {
        super.onResume()
        loadNotes()
    }

    private fun loadNotes() {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { dbManagerNoteBook.readDbData() }) {
                is DbResult.Success -> {
                    noteBookAdapter.updateAdapter(result.value)
                    binding.tvEmpty.isVisible = result.value.isEmpty()
                    binding.tvEmpty.text = getString(R.string.empty)
                    ratePrompter.maybeShowRateDialog(requireActivity(), viewLifecycleOwner, result.value.size)
                }
                is DbResult.Failure -> {
                    binding.tvEmpty.isVisible = true
                    binding.tvEmpty.text = getString(R.string.notes_load_failed)
                }
            }
        }
    }

    private fun init() {
        with(binding) {
            rv.layoutManager = LinearLayoutManager(requireContext())
            getSwipeHelper().attachToRecyclerView(rv)
            rv.adapter = noteBookAdapter
        }
    }

    private fun getSwipeHelper(): ItemTouchHelper = ItemTouchHelper(object :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            showDeleteDialog(viewHolder.bindingAdapterPosition)
        }
    })

    private fun showDeleteDialog(adapterPosition: Int) {
        if (!isAdded) return
        AlertDialog.Builder(requireActivity())
            .setTitle(getString(R.string.delete))
            .setMessage(getString(R.string.are_you_sure_you_want_to_delete_this_note))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                noteBookAdapter.notifyItemChanged(adapterPosition)
            }
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                val note = noteBookAdapter.getItem(adapterPosition) ?: return@setPositiveButton
                viewLifecycleOwner.lifecycleScope.launch {
                    when (withContext(Dispatchers.IO) { dbManagerNoteBook.removeItemFromDb(note.id) }) {
                        is DbResult.Success -> {
                            noteBookAdapter.removeAt(adapterPosition)
                            binding.tvEmpty.isVisible = noteBookAdapter.itemCount == 0
                        }
                        is DbResult.Failure -> {
                            noteBookAdapter.notifyItemChanged(adapterPosition)
                            showToast(R.string.note_delete_failed)
                        }
                    }
                }
            }
            .setCancelable(false)
            .show()
    }
}
