package io.github.lookie14.runcash.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RuleScheduleTest {

    private val changeDay = LocalDate.of(2026, 10, 10)
    private val schedule = RuleSchedule(
        changes = mapOf(changeDay to PointRules(dailyGoal = 8_000, dailyMaxPoints = 2_000)),
        initial = PointRules(dailyGoal = 5_000, dailyMaxPoints = 1_000),
    )

    @Test
    fun beforeChange_usesInitialRules() {
        assertEquals(5_000, schedule.goalOn(changeDay.minusDays(1)))
        assertEquals(1_000, schedule.wonForDay(changeDay.minusDays(1), 5_000))
    }

    @Test
    fun fromChangeDay_usesNewRules() {
        assertEquals(8_000, schedule.goalOn(changeDay))
        assertEquals(1_000, schedule.wonForDay(changeDay, 4_000))   // 4,000/8,000 x 2,000원
        assertEquals(2_000, schedule.wonForDay(changeDay.plusDays(5), 9_000))
    }

    @Test
    fun period_appliesEachDaysRules() {
        val days = mapOf(changeDay.minusDays(1) to 5_000L, changeDay to 8_000L)
        assertEquals(3_000, schedule.wonForPeriod(days))
    }

    @Test
    fun monthSummary_countsGoalDaysPerDay() {
        val days = mapOf(changeDay.minusDays(1) to 6_000L, changeDay to 6_000L)
        val summary = MonthSummary.of(days, schedule)
        assertEquals(1, summary.goalDays)
        assertEquals(1_000 + 1_500, summary.won)
    }
}
