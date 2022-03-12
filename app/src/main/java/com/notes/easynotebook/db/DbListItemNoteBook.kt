package com.notes.easynotebook.db

import java.util.*

data class DbListItemNoteBook(
    val id: Int,
    val title: String,
    val desc: String,
    val time: Date,
)
