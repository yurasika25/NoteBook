package com.notes.easynotebook.utils

import java.text.SimpleDateFormat
import java.util.Locale

object DateFormatter {
    val formatter = SimpleDateFormat("dd.MM.yy kk:mm", Locale.getDefault())
}