package com.notes.easynotebook.base

import android.animation.ValueAnimator
import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.view.marginBottom
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

abstract class BaseFragment : Fragment() {

    private var fabAnimator: ValueAnimator? = null
    private var isHideAnimating: Boolean? = null

    protected fun Fragment.showKeyBoard() {
        val imm =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    fun showToast(message: Int) {
        Toast.makeText(requireActivity(), message, Toast.LENGTH_SHORT).show()
    }

    fun hideKeyBoard() {
        val imm: InputMethodManager =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
        imm.hideSoftInputFromWindow(requireActivity().window.decorView.windowToken, 0)
    }

    protected fun showAndHideFloat(rv: RecyclerView, btn: View) {
        rv.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val translationY = if (dy > 0) {
                    if (isHideAnimating == true) return
                    isHideAnimating = true
                    btn.height + btn.marginBottom.toFloat()
                } else {
                    if (isHideAnimating == false) return
                    isHideAnimating = false
                    0f
                }

                fabAnimator?.cancel()
                fabAnimator = ValueAnimator.ofFloat(
                    btn.translationY, translationY
                ).apply {
                    duration = 250L

                    addUpdateListener {
                        btn.translationY = it.animatedValue as Float
                    }
                    start()
                }
            }
        })
    }
}
