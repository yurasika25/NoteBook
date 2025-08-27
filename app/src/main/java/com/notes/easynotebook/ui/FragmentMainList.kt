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
import com.notes.easynotebook.databinding.FrgMainListBinding
import com.notes.easynotebook.db.DbManagerNoteBook
import com.notes.easynotebook.`fun`.applySystemBarsInsets
import com.notes.easynotebook.main.MainActivity

class FragmentMainList : BaseFragment() {

    private lateinit var dbMangerNoteBook: DbManagerNoteBook
    private lateinit var binding: FrgMainListBinding

    private var activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>? = null

    private val noteBookAdapter = NoteBookAdapter { itemList ->
        val fm = requireActivity().supportFragmentManager
        val ft = fm.beginTransaction()
        ft.replace(R.id.mainContainer, FragmentAddNote.newInstance(itemList))
        ft.addToBackStack(null)
        ft.commit()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbMangerNoteBook = DbManagerNoteBook(requireContext())
        dbMangerNoteBook.openDb()
        checkForAppUpdate()
    }

    private fun checkForAppUpdate() {
        val appUpdateManager = AppUpdateManagerFactory.create(requireContext())
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo: AppUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
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
                    e.printStackTrace()
                }
            }
        }

        try {
            activityResultLauncher = registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result: ActivityResult ->
                if (result.resultCode != AppCompatActivity.RESULT_OK) {
                    Log.d("message", "Update flow failed")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
        startFragmentAddNote()
        showAndHideFloat(binding.rv, binding.idFlotEditFragment)
        binding.tbMain.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_password -> (requireActivity() as MainActivity).goToPasscodeLockFragment()

            }
            true
        }
        binding.mainContainer.applySystemBarsInsets()
    }

    private fun startFragmentAddNote() {
        binding.idFlotEditFragment.setOnClickListener {
            (requireActivity() as MainActivity).goToFragmentAddNote()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dbMangerNoteBook.closeDb()
    }

    override fun onResume() {
        super.onResume()
        fillAdapter()
    }

    private fun fillAdapter() {
        val list = dbMangerNoteBook.readDbData()
        noteBookAdapter.updateAdapter(list)
        binding.tvEmpty.isVisible = list.isEmpty()
    }

    private fun init() {
        with(binding) {
            rv.layoutManager = LinearLayoutManager(requireContext())
            val swapHelper = getSwapMg()
            swapHelper.attachToRecyclerView(rv)
            rv.adapter = noteBookAdapter
        }
    }

    private fun getSwapMg(): ItemTouchHelper {
        return ItemTouchHelper(object :
            ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                showDialog(viewHolder.adapterPosition, dbMangerNoteBook)
            }
        })
    }

    private fun showDialog(adapterPosition: Int, dbMangerNoteBook: DbManagerNoteBook) {
        val alertDialog: AlertDialog.Builder = AlertDialog.Builder(requireActivity())
        alertDialog.apply {
            setTitle(getString(R.string.delete))
            setMessage(getString(R.string.are_you_sure_you_want_to_delete_this_note))
            setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
                noteBookAdapter.notifyItemChanged(adapterPosition)
            }
            setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                noteBookAdapter.removeItem(adapterPosition, dbMangerNoteBook)
                binding.tvEmpty.isVisible = noteBookAdapter.itemCount == 0
            }
            setCancelable(false)
            create().show()
        }
    }
}