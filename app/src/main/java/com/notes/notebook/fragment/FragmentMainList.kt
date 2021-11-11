package com.notes.notebook.fragment

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notes.notebook.R
import com.notes.notebook.adapter.NoteBookAdapter
import com.notes.notebook.db.MyDbManager
import com.notes.notebook.main.MainActivity
import kotlinx.android.synthetic.main.fragment_main_list.*

class FragmentMainList : Fragment() {

    private lateinit var myDbManger: MyDbManager

    private val noteBookAdapter = NoteBookAdapter { itemList ->
        val fm = requireActivity().supportFragmentManager
        val ft = fm.beginTransaction()
        ft.replace(R.id.mainContainer, FragmentAddNote.newInstance(itemList))
        ft.addToBackStack(null)
        ft.commit()
    }

    private var fabAnimator: ValueAnimator? = null
    private var isHideAnimating: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        myDbManger = MyDbManager(requireContext())
        myDbManger.openDb()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showAndHideFloat()
        init()
        startFragmentAddNote()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fabAnimator?.cancel()
    }

    private fun showAndHideFloat() {
        rcViewId.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val translationY = if (dy > 0) {
                    if (isHideAnimating == true) return
                    isHideAnimating = true
                    id_flot_edit_fragment.height + id_flot_edit_fragment.marginBottom.toFloat()
                } else {
                    if (isHideAnimating == false) return
                    isHideAnimating = false
                    0f
                }

                fabAnimator?.cancel()
                fabAnimator = ValueAnimator.ofFloat(
                    id_flot_edit_fragment.translationY, translationY
                ).apply {
                    duration = 500L

                    addUpdateListener {
                        id_flot_edit_fragment.translationY = it.animatedValue as Float
                    }
                    start()
                }
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

    override fun onResume() {
        super.onResume()
        fillAdapter()
    }

    private fun fillAdapter() {
        val list = myDbManger.readDbData()
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
                tvEmpty.isVisible = noteBookAdapter.itemCount == 0
            }
        })
    }
}

