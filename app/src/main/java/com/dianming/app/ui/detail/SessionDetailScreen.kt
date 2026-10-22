package com.dianming.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dianming.app.data.AttendanceStatus
import com.dianming.app.data.Student
import com.dianming.app.ui.common.CountBadge
import com.dianming.app.ui.common.StatusBadge
import com.dianming.app.ui.theme.BrandAmber
import com.dianming.app.ui.theme.BrandGreen
import com.dianming.app.ui.theme.BrandRed
import com.dianming.app.util.PhoneHelper
import com.dianming.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onContinueRollCall: (classId: String, sessionId: String) -> Unit
) {
    val vm: SessionDetailViewModel = viewModel(factory = SessionDetailViewModel.factory(sessionId))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }

    val session = state.session

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = session?.title ?: "考勤详情",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        if (session != null) {
                            Text(
                                text = TimeFormat.dateTime(session.startedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "删除这次考勤")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "本次点名统计",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CountBadge("出勤 ${session?.present ?: 0}", BrandGreen)
                            CountBadge("缺勤 ${session?.absent ?: 0}", BrandRed)
                            CountBadge("请假 ${session?.leave ?: 0}", BrandAmber)
                            if ((session?.pending ?: 0) > 0) {
                                CountBadge("未点名 ${session?.pending ?: 0}", MaterialTheme.colorScheme.outline)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "点击右侧状态可修改该学生的考勤结果",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (session != null && session.isUnfinished(state.students.size)) {
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { onContinueRollCall(session.classId, session.id) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "继续这次点名（还差 ${state.students.size - session.markedCount} 人）"
                                )
                            }
                        }
                    }
                }
            }

            items(state.students, key = { it.id }) { student ->
                StudentRecordRow(
                    student = student,
                    status = state.statusOf(student.id),
                    note = state.records[student.id]?.note,
                    onClickStatus = { vm.startEdit(student) },
                    onCall = { student.phone?.let { PhoneHelper.dial(context, it) } }
                )
            }
        }
    }

    state.editing?.let { student ->
        EditStatusDialog(
            student = student,
            currentStatus = state.statusOf(student.id),
            currentNote = state.records[student.id]?.note.orEmpty(),
            onDismiss = { vm.cancelEdit() },
            onConfirm = { status, note -> vm.applyStatus(student.id, status, note) }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这次考勤") },
            text = { Text("将删除本次点名的全部记录，且无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteSession { onBack() }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun StudentRecordRow(
    student: Student,
    status: AttendanceStatus,
    note: String?,
    onClickStatus: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val extra = student.extras.entries.joinToString(" · ") { "${it.key}: ${it.value}" }
                if (extra.isNotBlank()) {
                    Text(
                        text = extra,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                if (!note.isNullOrBlank()) {
                    Text(
                        text = "备注：$note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (!student.phone.isNullOrBlank()) {
                IconButton(onClick = onCall) {
                    Icon(
                        Icons.Rounded.Call,
                        contentDescription = "拨打电话",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
            }
            StatusBadge(
                status = status,
                modifier = Modifier.clickable { onClickStatus() }
            )
        }
    }
}

@Composable
private fun EditStatusDialog(
    student: Student,
    currentStatus: AttendanceStatus,
    currentNote: String,
    onDismiss: () -> Unit,
    onConfirm: (AttendanceStatus, String?) -> Unit
) {
    var status by remember { mutableStateOf(currentStatus) }
    var note by remember { mutableStateOf(currentNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(student.name) },
        text = {
            Column {
                AttendanceStatus.entries.forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { status = s }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = status == s, onClick = { status = s })
                        Spacer(Modifier.width(6.dp))
                        Text(s.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(status, note.ifBlank { null }) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
