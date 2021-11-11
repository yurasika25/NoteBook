package com.notes.notebook.db

import android.provider.BaseColumns

object MyDbNameClass : BaseColumns {
    const val TABLE_NAME = "my_table"
    const val COLUMN_NAME_ID = "id"
    const val COLUMN_NAME_TITLE = "title"
    const val COLUMN_NAME_CONTENT = "content"
    const val COLUMN_NAME_TIME = "time"

    const val DATABASE_VERSION = 2
    const val DATABASE_NAME = "NoteBook.db"

    const val CREATE_TABLE = "CREATE TABLE IF NOT EXISTS " +
            "$TABLE_NAME (" +
            "$COLUMN_NAME_ID INTEGER PRIMARY KEY, $COLUMN_NAME_TITLE TEXT, " +
            "$COLUMN_NAME_CONTENT TEXT, $COLUMN_NAME_TIME BIGINT)"
    const val SQL_DELETE_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"
}