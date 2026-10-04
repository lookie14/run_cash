package io.github.lookie14.runcash.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import kotlin.random.Random

/**
 * 에뮬레이터 개발용 가짜 걸음 데이터.
 * 지난 날짜는 날짜를 시드로 한 난수라서 실행할 때마다 같은 값이 나온다.
 */
class FakeStepRepository(
    initialTodaySteps: Long = 3_200L,
) : StepRepository {

    private val today = MutableStateFlow(initialTodaySteps)

    override val isFake: Boolean = true

    override fun todayStepsWithDate(): Flow<DaySteps> = today.map { DaySteps(LocalDate.now(), it) }

    override suspend fun readTodaySteps(): DaySteps = DaySteps(LocalDate.now(), today.value)

    fun addSteps(amount: Long) {
        today.update { it + amount }
    }

    override suspend fun readStepsBetween(start: LocalDate, endInclusive: LocalDate): Map<LocalDate, Long> =
        fillDays(start, endInclusive) { date -> Random(date.toEpochDay()).nextLong(1_500L, 9_000L) }
}
