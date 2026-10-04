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
import kotlinx.coroutines.flow.StateFlow
import io.github.lookie14.runcash.domain.RuleSchedule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.lookie14.runcash.work.WorkScheduler

/**
 * 화면들이 같은 걸음 데이터와 서버 연결을 공유하도록 한 곳에서 만든다.
 * 디버그 빌드는 가짜 걸음 데이터, 릴리스 빌드는 Health Connect를 쓴다.
 */
object AppContainer {
    private var repository: StepRepository? = null
    private var store: SessionStore? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var memberJob: Job? = null
    private var memberKey: String? = null

    val stepRepository: StepRepository
        get() = checkNotNull(repository) { "AppContainer.init(context)를 먼저 호출해야 한다" }

    val sessionStore: SessionStore
        get() = checkNotNull(store) { "AppContainer.init(context)를 먼저 호출해야 한다" }

    val familyRepository: FamilyRepository by lazy { FamilyRepository() }

    private val _ruleSchedule = MutableStateFlow(RuleSchedule())

    /** 사용자 폰: 관리자가 정한 날짜별 목표/금액 규칙. 서버에서 받기 전에는 기본 규칙(5,000걸음/1,000원). */
    val ruleSchedule: StateFlow<RuleSchedule> = _ruleSchedule

    /** 앱이 화면으로 돌아올 때마다 올라간다. 걸음 수와 권한을 다시 확인하는 신호로 쓴다. */
    val resumeTick = MutableStateFlow(0)

    fun init(context: Context) {
        if (repository != null) return
        val app = context.applicationContext
        val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        repository = if (debuggable) FakeStepRepository() else HealthConnectStepRepository(app)
        val sessionStore = SessionStore(app)
        store = sessionStore

        // 화면과 상관없이 역할/연결 상태가 바뀌면 백그라운드 작업을 켜고 끈다.
        appScope.launch {
            sessionStore.session.collect {
                updateFamilyWork(it)
                updateScheduledWork(app, it)
            }
        }
    }

    /**
     * 연결된 폰에서 앱이 꺼져 있어도(화면과 상관없이) 도는 일.
     *  - 공통: 이 폰이 가족에서 빠지면(연결 끊김, 관리자 목록에서 빠짐) 연결 정보를 지워 처음 화면으로 보낸다.
     *  - 사용자 폰: 걸음 업로드, 관리자가 정한 규칙 받기
     */
    private fun updateFamilyWork(session: Session) {
        val familyId = session.familyId?.takeIf { session.role != null }
        val key = familyId?.let { "${session.role}:$it" }
        if (key == memberKey && memberJob?.isActive == true) return
        memberJob?.cancel()
        memberJob = null
        memberKey = key
        if (familyId == null) return
        val isUser = session.role == Role.Grandma
        memberJob = appScope.launch {
            launch { watchMembership(familyId, asAdmin = !isUser) }
            if (isUser) {
                launch { StepSyncer(stepRepository, familyRepository, sessionStore).run(familyId, resumeTick) }
                launch { watchRules(familyId) }
            }
        }
    }

    /** 앱이 꺼져 있을 때 도는 작업: 사용자 폰은 1시간마다 업로드, 관리자 폰은 매일 정산 확인. */
    private fun updateScheduledWork(context: Context, session: Session) {
        if (session.role == Role.Grandma && session.familyId != null) {
            WorkScheduler.scheduleStepUpload(context)
        } else {
            WorkScheduler.cancelStepUpload(context)
        }
        if (session.role == Role.Grandson && session.familyId != null) {
            WorkScheduler.scheduleSettlementReminder(context)
        } else {
            WorkScheduler.cancelSettlementReminder(context)
        }
    }

    /** 관리자가 규칙을 바꾸면 사용자 화면에 바로 반영한다. 오류가 나면 잠시 뒤 다시 지켜본다. */
    private suspend fun watchRules(familyId: String) {
        while (true) {
            try {
                familyRepository.observeRules(familyId).collect { _ruleSchedule.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 아래에서 잠시 쉬고 다시 시도
            }
            delay(LINK_RETRY_MILLIS)
        }
    }

    /** 이 폰이 가족에서 빠지면 연결 정보를 지운다. 사용자 폰은 숫자 입력, 관리자 폰은 시작 화면으로 돌아간다. */
    private suspend fun watchMembership(familyId: String, asAdmin: Boolean) {
        while (true) {
            try {
                familyRepository.observeMembership(familyId, asAdmin).first { member -> !member }
                // 그사이 다른 가족으로 바뀌었다면 건드리지 않는다.
                if (sessionStore.session.value.familyId == familyId) sessionStore.clearFamily()
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
