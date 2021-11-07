package com.notes.notebook.db

import java.util.*

data class ListItem(
    val id: Int,
    val title: String,
    val desc: String,
    val time: Date,
)