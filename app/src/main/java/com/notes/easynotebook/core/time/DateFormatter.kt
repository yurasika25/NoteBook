package com.notes.easynotebook.core.time

import java.text.SimpleDateFormat
import java.util.Locale

object DateFormatter {
    val formatter = SimpleDateFormat("dd.MM.yy kk:mm", Locale.getDefault())
}