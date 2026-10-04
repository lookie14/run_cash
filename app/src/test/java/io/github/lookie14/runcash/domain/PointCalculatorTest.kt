package io.github.lookie14.runcash.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PointCalculatorTest {

    private val calculator = PointCalculator(PointRules())
    private val start = LocalDate.of(2026, 10, 1)

    @Test
    fun zeroOrNegativeSteps_giveNoPoints() {
        assertEquals(0, calculator.pointsForDay(0))
        assertEquals(0, calculator.pointsForDay(-100))
    }

    @Test
    fun belowGoal_accumulatesInProportion() {
        assertEquals(500, calculator.pointsForDay(2_500))
        assertEquals(799, calculator.pointsForDay(3_999))
    }

    @Test
    fun reachingGoal_givesDailyMax() {
        assertEquals(1_000, calculator.pointsForDay(5_000))
    }

    @Test
    fun dailyPoints_areCappedAtMax() {
        assertEquals(1_000, calculator.pointsForDay(30_000))
    }

    @Test
    fun period_sumsDailyPoints() {
        val days = mapOf(
            start to 5_000L,
            start.plusDays(1) to 2_500L,
            start.plusDays(2) to 0L,
        )
        assertEquals(1_500, calculator.pointsForPeriod(days))
    }

    @Test
    fun monthlyCap_limitsTotal() {
        val capped = PointCalculator(PointRules(monthlyCapPoints = 2_500))
        val days = (0L until 7L).associate { start.plusDays(it) to 5_000L }
        assertEquals(2_500, capped.pointsForPeriod(days))
    }

    @Test
    fun onePoint_isOneWon() {
        assertEquals(1_000, calculator.toWon(1_000))
    }
}
