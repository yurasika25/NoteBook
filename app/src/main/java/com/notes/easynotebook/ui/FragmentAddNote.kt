package com.notes.easynotebook.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.notes.easynotebook.R
import com.notes.easynotebook.base.BaseFragment
import com.notes.easynotebook.databinding.FragmentAddNoteBinding
import com.notes.easynotebook.db.DbListItemNoteBook
import com.notes.easynotebook.db.DbManagerNoteBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class FragmentAddNote : BaseFragment() {

    companion object {
        const val I_TITLE_KEY = "I_TITLE_KEY"
        const val I_DESK_KEY = "I_DESK_KEY"
        const val I_ID_KEY = "I_ID_KEY"

        fun newInstance(itemDbListItemNoteBook: DbListItemNoteBook): FragmentAddNote {
            val fragment = FragmentAddNote()
            val bundle = Bundle()
            bundle.putString(I_TITLE_KEY, itemDbListItemNoteBook.title)
            bundle.putString(I_DESK_KEY, itemDbListItemNoteBook.desc)
            bundle.putInt(I_ID_KEY, itemDbListItemNoteBook.id)
            fragment.arguments = bundle
            return fragment
        }
    }

    private var _id: Int? = null
    private lateinit var dbMangerNoteBook: DbManagerNoteBook
    private var _binding: FragmentAddNoteBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbMangerNoteBook = DbManagerNoteBook(requireContext())
        dbMangerNoteBook.openDb()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddNoteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        dbMangerNoteBook.closeDb()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getBundleData()
        if (_id == null && savedInstanceState == null) {
            lifecycleScope.launch {
                Dispatchers.IO
                delay(300)
                showKeyBoard()
            }
            binding.titleText.requestFocus()
        }

        binding.btnSaveNote.setOnClickListener {
            saveData()
        }
    }

    private fun saveData() {
        val myTitle = binding.titleText.text.toString().trim()
        val myDesk = binding.descriptionText.text.toString().trim()
        if (myTitle != "" && myDesk != "")
            if (_id != null) {
                dbMangerNoteBook.updateItem(myTitle, myDesk, _id!!, getTime())
            } else {
                dbMangerNoteBook.insertToDb(myTitle, myDesk, getTime())
            }
        if (myTitle != "" && myDesk != "") {
            showToast(R.string.save_t)
            hideKeyBoard()
            requireActivity().onBackPressedDispatcher.onBackPressed()
        } else
            showToast(R.string.f_toast)
    }

    private fun getBundleData() {
        val value = arguments?.getString(I_TITLE_KEY)
        binding.titleText.setText(value)

        val valueDesk = arguments?.getString(I_DESK_KEY)
        binding.descriptionText.setText(valueDesk)

        val idValue = arguments?.getInt(I_ID_KEY)
        idValue?.let {
            _id = idValue
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getTime(): Long = Date().time
}