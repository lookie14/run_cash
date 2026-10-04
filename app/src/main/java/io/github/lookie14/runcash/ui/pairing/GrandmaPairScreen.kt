package io.github.lookie14.runcash.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.ClaimResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import io.github.lookie14.runcash.ui.theme.GrandmaTheme
import io.github.lookie14.runcash.ui.theme.RunCashColors

private const val CODE_LENGTH = 6

/** 사용자 폰: 관리자 폰에 뜬 6자리 숫자를 입력해서 연결한다. */
@Composable
fun GrandmaPairRoute(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    GrandmaPairScreen(
        code = code,
        loading = loading,
        message = message,
        onCodeChange = {
            code = it.filter(Char::isDigit).take(CODE_LENGTH)
            message = null
        },
        onSubmit = {
            loading = true
            message = null
            scope.launch {
                try {
                    when (val result = AppContainer.familyRepository.claimPairCode(code)) {
                        is ClaimResult.Success -> AppContainer.sessionStore.setFamily(result.familyId)
                        ClaimResult.InvalidCode -> message = "숫자가 맞지 않아요.\n다시 확인해 주세요."
                        ClaimResult.Expired -> message = "시간이 지났어요.\n관리자 폰에서 새 숫자를 받아 주세요."
                        ClaimResult.AlreadyPaired -> message = "이미 연결된 숫자예요.\n관리자 폰에서 새 숫자를 받아 주세요."
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message = "연결하지 못했어요.\n인터넷을 확인하고 다시 눌러 주세요."
                } finally {
                    loading = false
                }
            }
        },
        onBack = onBack,
    )
}

@Composable
fun GrandmaPairScreen(
    code: String,
    loading: Boolean,
    message: String?,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
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
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = "관리자 폰에 나온\n숫자 6개를 넣어 주세요",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = RunCashColors.Ink,
            )
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = 8.sp,
                    color = RunCashColors.Ink,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (message != null) {
                Text(
                    text = message,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    color = Color(0xFFB3261E),
                )
            }
            Button(
                onClick = onSubmit,
                enabled = code.length == CODE_LENGTH && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
            ) {
                Text(if (loading) "연결 중..." else "연결하기", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onBack) {
                Text("처음으로", fontSize = 20.sp, color = RunCashColors.Muted)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun GrandmaPairPreview() {
    GrandmaTheme {
        GrandmaPairScreen(
            code = "123",
            loading = false,
            message = null,
            onCodeChange = {},
            onSubmit = {},
            onBack = {},
        )
    }
}
