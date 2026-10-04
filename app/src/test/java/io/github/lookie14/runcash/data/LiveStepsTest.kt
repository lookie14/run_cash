package io.github.lookie14.runcash.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LiveStepsTest {

    private val zone = ZoneOffset.ofHours(9)
    private val day = LocalDate.of(2026, 10, 15)
    private fun at(hour: Int, minute: Int = 0) =
        day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun addsOnlySensorStepsAfterLastHealthConnectRecord() {
        val log = SensorStepLog().apply {
            add(StepIncrement(at(9), 100))   // 9시: 이미 Health Connect에 들어간 걸음
            add(StepIncrement(at(11), 50))   // 11시: 아직 안 들어간 걸음
        }
        val hc = DaySteps(day, steps = 3_000, coveredUntilMillis = at(10))
        assertEquals(3_050, estimateTodaySteps(hc, log, zone))
    }

    @Test
    fun noDoubleCountAfterHealthConnectCatchesUp() {
        val log = SensorStepLog().apply { add(StepIncrement(at(11), 50)) }
        val synced = DaySteps(day, steps = 3_050, coveredUntilMillis = at(11, 30))
        assertEquals(3_050, estimateTodaySteps(synced, log, zone))
    }

    @Test
    fun withoutAnyRecordToday_countsSensorStepsSinceMidnight() {
        val log = SensorStepLog().apply {
            add(StepIncrement(day.minusDays(1).atTime(23, 50).atZone(zone).toInstant().toEpochMilli(), 999))
            add(StepIncrement(at(0, 10), 30))
        }
        assertEquals(30, estimateTodaySteps(DaySteps(day, 0), log, zone))
    }

    @Test
    fun ignoresSensorStepsAfterThatDay() {
        val log = SensorStepLog().apply {
            add(StepIncrement(day.plusDays(1).atTime(0, 5).atZone(zone).toInstant().toEpochMilli(), 40))
        }
        assertEquals(500, estimateTodaySteps(DaySteps(day, 500, at(23)), log, zone))
    }
}
