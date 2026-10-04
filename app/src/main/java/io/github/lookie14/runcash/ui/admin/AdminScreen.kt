package io.github.lookie14.runcash.ui.admin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lookie14.runcash.data.AdminMember
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.domain.ActivityStats
import io.github.lookie14.runcash.domain.MonthSummary
import io.github.lookie14.runcash.domain.PointRules
import io.github.lookie14.runcash.domain.RuleSchedule
import io.github.lookie14.runcash.ui.calendar.CalendarGrid
import io.github.lookie14.runcash.ui.calendar.DetailCard
import io.github.lookie14.runcash.ui.calendar.MonthHeader
import io.github.lookie14.runcash.ui.theme.GrandmaTheme
import io.github.lookie14.runcash.ui.theme.RunCashColors
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.TextStyle as DateTextStyle
import java.util.Date
import java.util.Locale

private val NumberFormatter = NumberFormat.getNumberInstance(Locale.KOREA)
private fun formatNumber(value: Number): String = NumberFormatter.format(value)
private val PaidDateFormat = SimpleDateFormat("M월 d일", Locale.KOREA)
private val RecordTimeFormat = SimpleDateFormat("M월 d일 a h:mm", Locale.KOREA)

/** 이 시간 넘게 기록이 안 올라오면 경고한다. */
private const val STALE_MILLIS = 24 * 60 * 60 * 1000L

private val Danger = Color(0xFFB3261E)
private val BarMuted = Color(0xFFB7C7BC)

private fun shortSteps(steps: Long): String =
    if (steps < 1_000) "$steps" else String.format(Locale.KOREA, "%.1f천", steps / 1_000.0)

private fun timeAgo(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "방금"
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        else -> "${minutes / (60 * 24)}일 전"
    }
}

@Composable
fun AdminRoute(
    familyId: String,
    // 가족이 바뀌면 새 뷰모델을 쓰도록 familyId를 키로 준다.
    viewModel: AdminViewModel = viewModel(key = "admin-$familyId", factory = AdminViewModel.factory(familyId)),
) {
    val state by viewModel.uiState.collectAsState()

    // 월말 정산 알림을 보내려면 안드로이드 13부터 알림 권한이 필요하다. 처음 들어왔을 때 한 번 묻는다.
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 앱으로 돌아올 때 날짜가 바뀌었으면 새로 불러온다.
    val tick by AppContainer.resumeTick.collectAsState()
    LaunchedEffect(tick) { viewModel.refreshIfDayChanged() }

    AdminScreen(
        state = state,
        onRetry = viewModel::start,
        onNewCode = viewModel::newCode,
        onMarkPaid = viewModel::markLastMonthPaid,
        onSaveRules = viewModel::saveRules,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelectDate = viewModel::selectDate,
        onCreateAdminInvite = viewModel::createAdminInvite,
        onCloseAdminInvite = viewModel::closeAdminInvite,
        onRemoveAdmin = viewModel::removeAdmin,
    )
}

