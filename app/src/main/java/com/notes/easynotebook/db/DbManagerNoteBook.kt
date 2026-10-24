package com.notes.easynotebook.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteDatabase
import com.notes.easynotebook.db.DbNameClassNoteBook.COLUMN_NAME_CONTENT
import com.notes.easynotebook.db.DbNameClassNoteBook.COLUMN_NAME_ID
import com.notes.easynotebook.db.DbNameClassNoteBook.COLUMN_NAME_TIME
import com.notes.easynotebook.db.DbNameClassNoteBook.COLUMN_NAME_TITLE
import com.notes.easynotebook.db.DbNameClassNoteBook.TABLE_NAME
import java.util.*
import kotlin.math.max
import kotlin.collections.ArrayList

sealed class DbResult<out T> {
    data class Success<T>(val value: T) : DbResult<T>()
    data class Failure(val isCorruption: Boolean = false) : DbResult<Nothing>()
}

class DbManagerNoteBook(
    context: Context,
    databaseName: String = DbNameClassNoteBook.DATABASE_NAME
) {

    private val myDbHelper = DbHelperNoteBook(context, databaseName)
    private var db: SQLiteDatabase? = null

    fun openDb(): DbResult<Unit> = try {
        db = myDbHelper.writableDatabase
        DbResult.Success(Unit)
    } catch (error: SQLiteException) {
        DbResult.Failure(error is SQLiteDatabaseCorruptException)
    }

    fun insertToDb(title: String, content: String, time: Long): DbResult<Int> = runDb {
        val values = ContentValues().apply {
            put(COLUMN_NAME_TITLE, title)
            put(COLUMN_NAME_CONTENT, content)
            put(COLUMN_NAME_TIME, time)
        }
        val rowId = requireDatabase().insert(TABLE_NAME, null, values)
        if (rowId == -1L) throw SQLiteException("Note insert failed")
        rowId.toInt()
    }

    fun removeItemFromDb(id: Int): DbResult<Unit> = runDb {
        val affectedRows = requireDatabase().delete(
            TABLE_NAME,
            "$COLUMN_NAME_ID = ?",
            arrayOf(id.toString())
        )
        if (affectedRows != 1) throw SQLiteException("Note delete affected $affectedRows rows")
        Unit
    }

    fun updateItem(
        title: String,
        content: String,
        id: Int,
        time: Long,
        expectedUpdatedAtMs: Long? = null
    ): DbResult<Unit> = runDb {
        val persistedTime = expectedUpdatedAtMs?.let { max(time, it + 1L) } ?: time
        val values = ContentValues().apply {
            put(COLUMN_NAME_TITLE, title)
            put(COLUMN_NAME_CONTENT, content)
            put(COLUMN_NAME_TIME, persistedTime)
        }
        val selection = if (expectedUpdatedAtMs == null) {
            "$COLUMN_NAME_ID = ?"
        } else {
            "$COLUMN_NAME_ID = ? AND $COLUMN_NAME_TIME = ?"
        }
        val selectionArgs = if (expectedUpdatedAtMs == null) {
            arrayOf(id.toString())
        } else {
            arrayOf(id.toString(), expectedUpdatedAtMs.toString())
        }
        val affectedRows = requireDatabase().update(
            TABLE_NAME,
            values,
            selection,
            selectionArgs
        )
        if (affectedRows != 1) throw SQLiteException("Note update affected $affectedRows rows")
        Unit
    }

    fun readDbData(): DbResult<List<DbListItemNoteBook>> = runDb {
        val dataList = ArrayList<DbListItemNoteBook>()
        requireDatabase().query(
            TABLE_NAME,
            arrayOf(COLUMN_NAME_ID, COLUMN_NAME_TITLE, COLUMN_NAME_TIME),
            null, null,
            null, null, "$COLUMN_NAME_TIME DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME_TITLE))
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_NAME_ID))
                val time = Date(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NAME_TIME)))
                dataList.add(DbListItemNoteBook(id, title, "", time))
            }
        }
        dataList
    }

    fun readNote(id: Int): DbResult<DbListItemNoteBook?> = runDb {
        val database = requireDatabase()
        database.query(
            TABLE_NAME,
            arrayOf(COLUMN_NAME_ID, COLUMN_NAME_TITLE, COLUMN_NAME_TIME),
            "$COLUMN_NAME_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@runDb null
            DbListItemNoteBook(
                id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_NAME_ID)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME_TITLE)),
                desc = readContentInChunks(database, id),
                time = Date(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NAME_TIME)))
            )
        }
    }

    fun closeDb() {
        myDbHelper.close()
        db = null
    }

    private fun requireDatabase(): SQLiteDatabase = db ?: myDbHelper.writableDatabase.also { db = it }

    /** Keeps individual cursor cells bounded for unusually large note bodies. */
    private fun readContentInChunks(database: SQLiteDatabase, id: Int): String {
        val totalCharacters = database.rawQuery(
            "SELECT length($COLUMN_NAME_CONTENT) FROM $TABLE_NAME WHERE $COLUMN_NAME_ID = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        if (totalCharacters == 0) return ""

        val content = StringBuilder(totalCharacters)
        var start = 1
        while (start <= totalCharacters) {
            val chunk = database.rawQuery(
                "SELECT substr($COLUMN_NAME_CONTENT, ?, ?) FROM $TABLE_NAME WHERE $COLUMN_NAME_ID = ?",
                arrayOf(start.toString(), CONTENT_CHUNK_CHARACTERS.toString(), id.toString())
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) ?: "" else "" }
            content.append(chunk)
            if (chunk.isEmpty()) break
            start += CONTENT_CHUNK_CHARACTERS
        }
        return content.toString()
    }

    private inline fun <T> runDb(block: () -> T): DbResult<T> = try {
        DbResult.Success(block())
    } catch (error: SQLiteException) {
        DbResult.Failure(error is SQLiteDatabaseCorruptException)
    }

    private companion object {
        const val CONTENT_CHUNK_CHARACTERS = 128 * 1024
    }
}
