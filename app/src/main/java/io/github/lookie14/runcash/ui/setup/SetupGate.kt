package io.github.lookie14.runcash.ui.setup

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.HealthSetupChecker
import io.github.lookie14.runcash.data.HealthStatus
import io.github.lookie14.runcash.data.SetupStatus
import io.github.lookie14.runcash.ui.theme.GrandmaTheme
import io.github.lookie14.runcash.ui.theme.RunCashColors

/** 홈 화면 등에서 설정 화면을 다시 열 때 쓴다. */
val LocalOpenSetup = staticCompositionLocalOf<(() -> Unit)?> { null }

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

/**
 * 사용자 폰의 처음 설정 도우미.
 *  - 처음 한 번은 반드시 보여준다. 필수 항목(Health Connect 준비, 걸음 읽기 허용)이 되면 시작할 수 있다.
 *  - 이후에도 필수 항목이 풀리면(권한 취소 등) 다시 보여준다.
 *  - 홈 화면의 "설정 확인"으로 언제든 다시 열 수 있다.
 * 설정 화면을 다녀오면(앱으로 돌아오면) 자동으로 다시 확인해 ✓를 갱신한다.
 */
@Composable
fun SetupGate(content: @Composable () -> Unit) {
    val checker = AppContainer.setupChecker
    val store = AppContainer.sessionStore
    val context = LocalContext.current
    val tick by AppContainer.resumeTick.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<SetupStatus?>(null) }
    var setupDone by remember { mutableStateOf(store.setupDone) }
    var reopened by remember { mutableStateOf(false) }
    val isFake = AppContainer.stepRepository.isFake

    LaunchedEffect(tick, refresh) { status = checker.check() }

    val healthPermission = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { refresh++ }
    val activityPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refresh++ }

    val current = status
    if (current == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Box(Modifier.fillMaxSize().padding(padding))
        }
        return
    }

    // 가짜 데이터(디버그) 빌드는 Health Connect가 없어도 쓸 수 있다.
    val needSetup = reopened || !setupDone || (!isFake && !current.requiredDone)
    if (!needSetup) {
        CompositionLocalProvider(LocalOpenSetup provides { reopened = true }) { content() }
        return
    }

    SetupScreen(
        status = current,
        canFinish = current.requiredDone || isFake,
        showDevSkip = isFake,
        onInstallHealthConnect = { openHealthConnectInStore(context) },
        onRequestHealthPermission = {
            runCatching { healthPermission.launch(checker.permissionsToRequest()) }
                .onFailure { refresh++ }
        },
        onOpenSamsungHealth = {
            context.packageManager.getLaunchIntentForPackage(HealthSetupChecker.SAMSUNG_HEALTH)
                ?.let { runCatching { context.startActivity(it) } }
        },
        onOpenHealthConnect = { openHealthConnectSettings(context) },
        onRequestBatteryExemption = { requestBatteryExemption(context) },
        onRequestActivity = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityPermission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        },
        onRecheck = { refresh++ },
        onFinish = {
            store.setupDone = true
            setupDone = true
            reopened = false
        },
    )
}

private fun openHealthConnectInStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
    }
    try {
        context.startActivity(market)
    } catch (e: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")),
            )
        }
    }
}