@Composable
fun AdminScreen(
    state: AdminUiState,
    onRetry: () -> Unit,
    onNewCode: () -> Unit,
    onMarkPaid: () -> Unit,
    onSaveRules: (goal: Int, maxWon: Int) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onCreateAdminInvite: () -> Unit,
    onCloseAdminInvite: () -> Unit,
    onRemoveAdmin: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmPaid by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<AdminMember?>(null) }
    var editRules by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("관리자 화면", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Ink)

            if (state.errorMessage != null) {
                Text(state.errorMessage, fontSize = 16.sp, color = Danger)
                OutlinedButton(onClick = onRetry) { Text("다시 시도", fontSize = 16.sp) }
            }

            when {
                state.isLoading && state.errorMessage == null ->
                    Text("불러오는 중...", fontSize = 20.sp, color = RunCashColors.Muted)

                state.paired -> {
                    StatusCard(state)
                    SettlementCard(state, onPaidClick = { confirmPaid = true })
                    ThisMonthCard(state)
                    state.activity?.let { activity ->
                        WeekChartCard(activity, goal = state.todayRules.dailyGoal)
                        TrendCard(activity)
                    }
                    RulesCard(state, onEditClick = { editRules = true })
                    CalendarSection(state, onPreviousMonth, onNextMonth, onSelectDate)
                    AdminsCard(state, onCreateAdminInvite, onCloseAdminInvite, onRemoveClick = { removing = it })
                }

                state.code != null -> {
                    CodeCard(state.code, onNewCode)
                    RulesCard(state, onEditClick = { editRules = true })
                    AdminsCard(state, onCreateAdminInvite, onCloseAdminInvite, onRemoveClick = { removing = it })
                }
            }
        }
    }

    if (confirmPaid) {
        val won = state.lastMonthSummary?.won ?: 0
        AlertDialog(
            onDismissRequest = { confirmPaid = false },
            title = { Text("${state.lastMonth.monthValue}월 용돈을 보내셨나요?") },
            text = { Text("${formatNumber(won)}원을 보낸 것으로 기록해요. 사용자 화면에 \"받으셨어요\"로 표시돼요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPaid = false
                    onMarkPaid()
                }) { Text("보냈어요") }
            },
            dismissButton = {
                TextButton(onClick = { confirmPaid = false }) { Text("취소") }
            },
        )
    }

    removing?.let { member ->
        val isMe = member.uid == state.myUid
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(if (isMe) "관리자에서 나갈까요?" else "${member.name}님을 관리자에서 뺄까요?") },
            text = {
                Text(
                    if (isMe) {
                        "이 폰에서 더는 기록을 볼 수 없어요. 다시 보려면 다른 관리자에게 초대 숫자를 받아야 해요."
                    } else {
                        "그 폰에서는 더 이상 기록을 볼 수 없어요. 다시 추가하려면 초대 숫자를 새로 보내야 해요."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    onRemoveAdmin(member.uid)
                }) { Text(if (isMe) "나가기" else "빼기", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) { Text("취소") }
            },
        )
    }

    if (editRules) {
        RulesDialog(
            initial = state.tomorrowRules,
            onDismiss = { editRules = false },
            onSave = { goal, maxWon ->
                editRules = false
                onSaveRules(goal, maxWon)
            },
        )
    }
}

@Composable
private fun Card(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    content: @Composable () -> Unit,
) {
    Surface(shape = RoundedCornerShape(20.dp), color = color, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Ink)
}

/** 사용자 폰에서 기록이 잘 올라오고 있는지. */
@Composable
private fun StatusCard(state: AdminUiState) {
    if (!state.recentLoaded) return
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }
    val last = state.lastRecordMillis
    when {
        last == null -> Card(color = Color(0xFFEDEDED)) {
            CardTitle("아직 올라온 기록이 없어요")
            Text("사용자 폰에서 앱을 한 번 열면 기록이 올라와요.", fontSize = 15.sp, color = RunCashColors.Muted)
        }

        now - last > STALE_MILLIS -> Card(color = Color(0xFFFFE3DE)) {
            Text("기록이 멈춘 것 같아요", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Danger)
            Text(
                "마지막 기록 ${timeAgo(now - last)} (${RecordTimeFormat.format(Date(last))})",
                fontSize = 15.sp,
                color = RunCashColors.Ink,
            )
            Text(
                "걷지 않으셨을 수도 있지만, 사용자 폰에서 앱이 열리는지, 인터넷과 걸음 수 권한이 켜져 있는지 확인해 보세요.",
                fontSize = 14.sp,
                color = RunCashColors.Muted,
            )
        }

        else -> Card(color = Color(0xFFE3F1E8)) {
            Text(
                "정상 · 마지막 기록 ${timeAgo((now - last).coerceAtLeast(0))}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = RunCashColors.Forest,
            )
            Text(RecordTimeFormat.format(Date(last)), fontSize = 14.sp, color = RunCashColors.Muted)
        }
    }
}

