package com.dianming.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** 新建学生时用的数据 */
data class NewStudent(
    val name: String,
    val phone: String? = null,
    val extras: Map<String, String> = emptyMap()
)

/**
 * 数据仓库：所有数据库操作都在 IO 线程执行。
 * [tick] 在每次写操作后自增，界面可以监听它来刷新数据。
 */
class Repository(private val db: AppDb) {

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick.asStateFlow()

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun notifyChange() {
        _tick.value = _tick.value + 1
    }

    // ------------------------------------------------------------ 班级

    suspend fun listClasses(): List<ClassInfo> = io {
        val result = ArrayList<ClassInfo>()
        db.readableDatabase.rawQuery(
            """
            SELECT c.id, c.name, c.owners, c.created_at,
                   (SELECT COUNT(*) FROM students s WHERE s.class_id = c.id) AS cnt,
                   (SELECT MAX(started_at) FROM sessions se WHERE se.class_id = c.id) AS last_at
            FROM classes c
            ORDER BY c.created_at DESC
            """.trimIndent(),
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    ClassInfo(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        owners = TinyStore.decodeOwners(cursor.getString(2)),
                        createdAt = cursor.getLong(3),
                        studentCount = cursor.getInt(4),
                        lastSessionAt = if (cursor.isNull(5)) null else cursor.getLong(5)
                    )
                )
            }
        }
        result
    }

    suspend fun getClass(id: String): ClassInfo? = io {
        db.readableDatabase.rawQuery(
            """
            SELECT c.id, c.name, c.owners, c.created_at,
                   (SELECT COUNT(*) FROM students s WHERE s.class_id = c.id) AS cnt,
                   (SELECT MAX(started_at) FROM sessions se WHERE se.class_id = c.id) AS last_at
            FROM classes c WHERE c.id = ?
            """.trimIndent(),
            arrayOf(id)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@io null
            ClassInfo(
                id = cursor.getString(0),
                name = cursor.getString(1),
                owners = TinyStore.decodeOwners(cursor.getString(2)),
                createdAt = cursor.getLong(3),
                studentCount = cursor.getInt(4),
                lastSessionAt = if (cursor.isNull(5)) null else cursor.getLong(5)
            )
        }
    }

    suspend fun createClass(
        name: String,
        owners: List<Owner>,
        students: List<NewStudent>
    ): String = io {
        val dbh = db.writableDatabase
        val classId = UUID.randomUUID().toString()
        dbh.beginTransaction()
        try {
            dbh.insertOrThrow("classes", null, ContentValues().apply {
                put("id", classId)
                put("name", name)
                put("owners", TinyStore.encodeOwners(owners))
                put("created_at", System.currentTimeMillis())
            })
            students.forEachIndexed { index, s ->
                dbh.insertOrThrow("students", null, ContentValues().apply {
                    put("id", UUID.randomUUID().toString())
                    put("class_id", classId)
                    put("order_index", index)
                    put("name", s.name)
                    put("phone", s.phone)
                    put("extras", TinyStore.encodeMap(s.extras))
                })
            }
            dbh.setTransactionSuccessful()
        } finally {
            dbh.endTransaction()
        }
        notifyChange()
        classId
    }

    suspend fun updateClass(id: String, name: String, owners: List<Owner>) = io {
        db.writableDatabase.update(
            "classes",
            ContentValues().apply {
                put("name", name)
                put("owners", TinyStore.encodeOwners(owners))
            },
            "id = ?",
            arrayOf(id)
        )
        notifyChange()
    }

    suspend fun deleteClass(id: String) = io {
        val dbh = db.writableDatabase
        dbh.beginTransaction()
        try {
            dbh.delete("records", "session_id IN (SELECT id FROM sessions WHERE class_id = ?)", arrayOf(id))
            dbh.delete("sessions", "class_id = ?", arrayOf(id))
            dbh.delete("students", "class_id = ?", arrayOf(id))
            dbh.delete("classes", "id = ?", arrayOf(id))
            dbh.setTransactionSuccessful()
        } finally {
            dbh.endTransaction()
        }
        notifyChange()
    }

    // ------------------------------------------------------------ 学生

    suspend fun listStudents(classId: String): List<Student> = io {
        val result = ArrayList<Student>()
        db.readableDatabase.rawQuery(
            "SELECT id, class_id, order_index, name, phone, extras FROM students WHERE class_id = ? ORDER BY order_index ASC",
            arrayOf(classId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    Student(
                        id = cursor.getString(0),
                        classId = cursor.getString(1),
                        orderIndex = cursor.getInt(2),
                        name = cursor.getString(3),
                        phone = if (cursor.isNull(4)) null else cursor.getString(4),
                        extras = TinyStore.decodeMap(cursor.getString(5))
                    )
                )
            }
        }
        result
    }

    // ------------------------------------------------------------ 点名场次

    suspend fun listSessions(classId: String): List<AttendanceSession> = io {
        val result = ArrayList<AttendanceSession>()
        db.readableDatabase.rawQuery(
            """
            SELECT se.id, se.class_id, se.started_at, se.title,
                   SUM(CASE WHEN r.status = 0 THEN 1 ELSE 0 END) AS present,
                   SUM(CASE WHEN r.status = 1 THEN 1 ELSE 0 END) AS absent,
                   SUM(CASE WHEN r.status = 2 THEN 1 ELSE 0 END) AS leave,
                   SUM(CASE WHEN r.status = -1 THEN 1 ELSE 0 END) AS pending,
                   COALESCE(MAX(r.marked_at), se.started_at) AS last_active
            FROM sessions se
            LEFT JOIN records r ON r.session_id = se.id
            WHERE se.class_id = ?
            GROUP BY se.id
            ORDER BY se.started_at DESC
            """.trimIndent(),
            arrayOf(classId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    AttendanceSession(
                        id = cursor.getString(0),
                        classId = cursor.getString(1),
                        startedAt = cursor.getLong(2),
                        title = cursor.getString(3),
                        present = cursor.getInt(4),
                        absent = cursor.getInt(5),
                        leave = cursor.getInt(6),
                        pending = cursor.getInt(7),
                        lastActiveAt = cursor.getLong(8)
                    )
                )
            }
        }
        result
    }

    suspend fun createSession(classId: String, title: String, startedAt: Long = System.currentTimeMillis()): String = io {
        val id = UUID.randomUUID().toString()
        db.writableDatabase.insertOrThrow("sessions", null, ContentValues().apply {
            put("id", id)
            put("class_id", classId)
            put("started_at", startedAt)
            put("title", title)
        })
        notifyChange()
        id
    }

    suspend fun getSession(id: String): AttendanceSession? = io {
        db.readableDatabase.rawQuery(
            """
            SELECT se.id, se.class_id, se.started_at, se.title,
                   SUM(CASE WHEN r.status = 0 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.status = 1 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.status = 2 THEN 1 ELSE 0 END),
                   SUM(CASE WHEN r.status = -1 THEN 1 ELSE 0 END),
                   COALESCE(MAX(r.marked_at), se.started_at)
            FROM sessions se LEFT JOIN records r ON r.session_id = se.id
            WHERE se.id = ? GROUP BY se.id
            """.trimIndent(),
            arrayOf(id)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@io null
            AttendanceSession(
                id = cursor.getString(0),
                classId = cursor.getString(1),
                startedAt = cursor.getLong(2),
                title = cursor.getString(3),
                present = cursor.getInt(4),
                absent = cursor.getInt(5),
                leave = cursor.getInt(6),
                pending = cursor.getInt(7),
                lastActiveAt = cursor.getLong(8)
            )
        }
    }

    suspend fun renameSession(id: String, title: String) = io {
        db.writableDatabase.update(
            "sessions",
            ContentValues().apply { put("title", title) },
            "id = ?",
            arrayOf(id)
        )
        notifyChange()
    }

    suspend fun deleteSession(id: String) = io {
        val dbh = db.writableDatabase
        dbh.beginTransaction()
        try {
            dbh.delete("records", "session_id = ?", arrayOf(id))
            dbh.delete("sessions", "id = ?", arrayOf(id))
            dbh.setTransactionSuccessful()
        } finally {
            dbh.endTransaction()
        }
        notifyChange()
    }

    // ------------------------------------------------------------ 点名记录

    suspend fun listRecords(sessionId: String): List<AttendanceRecord> = io {
        val result = ArrayList<AttendanceRecord>()
        db.readableDatabase.rawQuery(
            "SELECT id, session_id, student_id, status, note, marked_at FROM records WHERE session_id = ?",
            arrayOf(sessionId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    AttendanceRecord(
                        id = cursor.getString(0),
                        sessionId = cursor.getString(1),
                        studentId = cursor.getString(2),
                        status = AttendanceStatus.fromCode(cursor.getInt(3)),
                        note = if (cursor.isNull(4)) null else cursor.getString(4),
                        markedAt = cursor.getLong(5)
                    )
                )
            }
        }
        result
    }

    /** 写入（或覆盖）某个学生的点名结果 */
    suspend fun setRecord(
        sessionId: String,
        studentId: String,
        status: AttendanceStatus,
        note: String? = null
    ) = io {
        val dbh = db.writableDatabase
        val updated = dbh.update(
            "records",
            ContentValues().apply {
                put("status", status.code)
                put("note", note)
                put("marked_at", System.currentTimeMillis())
            },
            "session_id = ? AND student_id = ?",
            arrayOf(sessionId, studentId)
        )
        if (updated == 0) {
            dbh.insertOrThrow("records", null, ContentValues().apply {
                put("id", UUID.randomUUID().toString())
                put("session_id", sessionId)
                put("student_id", studentId)
                put("status", status.code)
                put("note", note)
                put("marked_at", System.currentTimeMillis())
            })
        }
        notifyChange()
    }

    suspend fun deleteRecord(sessionId: String, studentId: String) = io {
        db.writableDatabase.delete(
            "records",
            "session_id = ? AND student_id = ?",
            arrayOf(sessionId, studentId)
        )
        notifyChange()
    }

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository = instance ?: synchronized(this) {
            instance ?: Repository(AppDb(context.applicationContext)).also { instance = it }
        }
    }
}