/** Health Connect 설정 화면. 여기서 "앱 권한 → 삼성헬스"로 들어가면 된다. */
private fun openHealthConnectSettings(context: Context) {
    val intents = listOf(
        Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in intents) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

/** "배터리 사용량 최적화 중지" 시스템 창을 띄운다. 허용 한 번이면 된다. */
private fun requestBatteryExemption(context: Context) {
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    if (runCatching { context.startActivity(direct) }.isFailure) {
        runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

private enum class StepState { Done, Todo, Blocked }

@Composable
fun SetupScreen(
    status: SetupStatus,
    canFinish: Boolean,
    showDevSkip: Boolean,
    onInstallHealthConnect: () -> Unit,
    onRequestHealthPermission: () -> Unit,
    onOpenSamsungHealth: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestActivity: () -> Unit,
    onRecheck: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val healthReady = status.health == HealthStatus.Ready
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("처음 설정", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = RunCashColors.Ink)
            Text(
                "위에서부터 차례로 눌러 주세요. 다 되면 ✓가 표시돼요.",
                fontSize = 18.sp,
                color = RunCashColors.Muted,
            )

            // 1. Health Connect
            when (status.health) {
                HealthStatus.Unsupported -> StepCard(
                    number = 1, title = "Health Connect", tag = "필수", state = StepState.Blocked,
                    description = "이 폰은 걸음 수를 가져오는 기능(Health Connect)을 지원하지 않아요.",
                )
                HealthStatus.NeedsInstall -> StepCard(
                    number = 1, title = "Health Connect 설치", tag = "필수", state = StepState.Todo,
                    description = "걸음 수를 주고받는 구글 앱이에요. 설치하거나 업데이트해 주세요.",
                ) { PrimaryAction("설치하러 가기", onInstallHealthConnect) }
                HealthStatus.Error -> StepCard(
                    number = 1, title = "Health Connect 확인", tag = "필수", state = StepState.Todo,
                    description = "상태를 확인하지 못했어요. 잠시 뒤 다시 확인해 주세요.",
                ) { PrimaryAction("다시 확인", onRecheck) }
                else -> StepCard(
                    number = 1, title = "Health Connect 준비됨", tag = "필수", state = StepState.Done,
                    description = "걸음 수를 주고받을 준비가 됐어요.",
                )
            }

            // 2. 걸음 읽기 허용 (+ 백그라운드)
            val bgNote = when {
                !status.readGranted -> ""
                !status.backgroundSupported -> "\n앱이 꺼져 있을 때 읽기는 이 폰에서 지원하지 않아요. 앱을 열 때 올라가요."
                status.backgroundGranted -> "\n앱이 꺼져 있을 때도 1시간마다 올라가요."
                else -> "\n\"백그라운드에서 읽기\"도 허용하면 앱이 꺼져 있을 때도 올라가요."
            }
            StepCard(
                number = 2, title = "걸음 수 읽기 허용", tag = "필수",
                state = when {
                    !healthReady -> StepState.Blocked
                    status.readGranted && (status.backgroundGranted || !status.backgroundSupported) -> StepState.Done
                    status.readGranted -> StepState.Done
                    else -> StepState.Todo
                },
                description = (if (status.readGranted) "Run Cash가 걸음 수를 읽을 수 있어요." else "Run Cash가 Health Connect의 걸음 수를 읽도록 허용해 주세요.") + bgNote,
            ) {
                if (healthReady && (!status.readGranted || (status.backgroundSupported && !status.backgroundGranted))) {
                    PrimaryAction(if (status.readGranted) "백그라운드도 허용하기" else "허용하기", onRequestHealthPermission)
                }
            }

            // 3. 삼성헬스 → Health Connect
            val sourceOk = status.stepSourceName != null
            StepCard(
                number = 3, title = "삼성헬스 걸음 보내기", tag = "권장",
                state = when {
                    !status.readGranted -> StepState.Blocked
                    sourceOk -> StepState.Done
                    else -> StepState.Todo
                },
                description = when {
                    !status.readGranted -> "2번을 먼저 해 주세요."
                    sourceOk -> "${status.stepSourceName}에서 걸음이 들어오고 있어요."
                    else -> "삼성헬스가 Health Connect로 걸음을 보내도록 켜 주세요.\n" +
                        "① 아래 [Health Connect 열기] → 앱 권한 → 삼성헬스(Samsung Health) → 모두 허용\n" +
                        "② [삼성헬스 열기]로 삼성헬스를 한 번 열어 주세요.\n" +
                        "켠 뒤 걸음이 들어오기까지 최대 1시간 걸릴 수 있어요. 나중에 다시 확인해도 돼요."
                },
            ) {
                if (status.readGranted && !sourceOk) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenHealthConnect) { Text("Health Connect 열기", fontSize = 15.sp) }
                        if (status.samsungInstalled) {
                            OutlinedButton(onClick = onOpenSamsungHealth) { Text("삼성헬스 열기", fontSize = 15.sp) }
                        }
                    }
                    TextButton(onClick = onRecheck) { Text("다시 확인", fontSize = 15.sp) }
                }
            }

            // 4. 배터리 절전 예외
            StepCard(
                number = 4, title = "배터리 절전 예외", tag = "권장",
                state = if (status.batteryExempt) StepState.Done else StepState.Todo,
                description = if (status.batteryExempt) {
                    "절전 때문에 기록이 멈추지 않아요."
                } else {
                    "폰이 절전하느라 걸음 업로드를 멈추지 않도록 허용해 주세요. 배터리는 거의 쓰지 않아요."
                },
            ) {
                if (!status.batteryExempt) PrimaryAction("허용하기", onRequestBatteryExemption)
            }

            // 5. 실시간 걸음 (센서)
            if (status.sensorAvailable) {
                StepCard(
                    number = 5, title = "걷는 즉시 숫자 올리기", tag = "선택",
                    state = if (status.activityGranted) StepState.Done else StepState.Todo,
                    description = if (status.activityGranted) {
                        "앱을 켜 둔 동안 걸으면 바로 숫자가 올라가요."
                    } else {
                        "\"신체 활동\"을 허용하면 앱을 켜 둔 동안 걸음이 바로 반영돼요."
                    },
                ) {
                    if (!status.activityGranted) PrimaryAction("허용하기", onRequestActivity)
                }
            }

            Button(
                onClick = onFinish,
                enabled = canFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
            ) { Text("시작하기", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Text(
                if (canFinish) "권장 항목은 나중에 해도 돼요. 홈 화면 맨 아래 \"설정 확인\"에서 다시 볼 수 있어요."
                else "1, 2번(필수)을 마치면 시작할 수 있어요.",
                fontSize = 15.sp,
                color = RunCashColors.Muted,
            )
            if (showDevSkip) {
                Text("개발용(디버그) 빌드라 가짜 걸음 데이터를 써요. 필수 항목 없이 시작할 수 있어요.", fontSize = 13.sp, color = RunCashColors.Muted)
            }
        }
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RunCashColors.Forest),
    ) { Text(text, fontSize = 18.sp) }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    tag: String,
    state: StepState,
    description: String,
    actions: @Composable () -> Unit = {},
) {
    val done = state == StepState.Done
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (done) Color(0xFFE3F1E8) else Color.White,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            when (state) {
                                StepState.Done -> RunCashColors.Forest
                                StepState.Todo -> RunCashColors.Gold
                                StepState.Blocked -> Color(0xFFB7C7BC)
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (done) "✓" else "$number", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RunCashColors.Ink, modifier = Modifier.weight(1f))
                Text(tag, fontSize = 14.sp, color = if (tag == "필수") Color(0xFFB3261E) else RunCashColors.Muted)
            }
            Text(description, fontSize = 16.sp, color = RunCashColors.Ink)
            actions()
        }
    }
}

@Preview(showBackground = true, heightDp = 1300)
@Composable
private fun SetupScreenPreview() {
    GrandmaTheme {
        SetupScreen(
            status = SetupStatus(
                health = HealthStatus.Ready,
                readGranted = true,
                backgroundSupported = true,
                backgroundGranted = false,
                samsungInstalled = true,
                sensorAvailable = true,
            ),
            canFinish = true,
            showDevSkip = false,
            onInstallHealthConnect = {}, onRequestHealthPermission = {}, onOpenSamsungHealth = {},
            onOpenHealthConnect = {}, onRequestBatteryExemption = {}, onRequestActivity = {},
            onRecheck = {}, onFinish = {},
        )
    }
}
