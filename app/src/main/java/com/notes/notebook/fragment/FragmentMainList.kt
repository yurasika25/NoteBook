package com.notes.notebook.fragment

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notes.notebook.R
import com.notes.notebook.`fun`.appCompatActivity
import com.notes.notebook.adapter.NoteBookAdapter
import com.notes.notebook.db.MyDbManager
import com.notes.notebook.main.MainActivity
import kotlinx.android.synthetic.main.fragment_main_list.*

class FragmentMainList : Fragment() {

    private val myDbManger = MyDbManager(appCompatActivity)
    private val noteBookAdapter = NoteBookAdapter()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        startFragmentAddNote()
        showAndHideFloat()
    }

    private fun showAndHideFloat() {
        rcViewId.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy < 0 && !id_flot_edit_fragment.isShown) id_flot_edit_fragment.show()
                else if (dy > 0 && id_flot_edit_fragment.isShown) id_flot_edit_fragment.hide()
            }
        })
    }

    private fun startFragmentAddNote() {
        id_flot_edit_fragment.setOnClickListener {
            (requireActivity() as MainActivity).goToFragmentAddNote()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        myDbManger.closeDb()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onResume() {
        super.onResume()
        myDbManger.openDb()
        fillAdapter()
    }

    private fun fillAdapter() {
        val list = myDbManger.readDbData()
        list.reverse()
        noteBookAdapter.updateAdapter(list)
        tvEmpty.isVisible = list.isEmpty()
    }

    private fun init() {
        rcViewId.layoutManager = LinearLayoutManager(requireContext())
        val swapHelper = getSwapMg()
        swapHelper.attachToRecyclerView(rcViewId)
        rcViewId.adapter = noteBookAdapter
    }

    private fun getSwapMg(): ItemTouchHelper {
        return ItemTouchHelper(object : ItemTouchHelper.
        SimpleCallback(0, ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                noteBookAdapter.removeItem(viewHolder.adapterPosition, myDbManger)
            }
        })
    }
}

