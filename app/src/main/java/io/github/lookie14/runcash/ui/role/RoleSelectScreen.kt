package io.github.lookie14.runcash.ui.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lookie14.runcash.data.Role
import io.github.lookie14.runcash.ui.theme.GrandmaTheme
import io.github.lookie14.runcash.ui.theme.RunCashColors

/** 앱을 처음 열었을 때, 이 폰이 누구 것인지 고른다. */
@Composable
fun RoleSelectScreen(onSelect: (Role) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 사용자(어르신)가 보는 화면이라 큰 버튼 하나만 크게 두고, 관리자 선택은 아래에 작게 둔다.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = "반가워요!",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = RunCashColors.Ink,
                )
                Button(
                    onClick = { onSelect(Role.Grandma) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
                ) {
                    Text("시작하기", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = { onSelect(Role.Grandson) }) {
                Text("관리자로 시작하기", fontSize = 16.sp, color = RunCashColors.Muted)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun RoleSelectPreview() {
    GrandmaTheme { RoleSelectScreen(onSelect = {}) }
}
