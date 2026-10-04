package io.github.lookie14.runcash.domain

import java.time.LocalDate

/** 포인트 규칙. 나중에 승현 관리 화면(Firestore config)에서 바꿀 수 있게 한다. */
data class PointRules(
    val stepsPerPoint: Int = 1_000,     // 1,000보당 1포인트
    val dailyGoal: Int = 5_000,         // 일일 목표
    val goalBonus: Int = 3,             // 목표 달성 보너스
    val dailyCap: Int = 15,             // 하루 최대 포인트 (무리 방지)
    val streakDays: Int = 7,            // 연속 달성 기준 일수
    val streakBonus: Int = 10,          // 연속 달성 보너스
    val wonPerPoint: Int = 100,         // 1포인트 = 100원
    val monthlyCapPoints: Int? = null,  // 월 예산 상한 (null이면 제한 없음)
)

class PointCalculator(private val rules: PointRules) {

    fun pointsForDay(steps: Long): Int {
        if (steps <= 0) return 0
        val base = (steps / rules.stepsPerPoint).toInt()
        val bonus = if (steps >= rules.dailyGoal) rules.goalBonus else 0
        return minOf(base + bonus, rules.dailyCap)
    }

    /** 기간 전체 포인트. 날짜가 하루라도 비거나 목표에 못 미치면 연속 기록이 끊긴다. */
    fun pointsForPeriod(daily: Map<LocalDate, Long>): Int {
        var total = 0
        var streak = 0
        var previous: LocalDate? = null

        for ((date, steps) in daily.toSortedMap()) {
            total += pointsForDay(steps)

            val continues = previous != null && previous.plusDays(1) == date
            streak = when {
                steps < rules.dailyGoal -> 0
                continues -> streak + 1
                else -> 1
            }
            if (streak > 0 && streak % rules.streakDays == 0) {
                total += rules.streakBonus
            }
            previous = date
        }

        return rules.monthlyCapPoints?.let { minOf(total, it) } ?: total
    }

    fun toWon(points: Int): Int = points * rules.wonPerPoint
}
