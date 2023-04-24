package com.notes.easynotebook.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.notes.easynotebook.R
import com.notes.easynotebook.`fun`.replaceFragment
import com.notes.easynotebook.db.SharedPref
import com.notes.easynotebook.ui.FragmentAddNote
import com.notes.easynotebook.ui.FragmentMainList
import com.notes.easynotebook.ui.FragmentPassword

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            goToPasswordFragment()
        }
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }

    private fun goToPasswordFragment() {
        if (SharedPref.readPassword(this) == null) {
            goToMainFragment()
        } else {
            replaceFragment(FragmentPassword(), false)
        }
    }

    fun goToFragmentAddNote() {
        replaceFragment(FragmentAddNote(), true)
    }
    fun goToMainFragment() {
        replaceFragment(FragmentMainList(), false)
    }
}
