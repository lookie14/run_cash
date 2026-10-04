package io.github.lookie14.runcash

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.lookie14.runcash.ui.home.HomeRoute
import io.github.lookie14.runcash.ui.theme.GrandmaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 시스템이 다크 모드여도 밝은 배경에 맞춰 상태바 아이콘을 어둡게 유지
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            GrandmaTheme {
                HomeRoute()
            }
        }
    }
}
