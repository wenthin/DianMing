package com.dianming.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDb(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS classes(
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                owners TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS students(
                id TEXT PRIMARY KEY,
                class_id TEXT NOT NULL,
                order_index INTEGER NOT NULL,
                name TEXT NOT NULL,
                phone TEXT,
                extras TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sessions(
                id TEXT PRIMARY KEY,
                class_id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                title TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS records(
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                student_id TEXT NOT NULL,
                status INTEGER NOT NULL,
                note TEXT,
                marked_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_class ON students(class_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_class ON sessions(class_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_session ON records(session_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS records")
        db.execSQL("DROP TABLE IF EXISTS sessions")
        db.execSQL("DROP TABLE IF EXISTS students")
        db.execSQL("DROP TABLE IF EXISTS classes")
        onCreate(db)
    }

    companion object {
        private const val DB_NAME = "dianming.db"
        private const val DB_VERSION = 1
    }
}
