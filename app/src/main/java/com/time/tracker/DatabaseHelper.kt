package com.time.tracker

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "Tracker.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {

        db.execSQL("""
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                main_category TEXT,
                sub_categories TEXT,
                start_time INTEGER,
                duration_seconds INTEGER
            )
        """)

        db.execSQL("CREATE TABLE categories (id INTEGER PRIMARY KEY AUTOINCREMENT, main_name TEXT, sub_name TEXT)")
        db.execSQL("INSERT INTO categories (main_name, sub_name) VALUES ('Cyber', 'DNEA'), ('Cyber', 'Offensive Ops')")
        db.execSQL("INSERT INTO categories (main_name, sub_name) VALUES ('Mathematics', 'Statistics'), ('Mathematics', 'Probability')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS sessions")
        db.execSQL("DROP TABLE IF EXISTS categories")
        onCreate(db)
    }

    fun insertSession(main: String, subs: String, start: Long, duration: Long) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("main_category", main)
            put("sub_categories", subs)
            put("start_time", start)
            put("duration_seconds", duration)
        }
        db.insert("sessions", null, values)
        db.close()
    }

    fun insertCategory(main: String, sub: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("main_name", main)
            put("sub_name", sub)
        }
        db.insert("categories", null, values)
        db.close()
    }
    fun getCategories(): Map<String, List<String>> {
        val categoryMap = mutableMapOf<String, MutableList<String>>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT main_name, sub_name FROM categories", null)

        if (cursor.moveToFirst()) {
            do {
                val main = cursor.getString(0)
                val sub = cursor.getString(1)
                if (!categoryMap.containsKey(main)) {
                    categoryMap[main] = mutableListOf()
                }
                categoryMap[main]?.add(sub)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return categoryMap
    }
    fun deleteCategory(main: String, sub: String) {
        val db = this.writableDatabase
        db.delete("categories", "main_name = ? AND sub_name = ?", arrayOf(main, sub))
        db.close()
    }
    fun deleteMainCategory(mainName: String) {
        val db = this.writableDatabase
        db.delete("categories", "main_name = ?", arrayOf(mainName))
    }


    fun getDatabasePath(context: Context): java.io.File {
        return context.getDatabasePath("Tracker.db")
    }

    fun getAllSessionsCursor(): android.database.Cursor {
        val db = this.readableDatabase
        return db.rawQuery("SELECT start_time, duration_seconds, main_category, sub_categories FROM sessions", null)
    }
    fun getTimeByCategory(startTime: Long): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val db = this.readableDatabase
        val cursor = db.rawQuery(
            "SELECT main_category, SUM(duration_seconds) FROM sessions WHERE start_time >= ? GROUP BY main_category",
            arrayOf(startTime.toString())
        )
        if (cursor.moveToFirst()) {
            do {
                result[cursor.getString(0)] = cursor.getLong(1)
            } while (cursor.moveToNext())
        }
        cursor.close()
        return result
    }

    fun getRawSessions(): List<SessionData> {
        val list = mutableListOf<SessionData>()
        val db = readableDatabase

        val cursor = db.rawQuery("SELECT id, start_time, duration_seconds, main_category, sub_categories FROM sessions", null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(0)
                val timestamp = cursor.getLong(1) // matches start_time
                val duration = cursor.getLong(2)  // matches duration_seconds
                val main = cursor.getString(3)    // matches main_category
                val subs = cursor.getString(4)    // matches sub_categories

                list.add(SessionData(id, timestamp, duration, main, subs))
            } while (cursor.moveToNext())
        }
        cursor.close()
        return list
    }
    fun clearAllSessions() {
        writableDatabase.delete("sessions", null, null)
        writableDatabase.execSQL("DELETE FROM sqlite_sequence WHERE name='sessions'")
    }

    fun deleteSession(id: Int) {
        writableDatabase.delete("sessions", "id = ?", arrayOf(id.toString()))
    }

    fun updateSessionDuration(id: Int, newSeconds: Long) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("duration_seconds", newSeconds)
        }
        db.update("sessions", values, "id = ?", arrayOf(id.toString()))
    }

    data class SessionData(
        val id: Int,
        val timestamp: Long,
        val duration: Long,
        val main: String,
        val subs: String
    )
}