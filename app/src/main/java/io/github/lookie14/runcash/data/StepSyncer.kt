package io.github.lookie14.runcash.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * 할머니 폰의 걸음 수를 서버(families/{id}/daily/{날짜})에 올린다.
 *  - 오늘 값: 바뀔 때마다 올리되, 올린 뒤 잠깐 쉰다. 값을 "읽은 날짜"로 올리므로 자정 무렵에도 날짜가 섞이지 않는다.
 *  - 지난 날짜: 앱이 화면으로 돌아올 때마다, 마지막으로 다 올린 날짜부터 어제까지 다시 올린다.
 *    며칠 동안 앱을 안 열었어도 빠진 날이 채워진다. 늦게 동기화된 값을 맞추려고 최근 이틀은 항상 다시 올린다.
 *  - 0걸음은 올리지 않는다. 기록이 없는 날은 서버에서 0걸음으로 취급한다.
 */
class StepSyncer(
    private val steps: StepRepository,
    private val family: FamilyRepository,
    private val store: SessionStore,
) {
    suspend fun run(familyId: String, refresh: Flow<Int>) = coroutineScope {
        launch {
            steps.todayStepsWithDate().conflate().collect { day ->
                push(familyId, day.date, day.steps)
                delay(TODAY_THROTTLE_MILLIS)
            }
        }
        launch {
            refresh.collect { syncPastDays(familyId) }
        }
    }

    private suspend fun syncPastDays(familyId: String) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val earliest = today.minusDays(MAX_BACKFILL_DAYS)
        val recent = today.minusDays(RESYNC_DAYS)
        val watermark = store.lastSyncedDate

        val start = when {
            watermark == null -> earliest
            watermark.isBefore(earliest) -> earliest
            watermark.isBefore(recent) -> watermark
            else -> recent
        }

        // 읽기에 실패하면(권한 없음, 백그라운드 등) 아무것도 하지 않고 다음 기회에 다시 한다.
        val days = try {
            steps.readStepsBetween(start, yesterday)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }

        var allOk = true
        for ((date, value) in days) {
            if (!push(familyId, date, value)) allOk = false
        }
        if (allOk) store.lastSyncedDate = yesterday
    }

    /** 성공했거나 올릴 필요가 없으면 true. */
    private suspend fun push(familyId: String, date: LocalDate, value: Long): Boolean {
        if (value <= 0L) return true
        return try {
            // 오프라인이면 쓰기가 기기에 저장됐다가 연결되면 올라간다. 그래서 시간 초과도 성공으로 본다.
            withTimeoutOrNull(UPLOAD_TIMEOUT_MILLIS) { family.uploadDaily(familyId, date, value) }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val TODAY_THROTTLE_MILLIS = 30_000L
        const val UPLOAD_TIMEOUT_MILLIS = 20_000L
        /** 처음 연결했거나 오래 안 열었을 때 거슬러 올라가는 최대 일수. */
        const val MAX_BACKFILL_DAYS = 35L
        /** 늦게 동기화되는 값을 맞추려고 항상 다시 올리는 최근 일수. */
        const val RESYNC_DAYS = 2L
    }
}
