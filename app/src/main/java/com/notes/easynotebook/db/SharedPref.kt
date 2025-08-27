package com.notes.easynotebook.db

import android.content.Context
import androidx.core.content.edit

object SharedPref {
    private const val PASSWORD_KEY = "PASSWORD_KEY"

    fun setPassword(context: Context, password: String? = null) {
        context.getSharedPreferences(PASSWORD_KEY, Context.MODE_PRIVATE).edit {
            putString(PASSWORD_KEY, password)
        }
    }

    fun readPassword(context: Context): String? {
        return context.getSharedPreferences(PASSWORD_KEY, Context.MODE_PRIVATE).getString(PASSWORD_KEY, null)
    }
}