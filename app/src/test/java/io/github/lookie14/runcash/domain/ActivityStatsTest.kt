package io.github.lookie14.runcash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ActivityStatsTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val schedule = RuleSchedule(initial = PointRules(dailyGoal = 5_000))

    private fun days(vararg pairs: Pair<Long, Long>): Map<LocalDate, Long> =
        pairs.associate { (back, steps) -> today.minusDays(back) to steps }

    @Test
    fun lastSevenDays_runsOldestToToday() {
        val stats = ActivityStats.of(today, days(0L to 100L, 6L to 600L), schedule)
        assertEquals(7, stats.lastSevenDays.size)
        assertEquals(today.minusDays(6), stats.lastSevenDays.first().date)
        assertEquals(600L, stats.lastSevenDays.first().steps)
        assertTrue(stats.lastSevenDays.last().isToday)
        assertEquals(100L, stats.lastSevenDays.last().steps)
    }

    @Test
    fun streak_keepsYesterdayRunWhenTodayNotYetReached() {
        val stats = ActivityStats.of(today, days(0L to 1_000L, 1L to 5_000L, 2L to 6_000L, 3L to 100L), schedule)
        assertEquals(2, stats.streakDays)
    }

    @Test
    fun streak_includesTodayWhenReached() {
        val stats = ActivityStats.of(today, days(0L to 5_000L, 1L to 5_000L), schedule)
        assertEquals(2, stats.streakDays)
    }

    @Test
    fun streak_restartsWhenYesterdayMissed() {
        val stats = ActivityStats.of(today, days(0L to 9_000L, 1L to 0L, 2L to 9_000L), schedule)
        assertEquals(1, stats.streakDays)
    }

    @Test
    fun weeklyAverages_excludeToday() {
        val recent = (1L..7L).map { it to 7_000L }
        val previous = (8L..14L).map { it to 3_500L }
        val stats = ActivityStats.of(today, days(0L to 99_999L, *(recent + previous).toTypedArray()), schedule)
        assertEquals(7_000L, stats.recentWeekAvg)
        assertEquals(3_500L, stats.previousWeekAvg)
    }
}

class ActivityStatsGoalChangeTest {
    @Test
    fun streak_usesEachDaysOwnGoal() {
        val today = LocalDate.of(2026, 10, 15)
        // 10/14부터 목표가 8,000으로 올랐다. 10/13은 5,000 기준으로 달성, 10/14는 8,000 기준으로 미달.
        val schedule = RuleSchedule(changes = mapOf(LocalDate.of(2026, 10, 14) to PointRules(dailyGoal = 8_000)))
        val steps = mapOf(
            today to 8_000L,
            today.minusDays(1) to 6_000L,
            today.minusDays(2) to 6_000L,
        )
        assertEquals(1, ActivityStats.of(today, steps, schedule).streakDays)
    }
}
