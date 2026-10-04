package io.github.lookie14.runcash.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override fun todaySteps(): Flow<Long> = today.asStateFlow()

    fun addSteps(amount: Long) {
        today.update { it + amount }
    }

    override suspend fun stepsBetween(
        start: LocalDate,
        endInclusive: LocalDate,
    ): Map<LocalDate, Long> {
        val result = linkedMapOf<LocalDate, Long>()
        var date = start
        while (!date.isAfter(endInclusive)) {
            result[date] = Random(date.toEpochDay()).nextLong(1_500L, 9_000L)
            date = date.plusDays(1)
        }
        return result
    }
}
