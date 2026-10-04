package io.github.lookie14.runcash.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.LocalDate

/**
 * 오늘 날짜를 내보내고, 자정이 지나 날짜가 바뀌면 새 날짜를 다시 내보낸다.
 * 수집하는 동안만 돌고, 날짜가 같으면 중복해서 내보내지 않는다.
 */
fun currentDateFlow(pollMillis: Long = 30_000L): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(pollMillis)
    }
}.distinctUntilChanged()
