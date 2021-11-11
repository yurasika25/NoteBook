package com.notes.notebook.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.notes.notebook.R
import com.notes.notebook.db.MyDbManager
import com.notes.notebook.db.MyIntentConstants.I_DESK_KEY
import com.notes.notebook.db.MyIntentConstants.I_ID_KEY
import com.notes.notebook.db.MyIntentConstants.I_TITLE_KEY
import kotlinx.android.synthetic.main.fragment_add_note.*
import java.util.*
import com.notes.notebook.`fun`.*
import com.notes.notebook.db.ListItem

class FragmentAddNote : Fragment() {

    companion object {
        fun newInstance(item: ListItem): FragmentAddNote {
            val fragment = FragmentAddNote()
            val bundle = Bundle()
            bundle.putString(I_TITLE_KEY, item.title)
            bundle.putString(I_DESK_KEY, item.desc)
            bundle.putInt(I_ID_KEY, item.id)
            fragment.arguments = bundle
            return fragment
        }
    }

    private var _id: Int? = null
    private lateinit var myDbManger: MyDbManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        myDbManger = MyDbManager(requireContext())
        myDbManger.openDb()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_note, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        myDbManger.closeDb()
    }

    override fun onResume() {
        super.onResume()
        if (_id == null) {
            titleText.requestFocus()
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
        floatBtnSaveFragment.setOnClickListener {
            saveData()
        }
    }

    private fun saveData() {
        val myTitle = titleText.text.toString()
        val myDesk = descriptionText.text.toString()
        if (myTitle != "" && myDesk != "")
            if (_id != null) {
                myDbManger.updateItem(myTitle, myDesk, _id!!, getTime())
            } else {
                myDbManger.insertToDb(myTitle, myDesk, getTime())
            }
        if (myTitle != "" && myDesk != "") {
            showToast("Збережено")
            requireActivity().onBackPressed()
        } else
            showToast("Заповніть усі поля")
    }

    private fun getBundleData() {
        val value = arguments?.getString(I_TITLE_KEY)
        titleText.text = value?.toEditable()

        val valueTwo = arguments?.getString(I_DESK_KEY)
        descriptionText.text = valueTwo?.toEditable()

        val idValue = arguments?.getInt(I_ID_KEY)
        idValue?.let {
            _id = idValue
        }
    }

    private fun getTime(): Long = Date().time
}