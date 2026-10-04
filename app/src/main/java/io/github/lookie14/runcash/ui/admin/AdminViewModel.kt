package io.github.lookie14.runcash.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lookie14.runcash.data.AdminMember
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.DailyRecord
import io.github.lookie14.runcash.data.FamilyRepository
import io.github.lookie14.runcash.data.PairKind
import io.github.lookie14.runcash.data.Settlement
import io.github.lookie14.runcash.domain.ActivityStats
import io.github.lookie14.runcash.domain.MonthSummary
import io.github.lookie14.runcash.domain.PointRules
import io.github.lookie14.runcash.domain.RuleSchedule
import io.github.lookie14.runcash.ui.calendar.CalendarUiState
import io.github.lookie14.runcash.ui.calendar.buildCalendarState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class AdminUiState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val paired: Boolean = false,
    /** 사용자 폰 연결 전에 보여줄 6자리 코드. */
    val code: String? = null,
    val today: LocalDate = LocalDate.now(),

    // 관리자
    val admins: List<AdminMember> = emptyList(),
    val myUid: String? = null,
    /** 관리자 추가용 6자리 코드. 만들기 전에는 null. */
    val adminInviteCode: String? = null,

    // 모니터링
    val recentLoaded: Boolean = false,
    /** 사용자 폰이 마지막으로 기록을 올린 시각. 기록이 하나도 없으면 null. */
    val lastRecordMillis: Long? = null,
    val todaySteps: Long = 0,
    val thisMonth: MonthSummary = MonthSummary(0, 0, 0, 0),
    val activity: ActivityStats? = null,

    // 규칙
    val todayRules: PointRules = PointRules(),
    val tomorrowRules: PointRules = PointRules(),
    val savingRules: Boolean = false,

    // 지난달 정산
    val lastMonth: YearMonth = YearMonth.now().minusMonths(1),
    val lastMonthSummary: MonthSummary? = null,
    val lastMonthSettlement: Settlement? = null,
    val settlementLoaded: Boolean = false,
    val markingPaid: Boolean = false,

    val calendar: CalendarUiState = CalendarUiState(month = YearMonth.now()),
)

