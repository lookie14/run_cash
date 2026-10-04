package io.github.lookie14.runcash

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import kotlinx.coroutines.flow.update
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.ui.AppRoot
import io.github.lookie14.runcash.ui.theme.GrandmaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContainer.init(applicationContext)
        // 시스템이 다크 모드여도 밝은 배경에 맞춰 상태바 아이콘을 어둡게 유지
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            GrandmaTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 설치/설정 화면을 다녀왔거나 자정이 지났을 수 있으니 권한과 기록을 다시 확인한다.
        AppContainer.resumeTick.update { it + 1 }
    }
}
