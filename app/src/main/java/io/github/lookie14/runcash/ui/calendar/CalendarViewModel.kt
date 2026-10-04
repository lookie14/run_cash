package io.github.lookie14.runcash.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.StepRepository
import io.github.lookie14.runcash.data.currentDateFlow
import io.github.lookie14.runcash.domain.RuleSchedule
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
    /** 그날의 목표 걸음 (관리자가 바꿨으면 날짜마다 다를 수 있다). */
    val goal: Int = 5_000,
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

/**
 * 달력 화면 상태를 만든다. 사용자 달력과 관리자 달력이 같은 계산을 쓴다.
 * stepsOf: 날짜별 걸음 수 (미래 날짜는 묻지 않는다)
 */
internal fun buildCalendarState(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    schedule: RuleSchedule,
    stepsOf: (LocalDate) -> Long,
): CalendarUiState {
    val days = (1..month.lengthOfMonth()).map { day ->
        val date = month.atDay(day)
        val isFuture = date.isAfter(today)
        val steps = if (isFuture) 0L else stepsOf(date)
        val goal = schedule.goalOn(date)
        CalendarDay(
            date = date,
            steps = steps,
            won = if (isFuture) 0 else schedule.wonForDay(date, steps),
            goalReached = !isFuture && steps >= goal,
            isToday = date == today,
            isFuture = isFuture,
            goal = goal,
        )
    }
    val counted = days.filter { !it.isFuture }
    return CalendarUiState(
        month = month,
        days = days,
        selected = days.firstOrNull { it.date == selected },
        dailyGoal = schedule.goalOn(today),
        totalSteps = counted.sumOf { it.steps },
        goalDays = counted.count { it.goalReached },
        monthWon = counted.sumOf { it.won },
        canGoNext = month < YearMonth.from(today),
        isLoading = false,
    )
}

/** 불러온 달, 그 시점의 오늘 날짜, 그 달의 (오늘 이전) 날짜별 걸음 수. */
internal data class LoadedMonth(
    val month: YearMonth,
    val today: LocalDate,
    val steps: Map<LocalDate, Long>,
)

private data class CalendarInputs(val month: YearMonth, val selected: LocalDate?, val today: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val repository: StepRepository,
    private val todaySteps: Flow<Long> = repository.todaySteps(),
    dateFlow: Flow<LocalDate> = currentDateFlow(),
    refresh: Flow<Int> = flowOf(0),
    schedule: Flow<RuleSchedule> = flowOf(RuleSchedule()),
) : ViewModel() {

    /** 오늘 날짜. 자정이 지나면 저절로 바뀐다. */
    private val today: StateFlow<LocalDate> =
        dateFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    private val rules: StateFlow<RuleSchedule> =
        schedule.stateIn(viewModelScope, SharingStarted.Eagerly, RuleSchedule())

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

    private val inputs: Flow<CalendarInputs> =
        combine(month, selectedDate, today) { m, s, t -> CalendarInputs(m, s, t) }

    val uiState: StateFlow<CalendarUiState> =
        combine(inputs, loaded, todaySteps, rules) { input, loaded, todaySteps, schedule ->
            val (month, selected, today) = input
            if (loaded == null || loaded.month != month || loaded.today != today) {
                CalendarUiState(
                    month = month,
                    dailyGoal = schedule.goalOn(today),
                    canGoNext = month < YearMonth.from(today),
                )
            } else {
                buildCalendarState(month, today, selected, schedule) { date ->
                    if (date == today) todaySteps else loaded.steps[date] ?: 0L
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CalendarUiState(month = month.value),
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
                    todaySteps = AppContainer.liveTodaySteps(),
                    refresh = AppContainer.resumeTick,
                    schedule = AppContainer.ruleSchedule,
                )
            }
        }
    }
}
