package io.github.lookie14.runcash.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PointCalculatorTest {

    private val calculator = PointCalculator(PointRules())
    private val start = LocalDate.of(2026, 10, 1)

    @Test
    fun belowGoal_getsOnlyBasePoints() {
        assertEquals(3, calculator.pointsForDay(3_999))
    }

    @Test
    fun reachingGoal_addsBonus() {
        assertEquals(5 + 3, calculator.pointsForDay(5_000))
    }

    @Test
    fun dailyPoints_areCapped() {
        assertEquals(15, calculator.pointsForDay(30_000))
    }

    @Test
    fun sevenDayStreak_addsStreakBonus() {
        val days = (0L until 7L).associate { start.plusDays(it) to 5_000L }
        assertEquals(7 * 8 + 10, calculator.pointsForPeriod(days))
    }

    @Test
    fun missingDay_breaksStreak() {
        val days = (0L until 6L).associate { start.plusDays(it) to 5_000L } +
            (start.plusDays(7) to 5_000L)
        assertEquals(7 * 8, calculator.pointsForPeriod(days))
    }

    @Test
    fun monthlyCap_limitsTotal() {
        val capped = PointCalculator(PointRules(monthlyCapPoints = 20))
        val days = (0L until 7L).associate { start.plusDays(it) to 5_000L }
        assertEquals(20, capped.pointsForPeriod(days))
    }
}
