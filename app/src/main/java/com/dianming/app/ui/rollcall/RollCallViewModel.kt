package com.dianming.app.ui.rollcall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dianming.app.DianMingApp
import com.dianming.app.data.AttendanceRecord
import com.dianming.app.data.AttendanceStatus
import com.dianming.app.data.ClassInfo
import com.dianming.app.data.Repository
import com.dianming.app.data.Student
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class RollCallUiState(
    val loading: Boolean = true,
    val classInfo: ClassInfo? = null,
    val students: List<Student> = emptyList(),
    val currentIndex: Int = 0,
    val records: Map<String, AttendanceRecord> = emptyMap()
) {
    val current: Student? get() = students.getOrNull(currentIndex)
    val finished: Boolean get() = currentIndex >= students.size
    val total: Int get() = students.size

    val markedCount: Int
        get() = students.count { records[it.id]?.status != null && records[it.id]?.status != AttendanceStatus.PENDING }

    val presentCount: Int get() = students.count { records[it.id]?.status == AttendanceStatus.PRESENT }
    val absentCount: Int get() = students.count { records[it.id]?.status == AttendanceStatus.ABSENT }
    val leaveCount: Int get() = students.count { records[it.id]?.status == AttendanceStatus.LEAVE }

    val statusOf: (Student) -> AttendanceStatus
        get() = { student -> records[student.id]?.status ?: AttendanceStatus.PENDING }
}

class RollCallViewModel(
    private val repo: Repository,
    private val classId: String,
    private val sessionId: String
) : ViewModel() {

    private val _state = MutableStateFlow(RollCallUiState())
    val state: StateFlow<RollCallUiState> = _state.asStateFlow()

    private val history = ArrayDeque<Pair<String, AttendanceStatus>>()

    init {
        viewModelScope.launch {
            val students = repo.listStudents(classId)
            val records = repo.listRecords(sessionId).associateBy { it.studentId }
            // 从第一个还没点名的学生开始
            val startIndex = students.indexOfFirst {
                val st = records[it.id]?.status
                st == null || st == AttendanceStatus.PENDING
            }.let { if (it < 0) students.size else it }
            // 撤销栈按点名顺序预置（含上次没点完的那部分），
            // 这样中途退出后再进来，撤销仍然能往回退
            history.addAll(
                records.values
                    .sortedBy { it.markedAt }
                    .map { it.studentId to it.status }
            )
            _state.update {
                it.copy(
                    loading = false,
                    classInfo = repo.getClass(classId),
                    students = students,
                    records = records,
                    currentIndex = startIndex
                )
            }
        }
    }

    fun mark(status: AttendanceStatus) {
        val s = _state.value
        val student = s.current ?: return
        history.addLast(student.id to status)
        val record = AttendanceRecord(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            studentId = student.id,
            status = status,
            markedAt = System.currentTimeMillis()
        )
        _state.update {
            it.copy(
                records = it.records + (student.id to record),
                currentIndex = it.currentIndex + 1
            )
        }
        viewModelScope.launch {
            repo.setRecord(sessionId, student.id, status)
        }
    }

    /** 撤销上一次点名 */
    fun undo() {
        val last = history.removeLastOrNull() ?: return
        _state.update {
            it.copy(
                records = it.records - last.first,
                currentIndex = (it.currentIndex - 1).coerceAtLeast(0)
            )
        }
        viewModelScope.launch {
            repo.deleteRecord(sessionId, last.first)
        }
    }

    fun jumpTo(index: Int) {
        _state.update { it.copy(currentIndex = index.coerceIn(0, it.students.size)) }
    }

    companion object {
        fun factory(classId: String, sessionId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DianMingApp
                RollCallViewModel(app.repository, classId, sessionId)
            }
        }
    }
}
