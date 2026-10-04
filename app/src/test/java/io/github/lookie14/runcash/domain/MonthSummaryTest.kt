package io.github.lookie14.runcash.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MonthSummaryTest {

    private val start = LocalDate.of(2026, 10, 1)

    @Test
    fun summarizesStepsGoalDaysAndWon() {
        val days = mapOf(
            start to 6_000L,              // 목표 달성 -> 1,000원
            start.plusDays(1) to 2_500L,  // 500원
            start.plusDays(2) to 0L,      // 기록 없음 취급
        )
        val summary = MonthSummary.of(days)
        assertEquals(8_500L, summary.totalSteps)
        assertEquals(1, summary.goalDays)
        assertEquals(2, summary.recordedDays)
        assertEquals(1_500, summary.won)
    }

    @Test
    fun emptyMonth_isZero() {
        assertEquals(MonthSummary(0, 0, 0, 0), MonthSummary.of(emptyMap()))
    }
}
