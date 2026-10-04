package io.github.lookie14.runcash.ui.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.HealthConnectStepRepository
import io.github.lookie14.runcash.data.HealthStatus
import io.github.lookie14.runcash.ui.theme.GrandmaTheme
import io.github.lookie14.runcash.ui.theme.RunCashColors

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * Health Connect를 쓰는 빌드에서는 걸음 수 읽기 권한이 준비될 때까지 안내 화면을 보여주고,
 * 준비되면 content를 보여준다. 가짜 데이터(디버그) 빌드는 바로 content를 보여준다.
 */
@Composable
fun HealthGate(content: @Composable () -> Unit) {
    val repository = AppContainer.stepRepository as? HealthConnectStepRepository
    if (repository == null) {
        content()
        return
    }

    val context = LocalContext.current
    val tick by AppContainer.resumeTick.collectAsState()
    var status by remember { mutableStateOf(HealthStatus.Checking) }
    var retry by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        status = if (granted.containsAll(repository.requiredPermissions)) {
            HealthStatus.Ready
        } else {
            HealthStatus.NeedsPermission
        }
    }

    // 처음, 그리고 앱이 화면으로 돌아올 때마다(설치/설정을 다녀온 뒤) 다시 확인한다.
    // checkStatus는 오류가 나도 예외 대신 Error를 돌려준다.
    LaunchedEffect(tick, retry) {
        status = repository.checkStatus()
    }

    when (status) {
        HealthStatus.Ready -> content()
        HealthStatus.Checking -> BlankScreen()
        else -> HealthSetupScreen(
            status = status,
            onGrant = {
                try {
                    // 지원하는 폰이면 백그라운드 읽기 권한도 같은 화면에서 함께 묻는다.
                    permissionLauncher.launch(repository.permissionsToRequest())
                } catch (e: Exception) {
                    status = HealthStatus.Error
                }
            },
            onInstall = { openHealthConnectInStore(context) },
            onRetry = {
                status = HealthStatus.Checking
                retry++
            },
        )
    }
}

@Composable
private fun BlankScreen() {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {}
    }
}

@Composable
fun HealthSetupScreen(
    status: HealthStatus,
    onGrant: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
) {
    val title: String
    val body: String
    val button: String?
    val action: () -> Unit
    when (status) {
        HealthStatus.NeedsInstall -> {
            title = "건강 앱이 필요해요"
            body = "걸음 수를 가져오려면 \"Health Connect\" 앱을 설치해야 해요."
            button = "설치하러 가기"
            action = onInstall
        }
        HealthStatus.Error -> {
            title = "걸음 수를 확인하지 못했어요"
            body = "잠시 뒤에 다시 눌러 주세요."
            button = "다시 시도"
            action = onRetry
        }
        HealthStatus.Unsupported -> {
            title = "이 폰에서는 쓸 수 없어요"
            body = "이 폰은 걸음 수를 가져오는 기능을 지원하지 않아요."
            button = null
            action = {}
        }
        else -> {
            title = "걸음 수를 가져올게요"
            body = "오늘 얼마나 걸으셨는지 보려면\n걸음 수 허용이 필요해요."
            button = "허용하기"
            action = onGrant
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = RunCashColors.Ink,
            )
            Text(
                text = body,
                fontSize = 26.sp,
                textAlign = TextAlign.Center,
                color = RunCashColors.Ink,
            )
            if (button != null) {
                Button(
                    onClick = action,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
                ) {
                    Text(button, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (status == HealthStatus.NeedsPermission) {
                Text(
                    text = "허용 창이 안 뜨면 폰 설정에서\nHealth Connect를 열어 허용해 주세요.",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    color = RunCashColors.Muted,
                )
            }
        }
    }
}

private fun openHealthConnectInStore(context: Context) {
    val uri = Uri.parse(
        "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding",
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = uri
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Play 스토어가 없으면 웹 주소로 연다.
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"),
                ),
            )
        }
    }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun HealthSetupPermissionPreview() {
    GrandmaTheme { HealthSetupScreen(HealthStatus.NeedsPermission, onGrant = {}, onInstall = {}) }
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun HealthSetupInstallPreview() {
    GrandmaTheme { HealthSetupScreen(HealthStatus.NeedsInstall, onGrant = {}, onInstall = {}) }
}
