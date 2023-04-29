package com.notes.easynotebook.`fun`

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.notes.easynotebook.R

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




