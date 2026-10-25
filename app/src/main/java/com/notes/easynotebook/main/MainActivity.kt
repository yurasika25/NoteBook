package com.notes.easynotebook.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import com.notes.easynotebook.R
import com.notes.easynotebook.`fun`.applySystemBarsInsets
import com.notes.easynotebook.`fun`.replaceFragment
import com.notes.easynotebook.db.SharedPref
import com.notes.easynotebook.ui.FragmentAddNote
import com.notes.easynotebook.ui.FragmentAddPassword
import com.notes.easynotebook.ui.FragmentMainList
import com.notes.easynotebook.ui.FragmentPassword

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15 enforces edge-to-edge for this target SDK. Opt in explicitly on
        // every supported version and let the content view handle the resulting insets.
        // Unlike enableEdgeToEdge(), this does not call the deprecated bar-color APIs.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.main_activity)
        findViewById<View>(R.id.mainContainer).applySystemBarsInsets()
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

    fun goToFragmentAddNote(recoveryDraftKey: String? = null) {
        replaceFragment(FragmentAddNote.newInstance(recoveryDraftKey = recoveryDraftKey), addStack = true, isAnimation = true)
    }
    fun goToMainFragment() {
        replaceFragment(FragmentMainList(), false)
    }
    fun goToPasscodeLockFragment() {
        replaceFragment(FragmentAddPassword(), addStack = true, isAnimation = true)
    }
}
