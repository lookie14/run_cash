package io.github.lookie14.runcash.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.FakeStepRepository
import io.github.lookie14.runcash.data.Settlement
import io.github.lookie14.runcash.data.StepRepository
import io.github.lookie14.runcash.data.currentDateFlow
import io.github.lookie14.runcash.domain.RuleSchedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val todaySteps: Long = 0,
    val dailyGoal: Int = 5_000,
    val todayPoints: Int = 0,
    val monthPoints: Int = 0,
    val todayWon: Int = 0,
    val monthWon: Int = 0,
    val isLoading: Boolean = true,
    /** 지난달 용돈. 금액이 없고 정산 기록도 없으면 null. */
    val lastMonth: LastMonthAllowance? = null,
    /** 내일부터 바뀌는 규칙. 바뀌지 않으면 null. */
    val upcoming: UpcomingRule? = null,
) {
    val goalReached: Boolean get() = todaySteps >= dailyGoal
    val stepsLeft: Long get() = (dailyGoal - todaySteps).coerceAtLeast(0)
    val progress: Float get() = (todaySteps.toFloat() / dailyGoal).coerceIn(0f, 1f)
}

/** 지난달 용돈 안내. paid면 관리자가 "보냈어요"를 누른 상태이고, 금액은 그때 고정된 값이다. */
data class LastMonthAllowance(val month: Int, val won: Int, val paid: Boolean)

data class UpcomingRule(val goal: Int, val maxWon: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: StepRepository,
    dateFlow: Flow<LocalDate> = currentDateFlow(),
    /** 값이 바뀔 때마다 지난 기록을 다시 읽는다. (앱이 화면으로 돌아올 때 등) */
    refresh: Flow<Int> = flowOf(0),
    /** 그 달의 정산 기록. 연결 전이거나 읽을 수 없으면 null을 내보낸다. */
    private val settlementOf: (YearMonth) -> Flow<Settlement?> = { flowOf(null) },
    /** 관리자가 정한 날짜별 규칙. */
    schedule: Flow<RuleSchedule> = flowOf(RuleSchedule()),
) : ViewModel() {

    /** 오늘 날짜. 자정이 지나면 저절로 바뀐다. */
    private val today: StateFlow<LocalDate> =
        dateFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    private val rules: StateFlow<RuleSchedule> =
        schedule.stateIn(viewModelScope, SharingStarted.Eagerly, RuleSchedule())

    /** 이번 달 1일부터 어제까지의 기록과 그 기준 날짜. 불러오기 전에는 null. */
    private val pastDays: StateFlow<Pair<LocalDate, Map<LocalDate, Long>>?> =
        combine(today, refresh) { date, _ -> date }
            .flatMapLatest { date ->
                flow {
                    emit(date to repository.stepsBetween(date.withDayOfMonth(1), date.minusDays(1)))
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 지난달 금액: 정산됐으면 기록된 금액, 아니면 폰의 걸음 기록과 그날의 규칙으로 계산한 예상 금액. */
    private val lastMonth: StateFlow<LastMonthAllowance?> =
        combine(today, refresh, rules) { date, _, schedule -> date to schedule }
            .flatMapLatest { (date, schedule) ->
                val month = YearMonth.from(date).minusMonths(1)
                val estimated = flow {
                    val days = repository.stepsBetween(month.atDay(1), month.atEndOfMonth())
                    emit(schedule.wonForPeriod(days))
                }
                val settlement = settlementOf(month).catch { emit(null) }
                combine(estimated, settlement) { won, settled ->
                    LastMonthAllowance(
                        month = month.monthValue,
                        won = settled?.amount ?: won,
                        paid = settled != null,
                    ).takeIf { it.paid || it.won > 0 }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val showTestControls: Boolean = repository.isFake

    val uiState: StateFlow<HomeUiState> =
        combine(today, repository.todaySteps(), pastDays, lastMonth, rules) { date, todaySteps, past, last, schedule ->
            val todayRules = schedule.rulesOn(date)
            val tomorrowRules = schedule.rulesOn(date.plusDays(1))
            val upcoming = if (tomorrowRules != todayRules) {
                UpcomingRule(tomorrowRules.dailyGoal, tomorrowRules.dailyMaxPoints)
            } else {
                null
            }

            if (past == null || past.first != date) {
                HomeUiState(
                    date = date,
                    dailyGoal = todayRules.dailyGoal,
                    todaySteps = todaySteps,
                    lastMonth = last,
                    upcoming = upcoming,
                )
            } else {
                val todayWon = schedule.wonForDay(date, todaySteps)
                val monthWon = schedule.wonForPeriod(past.second + (date to todaySteps))
                HomeUiState(
                    date = date,
                    todaySteps = todaySteps,
                    dailyGoal = todayRules.dailyGoal,
                    todayPoints = todayWon,
                    monthPoints = monthWon,
                    todayWon = todayWon,
                    monthWon = monthWon,
                    isLoading = false,
                    lastMonth = last,
                    upcoming = upcoming,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(date = today.value, dailyGoal = rules.value.goalOn(today.value)),
        )

    fun addTestSteps(amount: Long = 500L) {
        (repository as? FakeStepRepository)?.addSteps(amount)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    repository = AppContainer.stepRepository,
                    refresh = AppContainer.resumeTick,
                    settlementOf = { month ->
                        val familyId = AppContainer.sessionStore.session.value.familyId
                        if (familyId == null) flowOf(null)
                        else AppContainer.familyRepository.observeSettlement(familyId, month)
                    },
                    schedule = AppContainer.ruleSchedule,
                )
            }
        }
    }
}
