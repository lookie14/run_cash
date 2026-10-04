package io.github.lookie14.runcash.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lookie14.runcash.ui.theme.GrandmaTheme
import io.github.lookie14.runcash.ui.theme.RunCashColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val NumberFormatter = NumberFormat.getNumberInstance(Locale.KOREA)
private val DetailDateFormat = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val WeekLabels = listOf("일", "월", "화", "수", "목", "금", "토")

private fun formatNumber(value: Number): String = NumberFormatter.format(value)

/** 달력 칸에는 좁아서 천 걸음 단위로 줄여 보여준다. 예: 5,230 -> 5.2천 */
private fun shortSteps(steps: Long): String =
    if (steps < 1_000) "$steps" else String.format(Locale.KOREA, "%.1f천", steps / 1_000.0)

@Composable
fun CalendarRoute(viewModel: CalendarViewModel = viewModel(factory = CalendarViewModel.Factory)) {
    val state by viewModel.uiState.collectAsState()
    CalendarScreen(
        state = state,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        onSelect = viewModel::select,
    )
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            MonthHeader(state.month, state.canGoNext, onPreviousMonth, onNextMonth)
            MonthSummary(state)
            CalendarGrid(state, onSelect)
            DetailCard(state)
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ArrowButton(text = "◀", enabled = true, onClick = onPrevious)
        Text(
            text = "${month.year}년 ${month.monthValue}월",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = RunCashColors.Ink,
        )
        ArrowButton(text = "▶", enabled = canGoNext, onClick = onNext)
    }
}

@Composable
private fun ArrowButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .alpha(if (enabled) 1f else 0.25f)
            .background(Color.White, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 24.sp, color = RunCashColors.Forest)
    }
}

@Composable
private fun MonthSummary(state: CalendarUiState) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = RunCashColors.Passbook,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("이 달에 모은 용돈", fontSize = 22.sp, color = RunCashColors.Muted)
            Text(
                text = "${formatNumber(state.monthWon)}원",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RunCashColors.Ink,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = RunCashColors.Rule)
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem("목표 달성", "${state.goalDays}일", Modifier.weight(1f))
                SummaryItem("총 걸음", "${formatNumber(state.totalSteps)}걸음", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 20.sp, color = RunCashColors.Muted)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Forest)
    }
}

@Composable
private fun CalendarGrid(state: CalendarUiState, onSelect: (LocalDate) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                WeekLabels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (index == 0) Color(0xFFB3261E) else RunCashColors.Muted,
                    )
                }
            }

            if (state.days.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("불러오는 중...", fontSize = 22.sp, color = RunCashColors.Muted)
                }
            } else {
                // 일요일 시작. dayOfWeek는 월=1..일=7이라 7로 나눈 나머지가 앞 빈칸 수가 된다.
                val leading = state.month.atDay(1).dayOfWeek.value % 7
                val cells: List<CalendarDay?> = List(leading) { null } + state.days
                val padded = cells + List((7 - cells.size % 7) % 7) { null }
                padded.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(modifier = Modifier.weight(1f)) {
                                if (day != null) {
                                    DayCell(
                                        day = day,
                                        selected = state.selected?.date == day.date,
                                        onClick = { onSelect(day.date) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDay, selected: Boolean, onClick: () -> Unit) {
    val background = when {
        day.goalReached -> RunCashColors.Passbook
        else -> Color.Transparent
    }
    val border = when {
        selected -> BorderStroke(3.dp, RunCashColors.Forest)
        day.isToday -> BorderStroke(2.dp, RunCashColors.Gold)
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = background,
        border = border,
        modifier = Modifier
            .padding(2.dp)
            .aspectRatio(0.72f)
            .clickable(enabled = !day.isFuture, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                text = "${day.date.dayOfMonth}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (day.isFuture) Color(0xFF9AA39D) else RunCashColors.Ink,
            )
            if (day.isFuture) {
                Box(modifier = Modifier.size(28.dp))
            } else if (day.goalReached) {
                // 목표 달성 도장
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(RunCashColors.Forest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Box(modifier = Modifier.size(28.dp))
            }
            Text(
                text = if (day.isFuture) "" else shortSteps(day.steps),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = RunCashColors.Muted,
            )
        }
    }
}

@Composable
private fun DetailCard(state: CalendarUiState) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val day = state.selected
            if (day == null) {
                Text(
                    "날짜를 눌러 보세요",
                    fontSize = 22.sp,
                    color = RunCashColors.Muted,
                )
            } else {
                Text(
                    text = day.date.format(DetailDateFormat),
                    fontSize = 22.sp,
                    color = RunCashColors.Muted,
                )
                Text(
                    text = "${formatNumber(day.steps)}걸음",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = RunCashColors.Ink,
                )
                Text(
                    text = if (day.goalReached) {
                        "목표를 채웠어요!"
                    } else {
                        "목표까지 ${formatNumber((state.dailyGoal - day.steps).coerceAtLeast(0))}걸음"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (day.goalReached) RunCashColors.Forest else RunCashColors.Ink,
                )
                Text(
                    text = "+${formatNumber(day.won)}원",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = RunCashColors.Forest,
                )
                if (day.isToday) {
                    Text(
                        text = "오늘 금액은 예상이고, 내일 아침에 확정돼요",
                        fontSize = 20.sp,
                        color = RunCashColors.Muted,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun CalendarScreenPreview() {
    val month = YearMonth.of(2026, 10)
    val today = LocalDate.of(2026, 10, 4)
    val days = (1..month.lengthOfMonth()).map { d ->
        val date = month.atDay(d)
        val future = date.isAfter(today)
        val steps = if (future) 0L else 2_000L + d * 700L
        CalendarDay(
            date = date,
            steps = steps,
            won = minOf(steps * 1_000 / 5_000, 1_000L).toInt(),
            goalReached = !future && steps >= 5_000,
            isToday = date == today,
            isFuture = future,
        )
    }
    GrandmaTheme {
        CalendarScreen(
            state = CalendarUiState(
                month = month,
                days = days,
                selected = days.first { it.date == today },
                totalSteps = days.sumOf { it.steps },
                goalDays = days.count { it.goalReached },
                monthWon = days.sumOf { it.won },
                isLoading = false,
            ),
            onPreviousMonth = {},
            onNextMonth = {},
            onSelect = {},
        )
    }
}
