package io.github.lookie14.runcash.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lookie14.runcash.ui.theme.GrandmaTheme
import io.github.lookie14.runcash.ui.theme.RunCashColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DateFormat = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)
private val NumberFormatter = NumberFormat.getNumberInstance(Locale.KOREA)

private fun formatNumber(value: Number): String = NumberFormatter.format(value)

@Composable
fun HomeRoute(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    val state by viewModel.uiState.collectAsState()
    HomeScreen(
        state = state,
        onAddTestSteps = if (viewModel.showTestControls) {
            { viewModel.addTestSteps() }
        } else {
            null
        },
    )
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onAddTestSteps: (() -> Unit)?,
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
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                text = state.date.format(DateFormat),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = RunCashColors.Ink,
            )

            StepRing(state)

            Text(
                text = if (state.goalReached) {
                    "오늘 목표를 채우셨어요!"
                } else {
                    "목표까지 ${formatNumber(state.stepsLeft)}걸음"
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = if (state.goalReached) RunCashColors.Forest else RunCashColors.Ink,
            )

            state.upcoming?.let { next ->
                Text(
                    text = "내일부터 목표가 ${formatNumber(next.goal)}걸음으로 바뀌어요\n(하루 최대 ${formatNumber(next.maxWon)}원)",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    color = RunCashColors.Muted,
                )
            }

            AllowanceCard(state)

            if (onAddTestSteps != null) {
                OutlinedButton(
                    onClick = onAddTestSteps,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                ) {
                    Text("테스트: 500걸음 추가", fontSize = 20.sp)
                }
            }
        }
    }
}

/**
 * 링 안의 글자는 시스템 글꼴 크기를 따르지 않게 dp로 고정한다.
 * 이미 충분히 크고, 글꼴을 최대로 키운 폰에서 링 밖으로 넘치는 걸 막기 위해서다.
 */
@Composable
private fun fixedSp(size: Dp): TextUnit = with(LocalDensity.current) { size.toSp() }

@Composable
private fun StepRing(state: HomeUiState) {
    val progress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 900),
        label = "ringProgress",
    )
    val steps by animateIntAsState(
        targetValue = state.todaySteps.toInt(),
        animationSpec = tween(durationMillis = 900),
        label = "stepCount",
    )
    val ringColor = if (state.goalReached) RunCashColors.Gold else RunCashColors.Forest

    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 30.dp.toPx()
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

            drawArc(
                color = RunCashColors.Track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
            if (progress > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "오늘 걸음",
                fontSize = fixedSp(24.dp),
                color = RunCashColors.Muted,
            )
            Text(
                text = formatNumber(steps),
                fontSize = fixedSp(60.dp),
                fontWeight = FontWeight.ExtraBold,
                color = RunCashColors.Ink,
            )
            Text(
                text = "목표 ${formatNumber(state.dailyGoal)}걸음",
                fontSize = fixedSp(20.dp),
                color = RunCashColors.Muted,
            )
        }
    }
}

@Composable
private fun AllowanceCard(state: HomeUiState) {
    val monthWon by animateIntAsState(
        targetValue = state.monthWon,
        animationSpec = tween(durationMillis = 900),
        label = "monthWon",
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = RunCashColors.Passbook,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("오늘 모은 용돈", fontSize = 22.sp, color = RunCashColors.Muted)
            Text(
                text = "+${formatNumber(state.todayWon)}원",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = RunCashColors.Forest,
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = RunCashColors.Rule,
            )

            Text("이번 달 모은 용돈", fontSize = 22.sp, color = RunCashColors.Muted)
            Text(
                text = "${formatNumber(monthWon)}원",
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RunCashColors.Ink,
            )
            Text(
                text = "월말에 지급돼요",
                fontSize = 20.sp,
                color = RunCashColors.Muted,
            )

            state.lastMonth?.let { last ->
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = RunCashColors.Rule,
                )
                Text(
                    text = if (last.paid) {
                        "${last.month}월 용돈 ${formatNumber(last.won)}원을 받으셨어요"
                    } else {
                        "${last.month}월 용돈 ${formatNumber(last.won)}원은 곧 받으실 거예요"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (last.paid) RunCashColors.Forest else RunCashColors.Ink,
                )
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun HomeScreenInProgressPreview() {
    GrandmaTheme {
        HomeScreen(
            state = HomeUiState(
                date = LocalDate.of(2026, 10, 4),
                todaySteps = 3_214,
                todayPoints = 642,
                monthPoints = 12_300,
                todayWon = 642,
                monthWon = 12_300,
                isLoading = false,
            ),
            onAddTestSteps = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 1000)
@Composable
private fun HomeScreenGoalReachedPreview() {
    GrandmaTheme {
        HomeScreen(
            state = HomeUiState(
                date = LocalDate.of(2026, 10, 4),
                todaySteps = 6_480,
                todayPoints = 1_000,
                monthPoints = 12_900,
                todayWon = 1_000,
                monthWon = 12_900,
                isLoading = false,
            ),
            onAddTestSteps = null,
        )
    }
}
