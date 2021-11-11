package com.notes.notebook.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.notes.notebook.R
import com.notes.notebook.`fun`.replaceFragment
import com.notes.notebook.fragment.FragmentAddNote
import com.notes.notebook.fragment.FragmentMainList

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        goToMainFragment()
    }

    private fun goToMainFragment() {
        replaceFragment(FragmentMainList(), false)
    }

    fun goToFragmentAddNote() {
        replaceFragment(FragmentAddNote(), true)
    }
}

