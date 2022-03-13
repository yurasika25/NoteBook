package com.notes.easynotebook.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.notes.easynotebook.R
import com.notes.easynotebook.`fun`.hideKeyBoard
import com.notes.easynotebook.`fun`.showKeyBoard
import com.notes.easynotebook.`fun`.showToast
import com.notes.easynotebook.`fun`.toEditable
import com.notes.easynotebook.databinding.FragmentAddNoteBinding
import com.notes.easynotebook.db.ConstantsIntentNoteBook.I_DESK_KEY
import com.notes.easynotebook.db.ConstantsIntentNoteBook.I_ID_KEY
import com.notes.easynotebook.db.ConstantsIntentNoteBook.I_TITLE_KEY
import com.notes.easynotebook.db.DbListItemNoteBook
import com.notes.easynotebook.db.DbManagerNoteBook
import java.util.*

class FragmentAddNote : Fragment() {

    companion object {
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

    override fun onResume() {
        super.onResume()
        if (_id == null) {
            binding.titleText.requestFocus()
            showKeyBoard()
        }
    }

    override fun onPause() {
        super.onPause()
        hideKeyBoard()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        getBundleData()
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
            showToast(R.string.save_toast)
            requireActivity().onBackPressed()
        } else
            showToast(R.string.fill_fields_toast)
    }

    private fun getBundleData() {
        val value = arguments?.getString(I_TITLE_KEY)
        binding.titleText.text = value?.toEditable()

        val valueTwo = arguments?.getString(I_DESK_KEY)
        binding.descriptionText.text = valueTwo?.toEditable()

        val idValue = arguments?.getInt(I_ID_KEY)
        idValue?.let {
            _id = idValue
        }
    }

    private fun getTime(): Long = Date().time
}