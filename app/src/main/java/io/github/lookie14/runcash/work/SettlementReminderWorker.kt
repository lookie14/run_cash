package io.github.lookie14.runcash.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.lookie14.runcash.data.AppContainer
import io.github.lookie14.runcash.data.Role
import io.github.lookie14.runcash.domain.MonthSummary
import kotlinx.coroutines.CancellationException
import java.time.YearMonth

/**
 * 관리자 폰: 매일 오전 9시쯤 깨어나 지난달 정산이 남아 있으면 알림을 띄운다.
 * 매월 1일에 처음 뜨고, "보냈어요"를 누를 때까지 매일 다시 뜬다.
 */
class SettlementReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        AppContainer.init(applicationContext)
        val session = AppContainer.sessionStore.session.value
        val familyId = session.familyId
        if (session.role != Role.Grandson || familyId == null) return Result.success()

        val repository = AppContainer.familyRepository
        // 백그라운드에서 새 익명 계정을 만들지 않도록, 이미 로그인된 경우에만 확인한다.
        if (repository.currentUidOrNull() == null) return Result.success()
        val month = YearMonth.now().minusMonths(1)

        return try {
            if (repository.getSettlement(familyId, month) == null) {
                // 관리자가 정한 날짜별 규칙으로 계산해야 화면의 정산 금액과 같아진다.
                val summary = MonthSummary.of(repository.monthSteps(familyId, month), repository.getRules(familyId))
                if (summary.won > 0) Notifications.showSettlementReminder(applicationContext, month, summary.won)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
