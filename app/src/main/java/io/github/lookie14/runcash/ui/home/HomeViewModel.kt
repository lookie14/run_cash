package io.github.lookie14.runcash.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lookie14.runcash.data.FakeStepRepository
import io.github.lookie14.runcash.data.StepRepository
import io.github.lookie14.runcash.domain.PointCalculator
import io.github.lookie14.runcash.domain.PointRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

class HomeViewModel(
    private val repository: StepRepository,
    private val rules: PointRules = PointRules(),
    private val today: LocalDate = LocalDate.now(),
) : ViewModel() {

    private val calculator = PointCalculator(rules)

    /** 이번 달 1일부터 어제까지의 기록. 불러오기 전에는 null. */
    private val pastDaysThisMonth = MutableStateFlow<Map<LocalDate, Long>?>(null)

    val showTestControls: Boolean = repository.isFake

    init {
        viewModelScope.launch {
            pastDaysThisMonth.value = repository.stepsBetween(
                start = today.withDayOfMonth(1),
                endInclusive = today.minusDays(1),
            )
        }
    }

    val uiState: StateFlow<HomeUiState> =
        combine(repository.todaySteps(), pastDaysThisMonth) { todaySteps, pastDays ->
            if (pastDays == null) {
                HomeUiState(date = today, dailyGoal = rules.dailyGoal, todaySteps = todaySteps)
            } else {
                val todayPoints = calculator.pointsForDay(todaySteps)
                val monthPoints = calculator.pointsForPeriod(pastDays + (today to todaySteps))
                HomeUiState(
                    date = today,
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
            initialValue = HomeUiState(date = today, dailyGoal = rules.dailyGoal),
        )

    fun addTestSteps(amount: Long = 500L) {
        (repository as? FakeStepRepository)?.addSteps(amount)
    }

    companion object {
        // Health Connect 구현이 준비되면 여기만 바꾸면 된다.
        val Factory = viewModelFactory {
            initializer { HomeViewModel(FakeStepRepository()) }
        }
    }
}
