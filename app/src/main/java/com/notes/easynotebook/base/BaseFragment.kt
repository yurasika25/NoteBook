package com.notes.easynotebook.base

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
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

    protected fun showToast(message: Int) {
        Toast.makeText(requireActivity(), message, Toast.LENGTH_SHORT).show()
    }
    protected  fun showShortToast(message: String) {
        Toast.makeText(requireActivity(), message, Toast.LENGTH_SHORT).show()
    }

    protected  fun hideKeyBoard() {
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


    protected fun vibratePhone(time: Long = 160) {
        val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(time.let { VibrationEffect.createOneShot(it, VibrationEffect.DEFAULT_AMPLITUDE) })
        } else {
            vibrator.vibrate(200)
        }
    }
}
