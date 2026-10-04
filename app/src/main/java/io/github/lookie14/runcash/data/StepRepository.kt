package io.github.lookie14.runcash.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** 어느 날짜의 걸음 수인지 함께 들고 다닌다. 자정 무렵 날짜가 섞이지 않게 하기 위해서다. */
data class DaySteps(val date: LocalDate, val steps: Long)

/**
 * 걸음 수를 가져오는 통로.
 * 디버그 빌드는 FakeStepRepository, 릴리스 빌드는 Health Connect 구현을 쓴다.
 */
interface StepRepository {

    /** 오늘 걸음 수와 그 값을 읽은 날짜. 값이 바뀔 때마다 새로 내보낸다. */
    fun todayStepsWithDate(): Flow<DaySteps>

    /** 지금 오늘 걸음 수를 한 번 읽는다. 실패하면 예외를 던진다. (백그라운드 업로드용) */
    suspend fun readTodaySteps(): DaySteps

    /** 화면용: 오늘 걸음 수만. */
    fun todaySteps(): Flow<Long> = todayStepsWithDate().map { it.steps }

    /**
     * start부터 endInclusive까지 날짜별 걸음 수. 읽기에 실패하면 예외를 던진다.
     * 서버 업로드처럼 "실패"와 "0걸음"을 구분해야 하는 곳에서 쓴다.
     */
    suspend fun readStepsBetween(start: LocalDate, endInclusive: LocalDate): Map<LocalDate, Long>

    /** 화면용: 읽기에 실패하면 0걸음으로 채운다. start가 더 늦으면 빈 맵을 돌려준다. */
    suspend fun stepsBetween(start: LocalDate, endInclusive: LocalDate): Map<LocalDate, Long> =
        try {
            readStepsBetween(start, endInclusive)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fillDays(start, endInclusive) { 0L }
        }

    /** 개발용 가짜 데이터인지 여부. 테스트 버튼을 보여줄지 결정할 때 쓴다. */
    val isFake: Boolean get() = false
}

/** start부터 endInclusive까지 모든 날짜를 채운 맵. start가 더 늦으면 빈 맵. */
internal inline fun fillDays(
    start: LocalDate,
    endInclusive: LocalDate,
    value: (LocalDate) -> Long,
): Map<LocalDate, Long> {
    val result = linkedMapOf<LocalDate, Long>()
    var date = start
    while (!date.isAfter(endInclusive)) {
        result[date] = value(date)
        date = date.plusDays(1)
    }
    return result
}