/** 서버에서 받은 원본 값. 화면용 계산은 규칙과 합쳐서 uiState에서 한다. */
private data class AdminCore(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val paired: Boolean = false,
    val code: String? = null,
    val admins: List<AdminMember> = emptyList(),
    val myUid: String? = null,
    val adminInviteCode: String? = null,
    val today: LocalDate = LocalDate.now(),
    val recent: Map<LocalDate, DailyRecord>? = null,
    val settlement: Settlement? = null,
    val settlementLoaded: Boolean = false,
    val markingPaid: Boolean = false,
    val savingRules: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val repository: FamilyRepository,
    private val familyId: String,
) : ViewModel() {

    private val core = MutableStateFlow(AdminCore())
    private val schedule = MutableStateFlow(RuleSchedule())
    private val calendarMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())
    private val calendarData = MutableStateFlow<Pair<YearMonth, Map<LocalDate, Long>>?>(null)

    val uiState: StateFlow<AdminUiState> =
        combine(core, schedule, calendarMonth, selectedDate, calendarData) { c, rules, month, selected, data ->
            toUiState(c, rules, month, selected, data)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AdminUiState())

    private var mainJob: Job? = null

    init {
        start()
    }

    /** 처음 시작할 때, "다시 시도", 날짜가 바뀌었을 때 쓴다. */
    fun start() {
        mainJob?.cancel()
        core.value = AdminCore()
        calendarData.value = null
        mainJob = viewModelScope.launch {
            try {
                val id = familyId
                val myUid = repository.uid()
                core.update { it.copy(myUid = myUid) }
                launchSafely { repository.observeRules(id).collect { schedule.value = it } }

                var detailJob: Job? = null
                repository.observeFamily(id).collect { family ->
                    core.update {
                        it.copy(
                            isLoading = false,
                            paired = family.paired,
                            code = if (family.paired) null else it.code,
                            admins = family.admins,
                        )
                    }
                    if (family.paired) {
                        if (detailJob?.isActive != true) {
                            detailJob = launch {
                                launchSafely { observeRecent(id) }
                                launchSafely { observeSettlement(id) }
                                launchSafely { observeCalendar(id) }
                            }
                        }
                    } else {
                        detailJob?.cancel()
                        detailJob = null
                        if (core.value.code == null) newCode()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("서버에 연결하지 못했어요", e)
            }
        }
    }

    /**
     * 하위 작업은 이 함수로 띄운다. 오류가 나면 앱을 끄지 않고 화면에 문구를 보여준다.
     * (그냥 launch하면 하위 작업의 오류가 부모로 번져 앱이 종료된다)
     */
    private fun CoroutineScope.launchSafely(block: suspend CoroutineScope.() -> Unit): Job = launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showError("기록을 불러오지 못했어요", e)
        }
    }

    private fun showError(prefix: String, e: Exception) {
        core.update { it.copy(isLoading = false, errorMessage = "$prefix. (${e.message.orEmpty()})") }
    }

    private suspend fun observeRecent(id: String) {
        val today = LocalDate.now()
        repository.observeDays(id, today.minusDays(RECENT_DAYS), today).collect { records ->
            core.update { it.copy(today = today, recent = records) }
        }
    }

    private suspend fun observeSettlement(id: String) {
        val month = YearMonth.from(core.value.today).minusMonths(1)
        repository.observeSettlement(id, month).collect { settlement ->
            core.update { it.copy(settlement = settlement, settlementLoaded = true) }
        }
    }

    private suspend fun observeCalendar(id: String) {
        calendarMonth
            .flatMapLatest { month -> repository.observeMonth(id, month).map { month to it } }
            .collect { calendarData.value = it }
    }

    private fun toUiState(
        c: AdminCore,
        rules: RuleSchedule,
        month: YearMonth,
        selected: LocalDate?,
        data: Pair<YearMonth, Map<LocalDate, Long>>?,
    ): AdminUiState {
        val today = c.today
        val lastMonth = YearMonth.from(today).minusMonths(1)
        val steps = c.recent?.mapValues { it.value.steps }.orEmpty()
        val calendar = if (data != null && data.first == month) {
            buildCalendarState(month, today, selected, rules) { date -> data.second[date] ?: 0L }
        } else {
            CalendarUiState(month = month, dailyGoal = rules.goalOn(today), canGoNext = month < YearMonth.from(today))
        }
        return AdminUiState(
            isLoading = c.isLoading,
            errorMessage = c.errorMessage,
            paired = c.paired,
            code = c.code,
            admins = c.admins,
            myUid = c.myUid,
            adminInviteCode = c.adminInviteCode,
            today = today,
            recentLoaded = c.recent != null,
            lastRecordMillis = c.recent?.values?.mapNotNull { it.updatedAtMillis }?.maxOrNull(),
            todaySteps = steps[today] ?: 0L,
            thisMonth = MonthSummary.of(steps.filterKeys { YearMonth.from(it) == YearMonth.from(today) }, rules),
            activity = c.recent?.let { ActivityStats.of(today, steps, rules) },
            todayRules = rules.rulesOn(today),
            tomorrowRules = rules.rulesOn(today.plusDays(1)),
            savingRules = c.savingRules,
            lastMonth = lastMonth,
            lastMonthSummary = c.recent?.let { MonthSummary.of(steps.filterKeys { YearMonth.from(it) == lastMonth }, rules) },
            lastMonthSettlement = c.settlement,
            settlementLoaded = c.settlementLoaded,
            markingPaid = c.markingPaid,
            calendar = calendar,
        )
    }

    /** "보냈어요": 지금 보이는 지난달 금액으로 정산 기록을 남긴다. */
    fun markLastMonthPaid() {
        val id = familyId
        val state = uiState.value
        val myName = state.admins.firstOrNull { it.uid == state.myUid }?.name ?: "관리자"
        val summary = state.lastMonthSummary ?: return
        if (state.markingPaid) return
        core.update { it.copy(markingPaid = true) }
        viewModelScope.launch {
            try {
                repository.markPaid(id, state.lastMonth, summary, paidBy = myName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("정산 기록을 남기지 못했어요", e)
            } finally {
                core.update { it.copy(markingPaid = false) }
            }
        }
    }

    /** 목표 걸음과 하루 최대 금액을 바꾼다. 내일부터 적용된다. */
    fun saveRules(goal: Int, maxWon: Int) {
        val id = familyId
        if (goal !in GOAL_RANGE || maxWon !in MAX_WON_RANGE) return
        core.update { it.copy(savingRules = true) }
        viewModelScope.launch {
            try {
                repository.setRulesFrom(id, core.value.today.plusDays(1), goal, maxWon)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("목표를 저장하지 못했어요", e)
            } finally {
                core.update { it.copy(savingRules = false) }
            }
        }
    }

    fun newCode() {
        viewModelScope.launch {
            try {
                val pairCode = repository.createPairCode(familyId, PairKind.User)
                core.update { it.copy(code = pairCode.code, errorMessage = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("연결 코드를 만들지 못했어요", e)
            }
        }
    }

    /** 다른 관리자 폰을 추가할 초대 코드를 만든다. (10분 유효) */
    fun createAdminInvite() {
        viewModelScope.launch {
            try {
                val pairCode = repository.createPairCode(familyId, PairKind.Admin)
                core.update { it.copy(adminInviteCode = pairCode.code) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("초대 코드를 만들지 못했어요", e)
            }
        }
    }

    fun closeAdminInvite() {
        core.update { it.copy(adminInviteCode = null) }
    }

    /** 관리자를 뺀다. 마지막 한 명은 뺄 수 없다. 나를 빼면 이 폰은 관리자 시작 화면으로 돌아간다. */
    fun removeAdmin(uid: String) {
        if (core.value.admins.size <= 1) return
        viewModelScope.launch {
            try {
                repository.removeAdmin(familyId, uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError("관리자를 빼지 못했어요", e)
            }
        }
    }

    fun previousMonth() = moveTo(calendarMonth.value.minusMonths(1))

    fun nextMonth() {
        if (calendarMonth.value < YearMonth.from(core.value.today)) moveTo(calendarMonth.value.plusMonths(1))
    }

    private fun moveTo(target: YearMonth) {
        calendarMonth.value = target
        selectedDate.value = if (target == YearMonth.from(core.value.today)) core.value.today else null
    }

    fun selectDate(date: LocalDate) {
        if (!date.isAfter(core.value.today)) selectedDate.value = date
    }

    /** 화면이 열린 채로 날짜가 바뀌었으면 새 날짜 기준으로 다시 불러온다. */
    fun refreshIfDayChanged() {
        if (core.value.recent != null && LocalDate.now() != core.value.today) {
            calendarMonth.value = YearMonth.now()
            selectedDate.value = LocalDate.now()
            start()
        }
    }

    companion object {
        /** 최근 기록을 지켜보는 기간. 지난달 1일까지 들어가도록 넉넉히 잡는다. */
        private const val RECENT_DAYS = 63L
        val GOAL_RANGE = 1_000..50_000
        val MAX_WON_RANGE = 0..100_000

        fun factory(familyId: String) = viewModelFactory {
            initializer { AdminViewModel(AppContainer.familyRepository, familyId) }
        }
    }
}
