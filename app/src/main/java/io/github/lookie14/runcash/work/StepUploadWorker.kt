package io.github.lookie14.runcash.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.HealthConnectStepRepository
import io.github.lookie14.runcash.data.Role
import io.github.lookie14.runcash.data.StepSyncer

/**
 * 할머니 폰: 1시간마다 깨어나 걸음 수를 서버에 한 번 올리고 끝난다.
 * Health Connect가 백그라운드 읽기를 지원하지 않거나 권한이 없으면 아무것도 하지 않는다.
 * (그런 폰에서는 앱을 열 때 올라간다.)
 */
class StepUploadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppContainer.init(applicationContext)
        val session = AppContainer.sessionStore.session.value
        val familyId = session.familyId
        if (session.role != Role.Grandma || familyId == null) return Result.success()

        val repository = AppContainer.stepRepository
        if (repository is HealthConnectStepRepository && !repository.canReadInBackground()) {
            return Result.success()
        }

        StepSyncer(repository, AppContainer.familyRepository, AppContainer.sessionStore).syncOnce(familyId)
        return Result.success()
    }
}
