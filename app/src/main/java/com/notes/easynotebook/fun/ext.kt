package com.notes.easynotebook.`fun`

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.notes.easynotebook.R
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

fun AppCompatActivity.replaceFragment(
    fragment: Fragment,
    addStack: Boolean = true,
    isAnimation: Boolean = false
) {
    if (addStack) {
        if (isAnimation) {
            supportFragmentManager.beginTransaction().setCustomAnimations(
                R.anim.transition_from_left_to_right,
                R.anim.transition_from_right_to_left
            )
        } else {
            supportFragmentManager.beginTransaction()
        }
            .addToBackStack(null)
            .replace(
                R.id.mainContainer,
                fragment
            ).commit()
    } else {
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.mainContainer,
                fragment
            ).commit()
    }
}

fun View.applySystemBarsInsets() {
    val initialPaddingLeft = paddingLeft
    val initialPaddingTop = paddingTop
    val initialPaddingRight = paddingRight
    val initialPaddingBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val systemBarsInsets = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val navigationBarsInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        // This root is the sole owner of bottom insets: keep bottom controls above
        // whichever occupies more space, the system navigation area or the IME.
        view.setPadding(
            initialPaddingLeft + systemBarsInsets.left,
            initialPaddingTop + systemBarsInsets.top,
            initialPaddingRight + systemBarsInsets.right,
            initialPaddingBottom + maxOf(
                systemBarsInsets.bottom,
                navigationBarsInsets.bottom,
                imeInsets.bottom
            )
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}





