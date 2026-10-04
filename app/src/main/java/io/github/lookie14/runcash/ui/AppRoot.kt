package io.github.lookie14.runcash.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.Role
import io.github.lookie14.runcash.ui.admin.AdminRoute
import io.github.lookie14.runcash.ui.admin.AdminSetupRoute
import io.github.lookie14.runcash.ui.pairing.GrandmaPairRoute
import io.github.lookie14.runcash.ui.role.RoleSelectScreen

/**
 * 앱의 시작점. 이 폰의 역할과 연결 상태에 따라 보여줄 화면을 고른다.
 *  역할 없음 -> 역할 선택
 *  관리자(가족 없음) -> 새로 시작/참여 / 관리자(가족 있음) -> 관리자 화면
 *  사용자(연결 전) -> 숫자 입력 / 사용자(연결됨) -> 걸음 화면
 *  (코드 안에서는 사용자 = Role.Grandma, 관리자 = Role.Grandson)
 */
@Composable
fun AppRoot() {
    val store = AppContainer.sessionStore
    val session by store.session.collectAsState()

    val familyId = session.familyId
    when {
        session.role == null -> RoleSelectScreen(onSelect = store::setRole)
        session.role == Role.Grandson && familyId == null -> AdminSetupRoute(onBack = store::reset)
        session.role == Role.Grandson -> AdminRoute(familyId = familyId!!)
        familyId == null -> GrandmaPairRoute(onBack = store::reset)
        else -> RunCashApp()
    }
}
