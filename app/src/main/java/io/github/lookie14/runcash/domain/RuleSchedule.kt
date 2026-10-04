package io.github.lookie14.runcash.domain

import java.time.LocalDate
import java.util.TreeMap

/**
 * 날짜별 용돈 규칙. 관리자가 목표 걸음이나 하루 최대 금액을 바꾸면 "그 다음 날부터" 새 규칙이 적용된다.
 * changes: 적용 시작일 -> 규칙. 가장 이른 변경 이전 날짜는 initial 규칙을 쓴다.
 * 지난날의 용돈은 그날의 규칙으로 계산되므로, 규칙을 바꿔도 이미 모인 금액은 그대로다.
 */
data class RuleSchedule(
    val changes: Map<LocalDate, PointRules> = emptyMap(),
    val initial: PointRules = PointRules(),
) {
    private val sorted = TreeMap(changes)

    fun rulesOn(date: LocalDate): PointRules = sorted.floorEntry(date)?.value ?: initial

    fun goalOn(date: LocalDate): Int = rulesOn(date).dailyGoal

    fun wonForDay(date: LocalDate, steps: Long): Int {
        val calculator = PointCalculator(rulesOn(date))
        return calculator.toWon(calculator.pointsForDay(steps))
    }

    fun wonForPeriod(days: Map<LocalDate, Long>): Int =
        days.entries.sumOf { (date, steps) -> wonForDay(date, steps) }
}
