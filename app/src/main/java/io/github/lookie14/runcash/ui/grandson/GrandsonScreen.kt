package io.github.lookie14.runcash.ui.grandson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.util.Locale

private val NumberFormatter = NumberFormat.getNumberInstance(Locale.KOREA)
private fun formatNumber(value: Number): String = NumberFormatter.format(value)

@Composable
fun GrandsonRoute(
    onResetRole: () -> Unit,
    viewModel: GrandsonViewModel = viewModel(factory = GrandsonViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    GrandsonScreen(
        state = state,
        onNewCode = viewModel::newCode,
        onRetry = viewModel::start,
        onUnpair = viewModel::unpair,
        onResetRole = onResetRole,
    )
}

@Composable
fun GrandsonScreen(
    state: GrandsonUiState,
    onNewCode: () -> Unit,
    onRetry: () -> Unit,
    onUnpair: () -> Unit,
    onResetRole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmUnpair by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("손주 화면", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Ink)

            if (state.errorMessage != null) {
                Text(state.errorMessage, fontSize = 18.sp, color = Color(0xFFB3261E))
                OutlinedButton(onClick = onRetry) { Text("다시 시도", fontSize = 18.sp) }
            }

            when {
                state.isLoading && state.errorMessage == null ->
                    Text("불러오는 중...", fontSize = 20.sp, color = RunCashColors.Muted)

                state.paired -> PairedContent(state, onUnpairClick = { confirmUnpair = true })

                state.code != null -> CodeCard(state.code, onNewCode)
            }

            TextButton(onClick = onResetRole) {
                Text("역할 다시 고르기", fontSize = 16.sp, color = RunCashColors.Muted)
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("연결을 끊을까요?") },
            text = { Text("할머니 폰의 걸음 수 전송이 멈춰요. 이미 모인 기록은 남아 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    onUnpair()
                }) { Text("끊기") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun CodeCard(code: String, onNewCode: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = RunCashColors.Passbook, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "할머니 폰에서 이 숫자를 입력하세요",
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                color = RunCashColors.Muted,
            )
            Text(
                text = code,
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 8.sp,
                color = RunCashColors.Ink,
            )
            Text("10분 안에 입력해야 해요", fontSize = 18.sp, color = RunCashColors.Muted)
            Button(
                onClick = onNewCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
            ) { Text("새 숫자 만들기", fontSize = 20.sp) }
        }
    }
}

@Composable
private fun PairedContent(state: GrandsonUiState, onUnpairClick: () -> Unit) {
    Text("할머니 폰과 연결됐어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Forest)

    Surface(shape = RoundedCornerShape(24.dp), color = RunCashColors.Passbook, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("이번 달 모인 용돈", fontSize = 20.sp, color = RunCashColors.Muted)
            Text(
                "${formatNumber(state.monthWon)}원",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = RunCashColors.Ink,
            )
            Text("월말에 이 금액을 보내 주세요", fontSize = 18.sp, color = RunCashColors.Muted)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = RunCashColors.Rule)
            Row(modifier = Modifier.fillMaxWidth()) {
                Stat("오늘 걸음", "${formatNumber(state.todaySteps)}", Modifier.weight(1f))
                Stat("이번 달 걸음", "${formatNumber(state.monthSteps)}", Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Stat("목표 달성", "${state.goalDays}일", Modifier.weight(1f))
                Stat("기록된 날", "${state.recordedDays}일", Modifier.weight(1f))
            }
        }
    }

    OutlinedButton(onClick = onUnpairClick) { Text("연결 끊기", fontSize = 16.sp) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, fontSize = 16.sp, color = RunCashColors.Muted)
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Forest)
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun GrandsonCodePreview() {
    GrandmaTheme {
        GrandsonScreen(
            state = GrandsonUiState(isLoading = false, code = "482915"),
            onNewCode = {}, onRetry = {}, onUnpair = {}, onResetRole = {},
        )
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun GrandsonPairedPreview() {
    GrandmaTheme {
        GrandsonScreen(
            state = GrandsonUiState(
                isLoading = false, paired = true, todaySteps = 3_214, monthSteps = 41_200,
                goalDays = 3, recordedDays = 4, monthWon = 3_850,
            ),
            onNewCode = {}, onRetry = {}, onUnpair = {}, onResetRole = {},
        )
    }
}
