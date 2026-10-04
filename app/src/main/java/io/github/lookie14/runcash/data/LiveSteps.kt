package io.github.lookie14.runcash.data

import java.time.ZoneId

/** 폰 걸음 센서가 센 걸음 조각. atMillis에 steps걸음이 늘었다. */
data class StepIncrement(val atMillis: Long, val steps: Long)

/**
 * 앱이 켜져 있는 동안 센서가 센 걸음 기록.
 * 센서 등록을 새로 할 때마다 첫 값은 기준으로만 쓰고(0걸음), 이후 늘어난 만큼만 그 시각에 기록한다.
 * 그래서 앱이 꺼져 있던 동안 걸은 걸음이 다시 켠 시각에 몰려 들어오는 일이 없다.
 */
class SensorStepLog(private val keepMillis: Long = 2 * 24 * 60 * 60 * 1000L) {
    private val items = ArrayDeque<StepIncrement>()

    fun add(increment: StepIncrement) {
        if (increment.steps > 0) items.addLast(increment)
        while (items.isNotEmpty() && items.first().atMillis < increment.atMillis - keepMillis) {
            items.removeFirst()
        }
    }

    /** fromExclusive 초과 ~ toInclusive 이하 시각에 센 걸음 합. */
    fun stepsBetween(fromExclusive: Long, toInclusive: Long): Long =
        items.filter { it.atMillis > fromExclusive && it.atMillis <= toInclusive }.sumOf { it.steps }
}

/**
 * 화면에 보여줄 오늘 걸음 = Health Connect 값 + 그 값에 아직 안 들어온(마지막 기록 이후) 센서 걸음.
 * 삼성헬스가 나중에 같은 걸음을 넘겨주면 coveredUntil이 그만큼 뒤로 가므로 두 번 세지 않는다.
 * 그날(hc.date)에 속한 센서 걸음만 더한다.
 */
fun estimateTodaySteps(hc: DaySteps, log: SensorStepLog, zone: ZoneId = ZoneId.systemDefault()): Long {
    val dayStart = hc.date.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = hc.date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val from = maxOf(hc.coveredUntilMillis ?: dayStart, dayStart)
    return hc.steps + log.stepsBetween(from, dayEnd - 1)
}
