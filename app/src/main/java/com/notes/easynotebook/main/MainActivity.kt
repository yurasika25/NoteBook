package com.notes.easynotebook.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.notes.easynotebook.R
import com.notes.easynotebook.`fun`.replaceFragment
import com.notes.easynotebook.fragment.FragmentAddNote
import com.notes.easynotebook.fragment.FragmentMainList
import com.notes.easynotebook.password.FragmentPassword

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
        replaceFragment(FragmentPassword(), false)
    }

    fun goToFragmentAddNote() {
        replaceFragment(FragmentAddNote(), true)
    }
    fun goToMainFragment() {
        replaceFragment(FragmentMainList(), true)
    }
}
