package io.github.lookie14.runcash.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.StepRepository
import io.github.lookie14.runcash.data.currentDateFlow
import io.github.lookie14.runcash.domain.PointCalculator
import io.github.lookie14.runcash.domain.PointRules
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class CalendarDay(
    val date: LocalDate,
    val steps: Long,
    val won: Int,
    val goalReached: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
)

data class CalendarUiState(
    val month: YearMonth,
    val days: List<CalendarDay> = emptyList(),
    val selected: CalendarDay? = null,
    val dailyGoal: Int = 5_000,
    val totalSteps: Long = 0,
    val goalDays: Int = 0,
    val monthWon: Int = 0,
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
)

/** 불러온 달, 그 시점의 오늘 날짜, 그 달의 (오늘 이전) 날짜별 걸음 수. */
internal data class LoadedMonth(
    val month: YearMonth,
    val today: LocalDate,
    val steps: Map<LocalDate, Long>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: StepRepository,
    private val rules: PointRules = PointRules(),
    dateFlow: Flow<LocalDate> = currentDateFlow(),
    refresh: Flow<Int> = flowOf(0),
) : ViewModel() {

    private val calculator = PointCalculator(rules)

    /** 오늘 날짜. 자정이 지나면 저절로 바뀐다. */
    private val today: StateFlow<LocalDate> =
        dateFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    private val month = MutableStateFlow(YearMonth.from(LocalDate.now()))
    private val selectedDate = MutableStateFlow<LocalDate?>(LocalDate.now())

    private val loaded: StateFlow<LoadedMonth?> =
        combine(month, today, refresh) { m, t, _ -> m to t }
            .flatMapLatest { (m, t) ->
                flow {
                    val monthEnd = m.atEndOfMonth()
                    val end = if (monthEnd.isBefore(t)) monthEnd else t.minusDays(1)
                    emit(LoadedMonth(m, t, repository.stepsBetween(m.atDay(1), end)))
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<CalendarUiState> =
        combine(month, selectedDate, today, loaded, repository.todaySteps()) { month, selected, today, loaded, todaySteps ->
            val currentMonth = YearMonth.from(today)
            if (loaded == null || loaded.month != month || loaded.today != today) {
                return@combine CalendarUiState(
                    month = month,
                    dailyGoal = rules.dailyGoal,
                    canGoNext = month < currentMonth,
                )
            }
            val days = (1..month.lengthOfMonth()).map { day ->
                val date = month.atDay(day)
                val isFuture = date.isAfter(today)
                val steps = when {
                    isFuture -> 0L
                    date == today -> todaySteps
                    else -> loaded.steps[date] ?: 0L
                }
                CalendarDay(
                    date = date,
                    steps = steps,
                    won = calculator.toWon(calculator.pointsForDay(steps)),
                    goalReached = !isFuture && steps >= rules.dailyGoal,
                    isToday = date == today,
                    isFuture = isFuture,
                )
            }
            val counted = days.filter { !it.isFuture }
            CalendarUiState(
                month = month,
                days = days,
                selected = days.firstOrNull { it.date == selected },
                dailyGoal = rules.dailyGoal,
                totalSteps = counted.sumOf { it.steps },
                goalDays = counted.count { it.goalReached },
                monthWon = calculator.toWon(
                    calculator.pointsForPeriod(counted.associate { it.date to it.steps }),
                ),
                canGoNext = month < currentMonth,
                isLoading = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(month = month.value, dailyGoal = rules.dailyGoal),
        )

    fun previousMonth() = moveTo(month.value.minusMonths(1))

    fun nextMonth() {
        if (month.value < YearMonth.from(today.value)) moveTo(month.value.plusMonths(1))
    }

    private fun moveTo(target: YearMonth) {
        month.value = target
        selectedDate.value = if (target == YearMonth.from(today.value)) today.value else null
    }

    fun select(date: LocalDate) {
        if (!date.isAfter(today.value)) selectedDate.value = date
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                CalendarViewModel(
                    repository = AppContainer.stepRepository,
                    refresh = AppContainer.resumeTick,
                )
            }
        }
    }
}
