package com.notes.easynotebook.base

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.fragment.app.Fragment

abstract class BaseFragment : Fragment() {

    protected fun Fragment.showKeyBoard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    fun showToast(message: Int) {
        Toast.makeText(requireActivity(), message, Toast.LENGTH_SHORT).show()
    }
    fun hideKeyBoard() {
        val imm: InputMethodManager = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE)
                as InputMethodManager
        imm.hideSoftInputFromWindow(requireActivity().window.decorView.windowToken, 0)
    }
}
