package io.github.lookie14.runcash.domain

import java.time.LocalDate

/** 한 달 기록을 요약한다. 관리자 화면, 정산, 알림이 모두 같은 계산을 쓰도록 한 곳에 둔다. */
data class MonthSummary(
    val totalSteps: Long,
    val goalDays: Int,
    val recordedDays: Int,
    val won: Int,
) {
    companion object {
        fun of(days: Map<LocalDate, Long>, schedule: RuleSchedule = RuleSchedule()): MonthSummary =
            MonthSummary(
                totalSteps = days.values.sum(),
                goalDays = days.count { (date, steps) -> steps >= schedule.goalOn(date) },
                recordedDays = days.count { it.value > 0 },
                won = schedule.wonForPeriod(days),
            )
    }
}
