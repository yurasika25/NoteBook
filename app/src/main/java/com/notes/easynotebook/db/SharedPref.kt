package com.notes.easynotebook.db

import android.content.Context

object SharedPref {
    private const val PASSWORD_KEY = "PASSWORD_KEY"

    fun setPassword(context: Context, password: String? = null) {
        val editor = context.getSharedPreferences(PASSWORD_KEY, Context.MODE_PRIVATE).edit()
        editor.putString(PASSWORD_KEY, password)
        editor.apply()
    }

    fun readPassword(context: Context): String? {
        return context.getSharedPreferences(PASSWORD_KEY, Context.MODE_PRIVATE).getString(PASSWORD_KEY, null)
    }
}