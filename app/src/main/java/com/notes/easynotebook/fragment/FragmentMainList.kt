package com.notes.easynotebook.fragment

import android.animation.ValueAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.notes.easynotebook.base.BaseFragment
import com.notes.easynotebook.R
import com.notes.easynotebook.adapter.NoteBookAdapter
import com.notes.easynotebook.databinding.FragmentMainListBinding
import com.notes.easynotebook.db.DbManagerNoteBook
import com.notes.easynotebook.main.MainActivity

class FragmentMainList : BaseFragment() {

    private lateinit var dbMangerNoteBook: DbManagerNoteBook
    private var _binding: FragmentMainListBinding? = null
    private val binding get() = _binding!!

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
        dbMangerNoteBook = DbManagerNoteBook(requireContext())
        dbMangerNoteBook.openDb()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showAndHideFloat()
        init()
        startFragmentAddNote()
        showPrivatePolicy()
    }

    private fun showPrivatePolicy() {
        binding.tbMain.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_privacy_policy -> openPrivatePolice()
            }
            true
        }
    }

    private fun openPrivatePolice() {
        val url = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.url)))
        startActivity(url)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fabAnimator?.cancel()
        _binding = null
    }

    private fun showAndHideFloat() {
        binding.rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val translationY = if (dy > 0) {
                    if (isHideAnimating == true) return
                    isHideAnimating = true
                    binding.idFlotEditFragment.height + binding.idFlotEditFragment.marginBottom.toFloat()
                } else {
                    if (isHideAnimating == false) return
                    isHideAnimating = false
                    0f
                }

                fabAnimator?.cancel()
                fabAnimator = ValueAnimator.ofFloat(
                    binding.idFlotEditFragment.translationY, translationY
                ).apply {
                    duration = 250L

                    addUpdateListener {
                        binding.idFlotEditFragment.translationY = it.animatedValue as Float
                    }
                    start()
                }
            }
        })
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
                noteBookAdapter.removeItem(viewHolder.adapterPosition, dbMangerNoteBook)
                binding.tvEmpty.isVisible = noteBookAdapter.itemCount == 0
            }
        })
    }
}

