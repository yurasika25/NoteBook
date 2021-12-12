package com.notes.easynotebook.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelperNoteBook(context: Context) : SQLiteOpenHelper(
    context, DbNameClassNoteBook.DATABASE_NAME,
    null, DbNameClassNoteBook.DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase?) {

        db?.execSQL(DbNameClassNoteBook.CREATE_TABLE)
    }
    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL(DbNameClassNoteBook.SQL_DELETE_TABLE)
        onCreate(db)
    }
}