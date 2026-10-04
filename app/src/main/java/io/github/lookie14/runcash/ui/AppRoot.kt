package io.github.lookie14.runcash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.Role
import io.github.lookie14.runcash.ui.grandson.GrandsonRoute
import io.github.lookie14.runcash.ui.pairing.GrandmaPairRoute
import io.github.lookie14.runcash.ui.role.RoleSelectScreen

/**
 * 앱의 시작점. 이 폰의 역할과 연결 상태에 따라 보여줄 화면을 고른다.
 *  역할 없음 -> 역할 선택 / 손주 -> 손주 화면 / 할머니(연결 전) -> 숫자 입력 / 할머니(연결됨) -> 걸음 화면
 */
@Composable
fun AppRoot() {
    val store = AppContainer.sessionStore
    val session by store.session.collectAsState()

    when {
        session.role == null -> RoleSelectScreen(onSelect = store::setRole)
        session.role == Role.Grandson -> GrandsonRoute(onResetRole = store::reset)
        session.familyId == null -> GrandmaPairRoute(onBack = store::reset)
        else -> RunCashApp()
    }
}
