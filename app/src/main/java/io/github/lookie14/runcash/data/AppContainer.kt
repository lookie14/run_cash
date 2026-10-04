package io.github.lookie14.runcash.data

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 화면들이 같은 걸음 데이터와 서버 연결을 공유하도록 한 곳에서 만든다.
 * 디버그 빌드는 가짜 걸음 데이터, 릴리스 빌드는 Health Connect를 쓴다.
 */
object AppContainer {
    private var repository: StepRepository? = null
    private var store: SessionStore? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var grandmaJob: Job? = null
    private var grandmaFamilyId: String? = null

    val stepRepository: StepRepository
        get() = checkNotNull(repository) { "AppContainer.init(context)를 먼저 호출해야 한다" }

    val sessionStore: SessionStore
        get() = checkNotNull(store) { "AppContainer.init(context)를 먼저 호출해야 한다" }

    val familyRepository: FamilyRepository by lazy { FamilyRepository() }

    /** 앱이 화면으로 돌아올 때마다 올라간다. 걸음 수와 권한을 다시 확인하는 신호로 쓴다. */
    val resumeTick = MutableStateFlow(0)

    fun init(context: Context) {
        if (repository != null) return
        val app = context.applicationContext
        val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        repository = if (debuggable) FakeStepRepository() else HealthConnectStepRepository(app)
        val sessionStore = SessionStore(app)
        store = sessionStore

        // 화면과 상관없이 역할/연결 상태가 바뀌면 할머니 폰의 백그라운드 작업을 켜고 끈다.
        appScope.launch {
            sessionStore.session.collect { updateGrandmaWork(it) }
        }
    }

    /** 할머니 폰이고 연결되어 있으면 걸음 업로드와 연결 감시를 돌리고, 아니면 멈춘다. */
    private fun updateGrandmaWork(session: Session) {
        val familyId = session.familyId?.takeIf { session.role == Role.Grandma }
        if (familyId == grandmaFamilyId && grandmaJob?.isActive == true) return
        grandmaJob?.cancel()
        grandmaJob = null
        grandmaFamilyId = familyId
        if (familyId == null) return
        grandmaJob = appScope.launch {
            launch { StepSyncer(stepRepository, familyRepository, sessionStore).run(familyId, resumeTick) }
            launch { watchLink(familyId) }
        }
    }

    /** 손주가 연결을 끊으면 할머니 폰을 숫자 입력 화면으로 돌려보낸다. */
    private suspend fun watchLink(familyId: String) {
        while (true) {
            try {
                familyRepository.observeGrandmaLink(familyId).first { linked -> !linked }
                sessionStore.clearFamily()
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 네트워크 오류 등. 잠시 뒤 다시 지켜본다.
                delay(LINK_RETRY_MILLIS)
            }
        }
    }

    private const val LINK_RETRY_MILLIS = 60_000L
}
