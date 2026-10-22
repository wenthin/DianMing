package com.dianming.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dianming.app.DianMingApp
import com.dianming.app.data.AttendanceSession
import com.dianming.app.data.ClassInfo
import com.dianming.app.data.Owner
import com.dianming.app.data.Repository
import com.dianming.app.data.Student
import com.dianming.app.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 点名中断后还能"接着点"的时间窗口。
 * 距离上一次点名活动超过这个时间，就认为那次已经作废，重新开始一次新的。
 */
private const val RESUME_WINDOW_MS = 10 * 60 * 1000L

data class ClassDetailUiState(
    val loading: Boolean = true,
    val classInfo: ClassInfo? = null,
    val students: List<Student> = emptyList(),
    val sessions: List<AttendanceSession> = emptyList(),
    /** 当前时间：每 10 秒刷新一次，用来判断"10 分钟内还能不能接着点" */
    val now: Long = System.currentTimeMillis()
) {
    /** 出勤概览统计 */
    val presentTotal: Int get() = sessions.sumOf { it.present }
    val absentTotal: Int get() = sessions.sumOf { it.absent }
    val leaveTotal: Int get() = sessions.sumOf { it.leave }
    val markedTotal: Int get() = presentTotal + absentTotal + leaveTotal
    val attendRate: Int
        get() = if (markedTotal == 0) 0 else Math.round(presentTotal * 100f / markedTotal)

    /**
     * 可以接着点的最近一次点名：还没点完，并且 10 分钟内还在活动。
     * sessions 按开始时间倒序排列，所以取到的就是最近那一次。
     */
    val resumableSession: AttendanceSession?
        get() = sessions.firstOrNull {
            it.isUnfinished(students.size) && now - it.lastActiveAt <= RESUME_WINDOW_MS
        }
}

class ClassDetailViewModel(
    private val repo: Repository,
    private val classId: String
) : ViewModel() {

    private val _state = MutableStateFlow(ClassDetailUiState())
    val state: StateFlow<ClassDetailUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.tick.collect { load() }
        }
        // 每 10 秒刷新一次时间，好让"继续点名"按钮在超过 10 分钟后自动变回"开始点名"
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                _state.update { it.copy(now = System.currentTimeMillis()) }
            }
        }
    }

    private suspend fun load() {
        val info = repo.getClass(classId)
        _state.update {
            it.copy(
                loading = false,
                classInfo = info,
                students = repo.listStudents(classId),
                sessions = repo.listSessions(classId)
            )
        }
    }

    /**
     * 开始点名。
     * 10 分钟内还有一次没点完的点名，就接着那次继续，从第一个还没点到的学生开始；
     * 否则新建一次点名。
     */
    fun startRollCall(onStart: (String) -> Unit) {
        viewModelScope.launch {
            val students = repo.listStudents(classId)
            val sessions = repo.listSessions(classId)
            val now = System.currentTimeMillis()
            val resumable = sessions.firstOrNull {
                it.isUnfinished(students.size) && now - it.lastActiveAt <= RESUME_WINDOW_MS
            }
            val id = resumable?.id
                ?: repo.createSession(classId, TimeFormat.sessionTitle(now))
            onStart(id)
        }
    }

    fun updateClass(name: String, owners: List<Owner>) {
        viewModelScope.launch {
            repo.updateClass(classId, name, owners)
        }
    }

    fun deleteClass(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repo.deleteClass(classId)
            onDeleted()
        }
    }

    companion object {
        fun factory(classId: String) = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as DianMingApp
                ClassDetailViewModel(app.repository, classId)
            }
        }
    }
}
