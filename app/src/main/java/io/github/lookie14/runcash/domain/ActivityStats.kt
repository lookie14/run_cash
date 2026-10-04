package io.github.lookie14.runcash.domain

import java.time.LocalDate

/** 막대 그래프 한 칸. */
data class DayBar(val date: LocalDate, val steps: Long, val isToday: Boolean)

/**
 * 관리자 화면 모니터링용 계산.
 *  - lastSevenDays: 오늘 포함 최근 7일 (오래된 날부터)
 *  - streakDays: 연속 목표 달성 일수(그날의 목표 기준). 오늘은 아직 진행 중이라, 오늘 못 채웠어도 어제까지의 연속은 유지한다.
 *  - recentWeekAvg / previousWeekAvg: 어제까지 7일 평균 vs 그 전 7일 평균 (오늘은 진행 중이라 뺀다)
 */
data class ActivityStats(
    val lastSevenDays: List<DayBar>,
    val streakDays: Int,
    val recentWeekAvg: Long,
    val previousWeekAvg: Long,
) {
    companion object {
        fun of(today: LocalDate, steps: Map<LocalDate, Long>, schedule: RuleSchedule): ActivityStats {
            fun stepsOn(date: LocalDate) = steps[date] ?: 0L

            val bars = (6 downTo 0).map { back ->
                val date = today.minusDays(back.toLong())
                DayBar(date, stepsOn(date), isToday = back == 0)
            }

            fun reached(date: LocalDate) = stepsOn(date) >= schedule.goalOn(date)

            var streak = if (reached(today)) 1 else 0
            var date = today.minusDays(1)
            while (reached(date)) {
                streak++
                date = date.minusDays(1)
            }

            val recent = (1L..7L).sumOf { stepsOn(today.minusDays(it)) } / 7
            val previous = (8L..14L).sumOf { stepsOn(today.minusDays(it)) } / 7

            return ActivityStats(bars, streak, recent, previous)
        }
    }
}
