package io.github.lookie14.runcash.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** 역할에 맞는 백그라운드 작업을 켜고 끈다. 이미 예약돼 있으면 그대로 둔다(KEEP). */
object WorkScheduler {
    private const val STEP_UPLOAD = "step-upload"
    private const val SETTLEMENT_REMINDER = "settlement-reminder"
    private const val REMINDER_HOUR = 9

    private val needsNetwork = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleStepUpload(context: Context) {
        val request = PeriodicWorkRequestBuilder<StepUploadWorker>(1, TimeUnit.HOURS)
            .setConstraints(needsNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(STEP_UPLOAD, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelStepUpload(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(STEP_UPLOAD)
    }

    /** 매일 오전 9시쯤. 첫 실행을 다음 9시에 맞춘다. */
    fun scheduleSettlementReminder(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(REMINDER_HOUR, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val request = PeriodicWorkRequestBuilder<SettlementReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(needsNetwork)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(SETTLEMENT_REMINDER, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancelSettlementReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SETTLEMENT_REMINDER)
    }
}
