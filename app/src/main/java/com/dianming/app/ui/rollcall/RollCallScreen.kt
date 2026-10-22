package com.dianming.app.ui.rollcall

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dianming.app.data.AttendanceStatus
import com.dianming.app.data.Student
import com.dianming.app.ui.common.CountBadge
import com.dianming.app.ui.common.InfoTag
import com.dianming.app.ui.theme.BrandBlue
import com.dianming.app.ui.theme.BrandGreen
import com.dianming.app.ui.theme.BrandRed
import com.dianming.app.util.PhoneHelper
import com.dianming.app.util.TtsHelper
import com.dianming.app.util.TtsStatus
import kotlinx.coroutines.launch

/** 正在飞出屏幕的卡片 */
private data class OutgoingCard(
    val student: Student,
    /** 序号（从 1 开始） */
    val index: Int,
    val status: AttendanceStatus
)

/** 状态对应的品牌色 */
private fun statusColor(status: AttendanceStatus): Color = when (status) {
    AttendanceStatus.PRESENT -> BrandGreen
    AttendanceStatus.ABSENT -> BrandRed
    AttendanceStatus.LEAVE -> BrandBlue
    AttendanceStatus.PENDING -> Color.Gray
}

/** 状态对应的大图标 */
private fun statusIcon(status: AttendanceStatus): ImageVector = when (status) {
    AttendanceStatus.PRESENT -> Icons.Rounded.Check
    AttendanceStatus.ABSENT -> Icons.Rounded.Close
    AttendanceStatus.LEAVE -> Icons.Rounded.BeachAccess
    AttendanceStatus.PENDING -> Icons.Rounded.Check
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollCallScreen(
    classId: String,
    sessionId: String,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val vm: RollCallViewModel = viewModel(factory = RollCallViewModel.factory(classId, sessionId))
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // ---------- 滑动动画状态 ----------
    /** 正在飞出的卡片（非空表示正处于动画中） */
    var outgoing by remember { mutableStateOf<OutgoingCard?>(null) }
    /** 飞出方向：出勤向右、缺勤向左、请假向上 */
    var exitDir by remember { mutableStateOf(Offset.Zero) }
    /** 动画进行中（用于锁住按钮，防止误触连点） */
    var animating by remember { mutableStateOf(false) }
    /** 动画对象，值 0f~1f，1f 表示静止；每次点名新建一个，保证首帧从 0 开始 */
    var anim by remember { mutableStateOf(Animatable(0f)) }

    val tts = remember { TtsHelper(context) }
    DisposableEffect(Unit) {
        onDispose { tts.release() }
    }

    /** 语音不可用时给出提示，并提供跳转到系统语音设置的入口 */
    fun notifyTtsUnavailable() {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "语音引擎不可用，朗读无法使用",
                actionLabel = "去设置",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                PhoneHelper.openTtsSettings(context)
            }
        }
    }

    /**
     * 点名：震动反馈 + 当前卡片盖上状态色滑出，下一张从反方向滑入。
     * 动画期间锁住按钮，避免连点造成多点。
     */
    fun markWithAnimation(status: AttendanceStatus) {
        if (animating) return
        val cur = state.current ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        exitDir = when (status) {
            AttendanceStatus.PRESENT -> Offset(1f, 0f)
            AttendanceStatus.ABSENT -> Offset(-1f, 0f)
            AttendanceStatus.LEAVE -> Offset(0f, -1f)
            AttendanceStatus.PENDING -> Offset.Zero
        }
        outgoing = OutgoingCard(cur, state.currentIndex + 1, status)
        animating = true
        // 新建动画对象（值 0f），这样这一帧卡片就在正确位置，不会闪一下
        val runner = Animatable(0f)
        anim = runner
        vm.mark(status)
        scope.launch {
            runner.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            outgoing = null
            animating = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.classInfo?.name ?: "点名",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Text(
                            text = if (state.finished) "已完成" else "第 ${state.currentIndex + 1} / ${state.total} 人",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { vm.undo() },
                        enabled = !animating
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "撤销上一次")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.current != null || outgoing != null) {
                RollCallBar(
                    enabled = !animating,
                    onAbsent = { markWithAnimation(AttendanceStatus.ABSENT) },
                    onLeave = { markWithAnimation(AttendanceStatus.LEAVE) },
                    onPresent = { markWithAnimation(AttendanceStatus.PRESENT) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LinearProgressIndicator(
                progress = {
                    if (state.total == 0) 0f else state.markedCount.toFloat() / state.total
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            )

            if (state.loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在加载名单…", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }

            if (state.total == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该班级还没有学生", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }

            if (state.finished && outgoing == null) {
                FinishSummary(
                    present = state.presentCount,
                    absent = state.absentCount,
                    leave = state.leaveCount,
                    total = state.total,
                    onFinish = onFinish,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    // 位移距离，保证卡片完全离开屏幕
                    val travelX = maxWidth + 48.dp
                    val travelY = maxHeight + 48.dp

                    // 1) 正在飞出的卡片：盖上状态色 + 大图标后滑出
                    outgoing?.let { out ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = anim.value
                                    translationX = exitDir.x * p * travelX.toPx()
                                    translationY = exitDir.y * p * travelY.toPx()
                                    // 后半段淡出
                                    alpha = 1f - ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)
                                }
                        ) {
                            StudentCallCard(
                                name = out.student.name,
                                index = out.index,
                                extras = out.student.extras,
                                phone = out.student.phone,
                                onSpeak = {},
                                onCall = {},
                                modifier = Modifier.fillMaxSize()
                            )
                            // 状态色盖层（快速淡入）+ 大图标
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = (anim.value / 0.3f).coerceIn(0f, 1f) * 0.94f
                                    }
                                    .background(statusColor(out.status)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = statusIcon(out.status),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(96.dp)
                                )
                            }
                        }
                    }

                    // 2) 当前卡片：从反方向滑入
                    val cur = state.current
                    if (cur != null) {
                        StudentCallCard(
                            name = cur.name,
                            index = state.currentIndex + 1,
                            extras = cur.extras,
                            phone = cur.phone,
                            onSpeak = {
                                if (tts.speak(cur.name) == TtsStatus.UNAVAILABLE) {
                                    notifyTtsUnavailable()
                                }
                            },
                            onCall = { phone -> PhoneHelper.dial(context, phone) },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val p = anim.value
                                    translationX = -exitDir.x * (1f - p) * travelX.toPx()
                                    translationY = -exitDir.y * (1f - p) * travelY.toPx()
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentCallCard(
    name: String,
    index: Int,
    extras: Map<String, String>,
    phone: String?,
    onSpeak: () -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .padding(0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$index",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = name,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { onSpeak() }
            )

            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!phone.isNullOrBlank()) {
                    InfoTag(
                        text = phone,
                        icon = Icons.Rounded.Call,
                        onClick = { onCall(phone) }
                    )
                }
                extras.entries.forEach { entry ->
                    InfoTag(text = "${entry.key}: ${entry.value}")
                }
            }
        }
    }
}

@Composable
private fun RollCallBar(
    enabled: Boolean,
    onAbsent: () -> Unit,
    onLeave: () -> Unit,
    onPresent: () -> Unit
) {
    // 与页面背景同色、不加色调，避免底部出现一条浅蓝色块，与整个界面保持统一
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // 避开系统导航栏（手势条/虚拟键），按钮不会贴到屏幕最底部
                .navigationBarsPadding()
                // 底部留白加大，让按钮离屏幕边缘更远
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 86.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusButton(
                color = BrandRed,
                icon = Icons.Rounded.Close,
                label = "缺勤",
                onClick = onAbsent,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            StatusButton(
                color = BrandBlue,
                icon = Icons.Rounded.BeachAccess,
                label = "请假",
                onClick = onLeave,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            StatusButton(
                color = BrandGreen,
                icon = Icons.Rounded.Check,
                label = "出勤",
                onClick = onPresent,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun StatusButton(
    color: Color,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    // 浅色底 + 深色图标/文字
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(82.dp),
        shape = RoundedCornerShape(18.dp),
        color = color.copy(alpha = if (enabled) 0.12f else 0.06f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) color else color.copy(alpha = 0.4f),
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) color else color.copy(alpha = 0.4f),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FinishSummary(
    present: Int,
    absent: Int,
    leave: Int,
    total: Int,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = BrandGreen,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "点名完成",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CountBadge("出勤 $present", BrandGreen)
            CountBadge("缺勤 $absent", BrandRed)
            CountBadge("请假 $leave", BrandBlue)
        }
        Text(
            text = "共 $total 人",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("完成，返回班级", fontWeight = FontWeight.SemiBold)
        }
    }
}