/** 지난달 정산: 보낼 금액과 "보냈어요" 버튼, 또는 보낸 기록. 보낼 금액이 없으면 숨긴다. */
@Composable
private fun SettlementCard(state: AdminUiState, onPaidClick: () -> Unit) {
    if (!state.settlementLoaded) return
    val settlement = state.lastMonthSettlement
    val summary = state.lastMonthSummary
    val month = state.lastMonth.monthValue
    if (settlement == null && (summary == null || summary.won <= 0)) return

    Card(color = if (settlement == null) Color(0xFFFFE8E0) else Color.White) {
        if (settlement != null) {
            Text("${month}월 정산 완료", fontSize = 16.sp, color = RunCashColors.Muted)
            Text(
                "${formatNumber(settlement.amount)}원 보냄",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RunCashColors.Forest,
            )
            val who = settlement.paidBy?.let { "${it}님" } ?: "관리자"
            val whenText = settlement.paidAtMillis?.let { " · ${PaidDateFormat.format(Date(it))}" }.orEmpty()
            Text("$who 보냄$whenText", fontSize = 14.sp, color = RunCashColors.Muted)
        } else if (summary != null) {
            Text("${month}월 용돈 정산", fontSize = 16.sp, color = RunCashColors.Muted)
            Text(
                "${formatNumber(summary.won)}원",
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RunCashColors.Ink,
            )
            Text(
                "목표 달성 ${summary.goalDays}일 · ${formatNumber(summary.totalSteps)}걸음",
                fontSize = 14.sp,
                color = RunCashColors.Muted,
            )
            Text("송금한 뒤 아래 버튼을 눌러 주세요", fontSize = 14.sp, color = RunCashColors.Muted)
            Button(
                onClick = onPaidClick,
                enabled = !state.markingPaid,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
            ) { Text(if (state.markingPaid) "기록 중..." else "보냈어요", fontSize = 18.sp) }
        }
    }
}

@Composable
private fun ThisMonthCard(state: AdminUiState) {
    Card(color = RunCashColors.Passbook) {
        Text("이번 달 모인 용돈", fontSize = 16.sp, color = RunCashColors.Muted)
        Text(
            "${formatNumber(state.thisMonth.won)}원",
            fontSize = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            color = RunCashColors.Ink,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = RunCashColors.Rule)
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("오늘 걸음", formatNumber(state.todaySteps), Modifier.weight(1f))
            Stat("이번 달 걸음", formatNumber(state.thisMonth.totalSteps), Modifier.weight(1f))
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("목표 달성", "${state.thisMonth.goalDays}일", Modifier.weight(1f))
            Stat("기록된 날", "${state.thisMonth.recordedDays}일", Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 14.sp, color = RunCashColors.Muted)
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Forest)
    }
}

/** 최근 7일 막대 그래프. 초록 = 그날 목표 달성, 점선 = 오늘 목표. */
@Composable
private fun WeekChartCard(activity: ActivityStats, goal: Int) {
    Card {
        CardTitle("최근 7일")
        val bars = activity.lastSevenDays
        val maxValue = maxOf(goal * 1.25, (bars.maxOfOrNull { it.steps } ?: 0L) * 1.1).coerceAtLeast(1.0)

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
        ) {
            val slot = size.width / bars.size
            val barWidth = slot * 0.55f
            bars.forEachIndexed { index, day ->
                val height = (day.steps / maxValue * size.height).toFloat()
                val left = slot * index + (slot - barWidth) / 2
                val color = when {
                    day.steps >= goal && !day.isToday -> RunCashColors.Forest
                    day.isToday -> RunCashColors.Gold
                    else -> BarMuted
                }
                if (height > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
            }
            val goalY = size.height - (goal / maxValue * size.height).toFloat()
            drawLine(
                color = RunCashColors.Ink.copy(alpha = 0.5f),
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            bars.forEach { day ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(shortSteps(day.steps), fontSize = 12.sp, color = RunCashColors.Ink)
                    Text(
                        if (day.isToday) "오늘" else day.date.dayOfWeek.getDisplayName(DateTextStyle.SHORT, Locale.KOREAN),
                        fontSize = 13.sp,
                        fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                        color = RunCashColors.Muted,
                    )
                }
            }
        }
        Text(
            "점선: 목표 ${formatNumber(goal)}걸음 · 초록: 목표 달성 · 노랑: 오늘(진행 중)",
            fontSize = 12.sp,
            color = RunCashColors.Muted,
        )
    }
}

