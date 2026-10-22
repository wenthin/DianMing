package com.dianming.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dianming.app.DianMingApp
import com.dianming.app.data.AttendanceRecord
import com.dianming.app.data.AttendanceSession
import com.dianming.app.data.AttendanceStatus
import com.dianming.app.data.ClassInfo
import com.dianming.app.data.Repository
import com.dianming.app.data.Student
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SessionDetailUiState(
    val loading: Boolean = true,
    val session: AttendanceSession? = null,
    val classInfo: ClassInfo? = null,
    val students: List<Student> = emptyList(),
    val records: Map<String, AttendanceRecord> = emptyMap(),
    val editing: Student? = null
) {
    fun statusOf(studentId: String): AttendanceStatus =
        records[studentId]?.status ?: AttendanceStatus.PENDING
}

class SessionDetailViewModel(
    private val repo: Repository,
    private val sessionId: String
) : ViewModel() {

    private val _state = MutableStateFlow(SessionDetailUiState())
    val state: StateFlow<SessionDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.tick.collect { load() }
        }
    }

    private suspend fun load() {
        val session = repo.getSession(sessionId)
        if (session == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        val students = repo.listStudents(session.classId)
        val records = repo.listRecords(sessionId).associateBy { it.studentId }
        _state.update {
            it.copy(
                loading = false,
                session = session,
                classInfo = repo.getClass(session.classId),
                students = students,
                records = records
            )
        }
    }

    fun startEdit(student: Student) = _state.update { it.copy(editing = student) }

    fun cancelEdit() = _state.update { it.copy(editing = null) }

    fun applyStatus(studentId: String, status: AttendanceStatus, note: String?) {
        viewModelScope.launch {
            repo.setRecord(sessionId, studentId, status, note)
            _state.update { it.copy(editing = null) }
        }
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteSession(sessionId)
            onDeleted()
        }
    }

    companion object {
        fun factory(sessionId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DianMingApp
                SessionDetailViewModel(app.repository, sessionId)
            }
        }
    }
}
