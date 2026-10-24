package com.notes.easynotebook.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DbHelperNoteBook(
    context: Context,
    databaseName: String = DbNameClassNoteBook.DATABASE_NAME
) : SQLiteOpenHelper(
    context, databaseName,
    null, DbNameClassNoteBook.DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(DbNameClassNoteBook.CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // The production schema has used the same table and columns since the
        // database was introduced.  Never drop user notes during an upgrade.
        // CREATE TABLE IF NOT EXISTS is deliberately idempotent and preserves
        // every existing row, including its original id and timestamp.
        onCreate(db)
    }
}
