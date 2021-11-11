package com.notes.notebook.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.notes.notebook.db.MyDbNameClass.COLUMN_NAME_CONTENT
import com.notes.notebook.db.MyDbNameClass.COLUMN_NAME_ID
import com.notes.notebook.db.MyDbNameClass.COLUMN_NAME_TIME
import com.notes.notebook.db.MyDbNameClass.COLUMN_NAME_TITLE
import com.notes.notebook.db.MyDbNameClass.TABLE_NAME
import java.util.*
import kotlin.collections.ArrayList

class MyDbManager(context: Context) {

    private val myDbHelper = MyDbHelper(context)
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

    fun readDbData(): ArrayList<ListItem> {
        val dataList = ArrayList<ListItem>()
        val cursor = db?.query(
            TABLE_NAME, null, null, null,
            null, null, "$COLUMN_NAME_TIME DESC"
        )

        while (cursor?.moveToNext() == true) {
            val title = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_TITLE))
            val desc =
                cursor.getString(cursor.getColumnIndex(COLUMN_NAME_CONTENT))
            val id =
                cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_ID))
            val time =
                Date(cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIME)))
            val item = ListItem(id, title, desc, time)
            dataList.add(item)
        }

        cursor?.close()
        return dataList
    }

    fun closeDb() {
        myDbHelper.close()
    }
}