/** 연속 달성과 주간 비교. */
@Composable
private fun TrendCard(activity: ActivityStats) {
    Card {
        CardTitle("추세")
        Row(modifier = Modifier.fillMaxWidth()) {
            Stat("연속 목표 달성", "${activity.streakDays}일째", Modifier.weight(1f))
            Stat("최근 7일 평균", "${formatNumber(activity.recentWeekAvg)}걸음", Modifier.weight(1f))
        }
        val previous = activity.previousWeekAvg
        val recent = activity.recentWeekAvg
        val (text, color) = when {
            previous == 0L && recent == 0L -> "지난 2주 동안 기록이 없어요" to RunCashColors.Muted
            previous == 0L -> "그 전 주에는 기록이 없었어요" to RunCashColors.Muted
            else -> {
                val change = ((recent - previous) * 100 / previous).toInt()
                when {
                    change >= 5 -> "그 전 7일보다 ${change}% 늘었어요 ▲" to RunCashColors.Forest
                    change <= -5 -> "그 전 7일보다 ${-change}% 줄었어요 ▼" to Danger
                    else -> "그 전 7일과 비슷해요" to RunCashColors.Ink
                }
            }
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        Text(
            "그 전 7일 평균 ${formatNumber(previous)}걸음 · 오늘은 진행 중이라 평균에서 뺐어요",
            fontSize = 12.sp,
            color = RunCashColors.Muted,
        )
    }
}

/** 목표 걸음과 하루 최대 금액. 바꾸면 내일부터 적용된다. */
@Composable
private fun RulesCard(state: AdminUiState, onEditClick: () -> Unit) {
    Card {
        CardTitle("목표 설정")
        Text(
            "오늘: ${formatNumber(state.todayRules.dailyGoal)}걸음 · 하루 최대 ${formatNumber(state.todayRules.dailyMaxPoints)}원",
            fontSize = 16.sp,
            color = RunCashColors.Ink,
        )
        if (state.tomorrowRules != state.todayRules) {
            Text(
                "내일부터: ${formatNumber(state.tomorrowRules.dailyGoal)}걸음 · 하루 최대 ${formatNumber(state.tomorrowRules.dailyMaxPoints)}원",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = RunCashColors.Forest,
            )
        }
        Text("목표보다 적게 걸으면 걸은 만큼 비례해서 쌓여요.", fontSize = 13.sp, color = RunCashColors.Muted)
        OutlinedButton(onClick = onEditClick, enabled = !state.savingRules) {
            Text(if (state.savingRules) "저장 중..." else "목표 바꾸기", fontSize = 16.sp)
        }
    }
}

@Composable
private fun RulesDialog(
    initial: PointRules,
    onDismiss: () -> Unit,
    onSave: (goal: Int, maxWon: Int) -> Unit,
) {
    var goalText by remember { mutableStateOf(initial.dailyGoal.toString()) }
    var wonText by remember { mutableStateOf(initial.dailyMaxPoints.toString()) }
    val goal = goalText.toIntOrNull()
    val maxWon = wonText.toIntOrNull()
    val goalOk = goal != null && goal in AdminViewModel.GOAL_RANGE
    val wonOk = maxWon != null && maxWon in AdminViewModel.MAX_WON_RANGE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("목표 바꾸기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it.filter(Char::isDigit).take(6) },
                    label = { Text("하루 목표 걸음") },
                    suffix = { Text("걸음") },
                    isError = !goalOk,
                    supportingText = {
                        Text("${formatNumber(AdminViewModel.GOAL_RANGE.first)}~${formatNumber(AdminViewModel.GOAL_RANGE.last)}걸음")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = wonText,
                    onValueChange = { wonText = it.filter(Char::isDigit).take(6) },
                    label = { Text("목표 달성 시 하루 용돈") },
                    suffix = { Text("원") },
                    isError = !wonOk,
                    supportingText = {
                        Text("${formatNumber(AdminViewModel.MAX_WON_RANGE.first)}~${formatNumber(AdminViewModel.MAX_WON_RANGE.last)}원")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text("내일부터 적용돼요. 지난날 용돈은 그대로예요.", fontSize = 14.sp, color = RunCashColors.Muted)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (goal != null && maxWon != null) onSave(goal, maxWon) },
                enabled = goalOk && wonOk,
            ) { Text("저장") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun CalendarSection(
    state: AdminUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardTitle("달력")
        MonthHeader(state.calendar.month, state.calendar.canGoNext, onPreviousMonth, onNextMonth)
        Text(
            "이 달 ${formatNumber(state.calendar.monthWon)}원 · 목표 달성 ${state.calendar.goalDays}일",
            fontSize = 16.sp,
            color = RunCashColors.Muted,
        )
        CalendarGrid(state.calendar, onSelectDate)
        DetailCard(state.calendar)
    }
}

/** 관리자 목록. 누구나 다른 관리자를 추가하거나 뺄 수 있다. 마지막 한 명은 뺄 수 없다. */
@Composable
private fun AdminsCard(
    state: AdminUiState,
    onCreateInvite: () -> Unit,
    onCloseInvite: () -> Unit,
    onRemoveClick: (AdminMember) -> Unit,
) {
    Card {
        CardTitle("관리자 ${state.admins.size}명")
        state.admins.forEach { member ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        member.name + if (member.uid == state.myUid) " (나)" else "",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = RunCashColors.Ink,
                    )
                    member.joinedAtMillis?.let {
                        Text("${PaidDateFormat.format(Date(it))} 추가", fontSize = 13.sp, color = RunCashColors.Muted)
                    }
                }
                if (state.admins.size > 1) {
                    TextButton(onClick = { onRemoveClick(member) }) {
                        Text(if (member.uid == state.myUid) "나가기" else "빼기", color = Danger)
                    }
                }
            }
        }

        val invite = state.adminInviteCode
        if (invite == null) {
            OutlinedButton(onClick = onCreateInvite) { Text("관리자 추가", fontSize = 16.sp) }
        } else {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = RunCashColors.Track)
            Text(
                "추가할 폰에서 '관리자 → 참여하기'를 누르고 이 숫자를 넣어 주세요",
                fontSize = 14.sp,
                color = RunCashColors.Muted,
            )
            Text(
                invite,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 6.sp,
                color = RunCashColors.Ink,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text("10분 안에 입력해야 해요", fontSize = 13.sp, color = RunCashColors.Muted)
            TextButton(onClick = onCloseInvite) { Text("닫기") }
        }
    }
}

