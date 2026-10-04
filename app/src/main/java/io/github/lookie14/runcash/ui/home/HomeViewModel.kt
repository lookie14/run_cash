package io.github.lookie14.runcash.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.FakeStepRepository
import io.github.lookie14.runcash.data.StepRepository
import io.github.lookie14.runcash.data.currentDateFlow
import io.github.lookie14.runcash.domain.PointCalculator
import io.github.lookie14.runcash.domain.PointRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUiState(
    val date: LocalDate = LocalDate.now(),
    val todaySteps: Long = 0,
    val dailyGoal: Int = 5_000,
    val todayPoints: Int = 0,
    val monthPoints: Int = 0,
    val todayWon: Int = 0,
    val monthWon: Int = 0,
    val isLoading: Boolean = true,
) {
    val goalReached: Boolean get() = todaySteps >= dailyGoal
    val stepsLeft: Long get() = (dailyGoal - todaySteps).coerceAtLeast(0)
    val progress: Float get() = (todaySteps.toFloat() / dailyGoal).coerceIn(0f, 1f)
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: StepRepository,
    private val rules: PointRules = PointRules(),
    dateFlow: Flow<LocalDate> = currentDateFlow(),
    /** 값이 바뀔 때마다 지난 기록을 다시 읽는다. (앱이 화면으로 돌아올 때 등) */
    refresh: Flow<Int> = flowOf(0),
) : ViewModel() {

    private val calculator = PointCalculator(rules)

    /** 오늘 날짜. 자정이 지나면 저절로 바뀐다. */
    private val today: StateFlow<LocalDate> =
        dateFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    /** 이번 달 1일부터 어제까지의 기록과 그 기준 날짜. 불러오기 전에는 null. */
    private val pastDays: StateFlow<Pair<LocalDate, Map<LocalDate, Long>>?> =
        combine(today, refresh) { date, _ -> date }
            .flatMapLatest { date ->
                flow {
                    emit(date to repository.stepsBetween(date.withDayOfMonth(1), date.minusDays(1)))
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val showTestControls: Boolean = repository.isFake

    val uiState: StateFlow<HomeUiState> =
        combine(today, repository.todaySteps(), pastDays) { date, todaySteps, past ->
            if (past == null || past.first != date) {
                HomeUiState(date = date, dailyGoal = rules.dailyGoal, todaySteps = todaySteps)
            } else {
                val todayPoints = calculator.pointsForDay(todaySteps)
                val monthPoints = calculator.pointsForPeriod(past.second + (date to todaySteps))
                HomeUiState(
                    date = date,
                    todaySteps = todaySteps,
                    dailyGoal = rules.dailyGoal,
                    todayPoints = todayPoints,
                    monthPoints = monthPoints,
                    todayWon = calculator.toWon(todayPoints),
                    monthWon = calculator.toWon(monthPoints),
                    isLoading = false,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(date = today.value, dailyGoal = rules.dailyGoal),
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
                )
            }
        }
    }
}
