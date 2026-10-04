package io.github.lookie14.runcash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 어르신 눈에 잘 보이도록 대비를 높인 색. 걷기 = 숲 초록, 용돈 = 동전 금색. */
object RunCashColors {
    val Forest = Color(0xFF1E6B45)
    val Gold = Color(0xFFD99A00)
    val Track = Color(0xFFDCE5DE)
    val Background = Color(0xFFF2F6F3)
    val Passbook = Color(0xFFFFF3D1)
    val Rule = Color(0xFFE2CF9A)
    val Ink = Color(0xFF000000)
    val Muted = Color(0xFF3F4A44)
}

private val GrandmaColorScheme = lightColorScheme(
    primary = RunCashColors.Forest,
    onPrimary = Color.White,
    background = RunCashColors.Background,
    onBackground = RunCashColors.Ink,
    surface = Color.White,
    onSurface = RunCashColors.Ink,
)

/** 시스템 다크 모드와 상관없이 항상 밝고 대비 높은 화면을 쓴다. */
@Composable
fun GrandmaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GrandmaColorScheme, content = content)
}
