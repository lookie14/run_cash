package io.github.lookie14.runcash.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.lookie14.runcash.MainActivity
import io.github.lookie14.runcash.R
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

object Notifications {
    private const val SETTLEMENT_CHANNEL = "settlement"
    private const val SETTLEMENT_ID = 1001

    fun showSettlementReminder(context: Context, month: YearMonth, won: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(SETTLEMENT_CHANNEL, "월말 정산", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "지난달 용돈을 보내야 할 때 알려줘요"
            },
        )

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val amount = NumberFormat.getNumberInstance(Locale.KOREA).format(won)
        val text = "${amount}원을 보내 주세요. 보낸 뒤 앱에서 \"보냈어요\"를 눌러 주세요."

        val notification = NotificationCompat.Builder(context, SETTLEMENT_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${month.monthValue}월 용돈 정산")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(SETTLEMENT_ID, notification)
        } catch (e: SecurityException) {
            // 알림 권한이 막 꺼진 경우. 다음 날 다시 시도한다.
        }
    }
}
