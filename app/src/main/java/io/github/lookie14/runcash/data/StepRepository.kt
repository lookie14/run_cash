package io.github.lookie14.runcash.data

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 걸음 수를 가져오는 통로.
 * 개발 중에는 FakeStepRepository, 나중에는 Health Connect 구현으로 교체한다.
 */
interface StepRepository {

    /** 오늘 걸음 수. 값이 바뀔 때마다 새로 내보낸다. */
    fun todaySteps(): Flow<Long>

    /** start부터 endInclusive까지 날짜별 걸음 수. start가 더 늦으면 빈 맵을 돌려준다. */
    suspend fun stepsBetween(start: LocalDate, endInclusive: LocalDate): Map<LocalDate, Long>

    /** 개발용 가짜 데이터인지 여부. 테스트 버튼을 보여줄지 결정할 때 쓴다. */
    val isFake: Boolean get() = false
}