@Composable
private fun CodeCard(code: String, onNewCode: () -> Unit) {
    Card(color = RunCashColors.Passbook) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "사용자 폰에서 이 숫자를 입력하세요",
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                color = RunCashColors.Muted,
            )
            Text(
                text = code,
                fontSize = 52.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 8.sp,
                color = RunCashColors.Ink,
            )
            Text("10분 안에 입력해야 해요", fontSize = 16.sp, color = RunCashColors.Muted)
            Button(
                onClick = onNewCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
            ) { Text("새 숫자 만들기", fontSize = 18.sp) }
        }
    }
}

@Preview(showBackground = true, heightDp = 1800)
@Composable
private fun AdminScreenPreview() {
    val today = LocalDate.of(2026, 10, 15)
    val steps = (0L..20L).associate { back -> today.minusDays(back) to (2_000L + (back * 1_337L) % 7_000L) }
    val schedule = RuleSchedule()
    GrandmaTheme {
        AdminScreen(
            state = AdminUiState(
                isLoading = false,
                paired = true,
                today = today,
                recentLoaded = true,
                lastRecordMillis = System.currentTimeMillis() - 40 * 60_000,
                todaySteps = steps[today] ?: 0,
                thisMonth = MonthSummary.of(steps.filterKeys { it.monthValue == 10 }, schedule),
                activity = ActivityStats.of(today, steps, schedule),
                tomorrowRules = PointRules(dailyGoal = 6_000, dailyMaxPoints = 1_500),
                settlementLoaded = true,
                lastMonthSummary = MonthSummary(98_000, 12, 25, 18_400),
                admins = listOf(
                    AdminMember("me", "승현", System.currentTimeMillis()),
                    AdminMember("other", "엄마", System.currentTimeMillis()),
                ),
                myUid = "me",
            ),
            onRetry = {}, onNewCode = {}, onMarkPaid = {}, onSaveRules = { _, _ -> },
            onPreviousMonth = {}, onNextMonth = {}, onSelectDate = {},
            onCreateAdminInvite = {}, onCloseAdminInvite = {}, onRemoveAdmin = {},
        )
    }
}
