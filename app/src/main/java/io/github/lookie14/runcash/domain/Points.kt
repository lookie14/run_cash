package io.github.lookie14.runcash.domain

import java.time.LocalDate

/**
 * 포인트 규칙. 1포인트 = 1원이다.
 * 나중에 승현 관리 화면(Firestore config)에서 바꿀 수 있게 한다.
 */
data class PointRules(
    val dailyGoal: Int = 5_000,         // 일일 목표 걸음 수
    val dailyMaxPoints: Int = 1_000,    // 하루 최대 포인트 (목표 달성 시 이 값)
    val wonPerPoint: Int = 1,           // 1포인트 = 1원
    val monthlyCapPoints: Int? = null,  // 월 예산 상한 (null이면 제한 없음)
)

class PointCalculator(private val rules: PointRules) {

    /** 목표 걸음 수에 가까워질수록 비례해서 쌓이고, 목표를 채우면 하루 최대치. */
    fun pointsForDay(steps: Long): Int {
        if (steps <= 0) return 0
        if (steps >= rules.dailyGoal) return rules.dailyMaxPoints
        return (steps * rules.dailyMaxPoints / rules.dailyGoal).toInt()
    }

    /** 기간 전체 포인트. 월 상한이 있으면 그 이상은 쌓이지 않는다. */
    fun pointsForPeriod(daily: Map<LocalDate, Long>): Int {
        val total = daily.values.sumOf { pointsForDay(it) }
        return rules.monthlyCapPoints?.let { minOf(total, it) } ?: total
    }

    fun toWon(points: Int): Int = points * rules.wonPerPoint
}
