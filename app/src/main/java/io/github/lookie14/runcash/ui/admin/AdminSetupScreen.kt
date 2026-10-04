package io.github.lookie14.runcash.ui.admin

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.ClaimResult
import io.github.lookie14.runcash.ui.theme.GrandmaTheme
import io.github.lookie14.runcash.ui.theme.RunCashColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val CODE_LENGTH = 6
private const val NAME_MAX = 12

/**
 * 관리자 폰을 처음 설정한다.
 *  - 새로 시작하기: 새 가족을 만들고 첫 관리자가 된다.
 *  - 참여하기: 다른 관리자가 만든 초대 코드로 같은 가족의 관리자가 된다.
 */
@Composable
fun AdminSetupRoute(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> String?) {
        loading = true
        message = null
        scope.launch {
            try {
                message = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "서버에 연결하지 못했어요. 인터넷을 확인해 주세요. (${e.message.orEmpty()})"
            } finally {
                loading = false
            }
        }
    }

    AdminSetupScreen(
        name = name,
        code = code,
        loading = loading,
        message = message,
        onNameChange = { name = it.take(NAME_MAX) },
        onCodeChange = {
            code = it.filter(Char::isDigit).take(CODE_LENGTH)
            message = null
        },
        onCreate = {
            run {
                val familyId = AppContainer.familyRepository.createFamily(name.trim())
                AppContainer.sessionStore.setFamily(familyId)
                null
            }
        },
        onJoin = {
            run {
                when (val result = AppContainer.familyRepository.joinAsAdmin(code, name.trim())) {
                    is ClaimResult.Success -> {
                        AppContainer.sessionStore.setFamily(result.familyId)
                        null
                    }
                    ClaimResult.Expired -> "시간이 지난 코드예요. 다른 관리자에게 새 코드를 받아 주세요."
                    else -> "코드가 맞지 않아요. 다른 관리자 화면의 '관리자 추가' 코드인지 확인해 주세요."
                }
            }
        },
        onBack = onBack,
    )
}

@Composable
fun AdminSetupScreen(
    name: String,
    code: String,
    loading: Boolean,
    message: String?,
    onNameChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameOk = name.isNotBlank()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("관리자 설정", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Ink)

            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("내 이름") },
                supportingText = { Text("다른 관리자에게 보이는 이름이에요") },
                singleLine = true,
            )

            if (message != null) {
                Text(message, fontSize = 16.sp, color = Color(0xFFB3261E))
            }

            Text("처음 시작하는 관리자라면", fontSize = 16.sp, color = RunCashColors.Muted)
            Button(
                onClick = onCreate,
                enabled = nameOk && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
            ) { Text(if (loading) "잠시만요..." else "새로 시작하기", fontSize = 20.sp) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = RunCashColors.Track)

            Text("이미 다른 관리자가 있다면, 그 관리자 화면의 '관리자 추가' 숫자를 넣어 주세요", fontSize = 16.sp, color = RunCashColors.Muted)
            OutlinedTextField(
                value = code,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("초대 숫자 6개") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedButton(
                onClick = onJoin,
                enabled = nameOk && code.length == CODE_LENGTH && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            ) { Text("참여하기", fontSize = 20.sp, color = RunCashColors.Forest) }

            TextButton(onClick = onBack) {
                Text("처음으로", fontSize = 16.sp, color = RunCashColors.Muted)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 900)
@Composable
private fun AdminSetupPreview() {
    GrandmaTheme {
        AdminSetupScreen(
            name = "승현", code = "", loading = false, message = null,
            onNameChange = {}, onCodeChange = {}, onCreate = {}, onJoin = {}, onBack = {},
        )
    }
}
