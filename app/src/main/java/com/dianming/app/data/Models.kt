package com.dianming.app.data

/** 负责人（可多个） */
data class Owner(
    val name: String,
    val phone: String
)

/** 班级 */
data class ClassInfo(
    val id: String,
    val name: String,
    val owners: List<Owner>,
    val createdAt: Long,
    val studentCount: Int = 0,
    val lastSessionAt: Long? = null
)

/** 学生 */
data class Student(
    val id: String,
    val classId: String,
    val orderIndex: Int,
    val name: String,
    val phone: String? = null,
    val extras: Map<String, String> = emptyMap()
)

/** 出勤状态 */
enum class AttendanceStatus(val code: Int, val label: String) {
    PENDING(-1, "未点名"),
    PRESENT(0, "出勤"),
    ABSENT(1, "缺勤"),
    LEAVE(2, "请假");

    companion object {
        fun fromCode(code: Int): AttendanceStatus = entries.firstOrNull { it.code == code } ?: PENDING
    }
}

/** 一次点名（考勤场次） */
data class AttendanceSession(
    val id: String,
    val classId: String,
    val startedAt: Long,
    val title: String,
    val present: Int = 0,
    val absent: Int = 0,
    val leave: Int = 0,
    val pending: Int = 0,
    /** 最近一次点名的活动时间：最后一次点名的记录时间；一个人都没点时就是开始时间 */
    val lastActiveAt: Long = 0L
) {
    /** 已经点过名的学生数 */
    val markedCount: Int get() = present + absent + leave

    /** 名额为 total 人时，这次点名是否还没点完 */
    fun isUnfinished(total: Int): Boolean = markedCount < total
}

/** 单个学生的点名记录 */
data class AttendanceRecord(
    val id: String,
    val sessionId: String,
    val studentId: String,
    val status: AttendanceStatus,
    val note: String? = null,
    val markedAt: Long = 0L
)
