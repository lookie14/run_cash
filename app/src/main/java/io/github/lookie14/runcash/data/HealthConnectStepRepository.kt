package io.github.lookie14.runcash.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

/** Health Connect를 쓸 수 있는 상태. */
enum class HealthStatus {
    Checking,
    Ready,
    NeedsPermission,
    /** Health Connect 앱이 없거나 업데이트가 필요하다. */
    NeedsInstall,
    /** 이 폰은 Health Connect를 지원하지 않는다. */
    Unsupported,
    /** 상태를 확인하다 오류가 났다. 다시 시도할 수 있다. */
    Error,
}

/**
 * 삼성헬스 -> Health Connect에 동기화된 걸음 수를 읽는다.
 * 여러 앱이 같은 걸음을 기록해도 aggregate가 중복을 걸러 준다.
 * Health Connect는 기본적으로 앱이 화면에 떠 있을 때만 읽기를 허용한다.
 */
class HealthConnectStepRepository(private val context: Context) : StepRepository {

    val requiredPermissions: Set<String> =
        setOf(HealthPermission.getReadPermission(StepsRecord::class))

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    /** 예외를 던지지 않는다. 오류가 나면 Error를 돌려준다. */
    suspend fun checkStatus(): HealthStatus =
        try {
            when (HealthConnectClient.getSdkStatus(context)) {
                HealthConnectClient.SDK_UNAVAILABLE -> HealthStatus.Unsupported
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NeedsInstall
                else -> {
                    val granted = client.permissionController.getGrantedPermissions()
                    if (granted.containsAll(requiredPermissions)) HealthStatus.Ready else HealthStatus.NeedsPermission
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthStatus.Error
        }

    /** 일정 간격으로 다시 읽는다. 값에는 읽은 날짜가 붙어 있어서 자정이 지나도 날짜가 섞이지 않는다. */
    override fun todayStepsWithDate(): Flow<DaySteps> = flow {
        var last: DaySteps? = null
        while (true) {
            val now = LocalDateTime.now()
            val date = now.toLocalDate()
            val read = try {
                DaySteps(date, readSteps(date.atStartOfDay(), now))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 일시적 오류면 직전 값을 유지한다. 단, 날짜가 바뀌었으면 어제 값을 쓰지 않는다.
                last?.takeIf { it.date == date }
            }
            emit(read ?: DaySteps(date, 0L))
            if (read != null) last = read
            delay(POLL_MILLIS)
        }
    }.distinctUntilChanged()

    override suspend fun readStepsBetween(start: LocalDate, endInclusive: LocalDate): Map<LocalDate, Long> {
        if (start.isAfter(endInclusive)) return emptyMap()

        val byDate = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(
                    start.atStartOfDay(),
                    endInclusive.plusDays(1).atStartOfDay(),
                ),
                timeRangeSlicer = Period.ofDays(1),
            ),
        ).associate { it.startTime.toLocalDate() to (it.result[StepsRecord.COUNT_TOTAL] ?: 0L) }

        return fillDays(start, endInclusive) { byDate[it] ?: 0L }
    }

    private suspend fun readSteps(start: LocalDateTime, end: LocalDateTime): Long =
        client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end),
            ),
        )[StepsRecord.COUNT_TOTAL] ?: 0L

    private companion object {
        const val POLL_MILLIS = 20_000L
    }
}
