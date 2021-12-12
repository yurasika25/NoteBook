package com.notes.easynotebook.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.notes.easynotebook.db.DbNameClassNoteBook.COLUMN_NAME_CONTENT
import com.notes.easynotebook.db.DbNameClassNoteBook.COLUMN_NAME_ID
import com.notes.easynotebook.db.DbNameClassNoteBook.COLUMN_NAME_TIME
import com.notes.easynotebook.db.DbNameClassNoteBook.COLUMN_NAME_TITLE
import com.notes.easynotebook.db.DbNameClassNoteBook.TABLE_NAME
import java.util.*
import kotlin.collections.ArrayList

class DbManagerNoteBook(context: Context) {

    private val myDbHelper = DbHelperNoteBook(context)
    private var db: SQLiteDatabase? = null

    fun openDb() {
        db = myDbHelper.writableDatabase
    }

    fun insertToDb(title: String, content: String, time: Long) {
        val values = ContentValues().apply {
            put(COLUMN_NAME_TITLE, title)
            put(COLUMN_NAME_CONTENT, content)
            put(COLUMN_NAME_TIME, time)
        }
        db?.insert(TABLE_NAME, null, values)
    }

    fun removeItemFromDb(id: Int) {
        val selection = "$COLUMN_NAME_ID=$id"
        db?.delete(TABLE_NAME, selection, null)
    }

    fun updateItem(title: String, content: String, id: Int, time: Long) {
        val selection = "$COLUMN_NAME_ID=$id"
        val values = ContentValues().apply {
            put(COLUMN_NAME_TITLE, title)
            put(COLUMN_NAME_CONTENT, content)
            put(COLUMN_NAME_TIME, time)
        }
        db?.update(TABLE_NAME, values, selection, null)
    }

    fun readDbData(): ArrayList<DbListItemNoteBook> {
        val dataList = ArrayList<DbListItemNoteBook>()
        val cursorDb = db?.query(
            TABLE_NAME, null, null, null,
            null, null, "$COLUMN_NAME_TIME DESC"
        )

        while (cursorDb?.moveToNext() == true) {
            val title = cursorDb.getString(cursorDb.getColumnIndexOrThrow(COLUMN_NAME_TITLE))
            val desc =
                cursorDb.getString(cursorDb.getColumnIndexOrThrow(COLUMN_NAME_CONTENT))
            val id =
                cursorDb.getInt(cursorDb.getColumnIndexOrThrow(COLUMN_NAME_ID))
            val time =
                Date(cursorDb.getLong(cursorDb.getColumnIndexOrThrow(COLUMN_NAME_TIME)))
            val item = DbListItemNoteBook(id, title, desc, time)
            dataList.add(item)
        }

        cursorDb?.close()
        return dataList
    }

    fun closeDb() {
        myDbHelper.close()
    }
}