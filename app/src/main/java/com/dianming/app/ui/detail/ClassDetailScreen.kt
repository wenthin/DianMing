package com.dianming.app.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.dianming.app.data.AttendanceSession
import com.dianming.app.data.Owner
import com.dianming.app.ui.common.CountBadge
import com.dianming.app.ui.common.SectionHeader
import com.dianming.app.ui.theme.BrandAmber
import com.dianming.app.ui.theme.BrandGreen
import com.dianming.app.ui.theme.BrandRed
import com.dianming.app.util.PhoneHelper
import com.dianming.app.util.TimeFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailScreen(
    classId: String,
    onBack: () -> Unit,
    onStartRollCall: (String) -> Unit,
    onOpenSession: (String) -> Unit
) {
    val vm: ClassDetailViewModel = viewModel(factory = ClassDetailViewModel.factory(classId))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var menuOpen by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    val info = state.classInfo

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(info?.name ?: "班级详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("编辑班级信息") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    showEdit = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除班级", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                }
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ClassInfoCard(
                    name = info?.name ?: "",
                    studentCount = state.students.size,
                    owners = info?.owners ?: emptyList(),
                    createdAt = info?.createdAt ?: 0L,
                    resume = state.resumableSession,
                    onStart = { vm.startRollCall { sessionId -> onStartRollCall(sessionId) } }
                )
            }

            item {
                SectionHeader(
                    title = "历史考勤数据",
                    trailing = "${state.sessions.size} 次"
                )
            }

            if (state.sessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "还没有考勤记录",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "点击上方「开始点名」创建第一次点名",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(state.sessions, key = { it.id }) { session ->
                    SessionCard(session = session, onClick = { onOpenSession(session.id) })
                }
            }

            if (state.sessions.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    StatsCard(
                        sessionCount = state.sessions.size,
                        present = state.presentTotal,
                        absent = state.absentTotal,
                        leave = state.leaveTotal,
                        rate = state.attendRate
                    )
                }
            }
        }
    }

    if (showEdit && info != null) {
        EditClassDialog(
            initialName = info.name,
            initialOwners = info.owners,
            onDismiss = { showEdit = false },
            onConfirm = { name, owners ->
                vm.updateClass(name, owners)
                showEdit = false
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除班级") },
            text = { Text("确定要删除「${info?.name}」吗？该班级的名单与全部考勤记录都会被清除。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteClass { onBack() }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ClassInfoCard(
    name: String,
    studentCount: Int,
    owners: List<Owner>,
    createdAt: Long,
    resume: AttendanceSession?,
    onStart: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountBadge(
                    text = "班级人数 $studentCount 人",
                    color = MaterialTheme.colorScheme.primary
                )
                if (createdAt > 0) {
                    CountBadge(
                        text = "创建于 ${TimeFormat.date(createdAt)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "负责人",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(6.dp))
            if (owners.isEmpty()) {
                Text(
                    text = "暂未设置负责人",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            } else {
                owners.forEach { owner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = owner.name.ifBlank { "未命名" },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        if (owner.phone.isNotBlank()) {
                            Spacer(Modifier.width(10.dp))
                            Row(
                                modifier = Modifier
                                    .clickable { PhoneHelper.dial(context, owner.phone) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Call,
                                    contentDescription = "拨打电话",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = owner.phone,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (resume != null) "继续点名" else "开始点名",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun StatsCard(
    sessionCount: Int,
    present: Int,
    absent: Int,
    leave: Int,
    rate: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("出勤概览", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountBadge("点名 $sessionCount 次", MaterialTheme.colorScheme.primary)
                CountBadge("出勤率 $rate%", BrandGreen)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountBadge("出勤 $present 人次", BrandGreen)
                CountBadge("缺勤 $absent 人次", BrandRed)
                CountBadge("请假 $leave 人次", BrandAmber)
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: AttendanceSession,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = session.title.ifBlank { TimeFormat.sessionTitle(session.startedAt) },
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = TimeFormat.friendly(session.startedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountBadge("出勤 ${session.present}", BrandGreen)
                CountBadge("缺勤 ${session.absent}", BrandRed)
                CountBadge("请假 ${session.leave}", BrandAmber)
            }
        }
    }
}

@Composable
private fun EditClassDialog(
    initialName: String,
    initialOwners: List<Owner>,
    onDismiss: () -> Unit,
    onConfirm: (String, List<Owner>) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var owners by remember { mutableStateOf(initialOwners) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑班级信息") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("班级名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("负责人", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(onClick = { owners = owners + Owner("", "") }) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("添加")
                    }
                }
                owners.forEachIndexed { index, owner ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = owner.name,
                            onValueChange = { v ->
                                owners = owners.mapIndexed { i, o -> if (i == index) o.copy(name = v) else o }
                            },
                            label = { Text("姓名") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = owner.phone,
                            onValueChange = { v ->
                                owners = owners.mapIndexed { i, o -> if (i == index) o.copy(phone = v) else o }
                            },
                            label = { Text("电话") },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )
                        IconButton(onClick = {
                            owners = owners.filterIndexed { i, _ -> i != index }
                        }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(name.trim(), owners.filter { it.name.isNotBlank() || it.phone.isNotBlank() })
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
