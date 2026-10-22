package com.dianming.app.ui.detail

import com.dianming.app.data.AttendanceSession
import com.dianming.app.data.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「继续点名 / 开始点名」的判定规则：
 * 最近一次**没点完**且**10 分钟内还有活动**的场次才能接着点。
 */
class ClassDetailUiStateTest {

    private val now = 1_700_000_000_000L
    private val minute = 60_000L

    private fun students(count: Int): List<Student> = List(count) {
        Student(id = "stu$it", classId = "c1", orderIndex = it, name = "同学$it")
    }

    private fun session(
        id: String,
        marked: Int,
        lastActiveAt: Long,
        startedAt: Long = lastActiveAt
    ) = AttendanceSession(
        id = id,
        classId = "c1",
        startedAt = startedAt,
        title = "点名",
        present = marked,
        lastActiveAt = lastActiveAt
    )

    private fun state(sessions: List<AttendanceSession>, studentCount: Int = 48) = ClassDetailUiState(
        loading = false,
        students = students(studentCount),
        sessions = sessions,
        now = now
    )

    @Test
    fun `十分钟内可以接着点`() {
        val s = state(listOf(session("s1", marked = 10, lastActiveAt = now - 9 * minute)))
        assertEquals("s1", s.resumableSession?.id)
    }

    @Test
    fun `超过十分钟就不再接着点`() {
        val s = state(listOf(session("s1", marked = 10, lastActiveAt = now - 11 * minute)))
        assertNull(s.resumableSession)
    }

    @Test
    fun `正好十分钟还能接着点`() {
        val s = state(listOf(session("s1", marked = 10, lastActiveAt = now - 10 * minute)))
        assertEquals("s1", s.resumableSession?.id)
    }

    @Test
    fun `已经点完的不再接着点`() {
        val s = state(listOf(session("s1", marked = 48, lastActiveAt = now - 1 * minute)))
        assertNull(s.resumableSession)
    }

    @Test
    fun `一个都没点过的时候用开始时间判断`() {
        // 刚开始 1 分钟 → 可续
        val fresh = state(
            listOf(
                session("s1", marked = 0, lastActiveAt = now - 1 * minute, startedAt = now - 1 * minute)
            )
        )
        assertEquals("s1", fresh.resumableSession?.id)

        // 开始了 11 分钟 → 不可续
        val stale = state(
            listOf(
                session("s1", marked = 0, lastActiveAt = now - 11 * minute, startedAt = now - 11 * minute)
            )
        )
        assertNull(stale.resumableSession)
    }

    @Test
    fun `多个场次时取最近的一个能续的`() {
        // sessions 按开始时间倒序：s3 最老但还没过期，s2 最新却已经超时
        val s = state(
            listOf(
                session("s2", marked = 3, lastActiveAt = now - 30 * minute),
                session("s1", marked = 20, lastActiveAt = now - 2 * minute),
                session("s3", marked = 5, lastActiveAt = now - 8 * minute)
            )
        )
        assertEquals("s1", s.resumableSession?.id)
    }

    @Test
    fun `没有学生时不产生可续场次`() {
        val s = state(listOf(session("s1", marked = 0, lastActiveAt = now)), studentCount = 0)
        assertNull(s.resumableSession)
    }
}
