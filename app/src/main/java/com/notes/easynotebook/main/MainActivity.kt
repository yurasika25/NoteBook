package com.notes.easynotebook.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.notes.easynotebook.R
import com.notes.easynotebook.`fun`.replaceFragment
import com.notes.easynotebook.db.SharedPref
import com.notes.easynotebook.ui.FragmentAddNote
import com.notes.easynotebook.ui.FragmentAddPassword
import com.notes.easynotebook.ui.FragmentMainList
import com.notes.easynotebook.ui.FragmentPassword

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        WindowCompat.enableEdgeToEdge(window)
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
        replaceFragment(FragmentAddNote(), addStack = true, isAnimation = true)
    }
    fun goToMainFragment() {
        replaceFragment(FragmentMainList(), false)
    }
    fun goToPasscodeLockFragment() {
        replaceFragment(FragmentAddPassword(), addStack = true, isAnimation = true)
    }
}
