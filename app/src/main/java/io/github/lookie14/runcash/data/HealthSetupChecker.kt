package io.github.lookie14.runcash.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.Instant

/** 처음 설정 화면에서 보여줄 항목별 상태. */
data class SetupStatus(
    /** Health Connect 자체: Ready(쓸 수 있음) / NeedsInstall / Unsupported / Error */
    val health: HealthStatus,
    val readGranted: Boolean = false,
    val backgroundSupported: Boolean = false,
    val backgroundGranted: Boolean = false,
    /** 최근 7일 안에 Health Connect로 걸음을 보낸 앱 이름. 없으면 null. */
    val stepSourceName: String? = null,
    val stepSourceIsSamsung: Boolean = false,
    val samsungInstalled: Boolean = false,
    val batteryExempt: Boolean = false,
    val sensorAvailable: Boolean = false,
    val activityGranted: Boolean = false,
) {
    /** 이것만 되면 앱을 쓸 수 있다. 나머지는 권장. */
    val requiredDone: Boolean get() = health == HealthStatus.Ready && readGranted
}

/**
 * 사용자 폰의 설정 상태를 확인한다. 다른 앱(삼성헬스)의 설정을 대신 켤 수는 없으므로,
 * 상태를 확인해서 무엇이 남았는지 보여주고 해당 화면으로 바로 보내는 데 쓴다.
 */
class HealthSetupChecker(private val context: Context) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    val readPermission: String = HealthPermission.getReadPermission(StepsRecord::class)
    val backgroundPermission: String = HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    private fun sdkStatus(): HealthStatus =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_UNAVAILABLE -> HealthStatus.Unsupported
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthStatus.NeedsInstall
            else -> HealthStatus.Ready
        }

    private fun backgroundSupported(): Boolean =
        try {
            client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        } catch (e: Exception) {
            false
        }

    /** 허용 창에서 한 번에 물을 권한. 지원하면 백그라운드 읽기도 함께. */
    fun permissionsToRequest(): Set<String> =
        if (sdkStatus() == HealthStatus.Ready && backgroundSupported()) {
            setOf(readPermission, backgroundPermission)
        } else {
            setOf(readPermission)
        }

    suspend fun check(): SetupStatus {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val base = SetupStatus(
            health = sdkStatus(),
            samsungInstalled = context.packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH) != null,
            batteryExempt = context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true,
            sensorAvailable = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null,
            activityGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED,
        )
        if (base.health != HealthStatus.Ready) return base

        return try {
            val granted = client.permissionController.getGrantedPermissions()
            val read = readPermission in granted
            val bgSupported = backgroundSupported()

            // 최근 7일 안에 어떤 앱이 걸음을 보냈는지 (읽기 권한이 있어야 확인할 수 있다)
            val sourcePackage = if (read) {
                client.readRecords(
                    ReadRecordsRequest(
                        recordType = StepsRecord::class,
                        timeRangeFilter = TimeRangeFilter.after(Instant.now().minus(Duration.ofDays(7))),
                        ascendingOrder = false,
                        pageSize = 1,
                    ),
                ).records.firstOrNull()?.metadata?.dataOrigin?.packageName
            } else {
                null
            }

            base.copy(
                readGranted = read,
                backgroundSupported = bgSupported,
                backgroundGranted = bgSupported && backgroundPermission in granted,
                stepSourceName = sourcePackage?.let(::appLabel),
                stepSourceIsSamsung = sourcePackage == SAMSUNG_HEALTH,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            base.copy(health = HealthStatus.Error)
        }
    }

    private fun appLabel(packageName: String): String =
        if (packageName == SAMSUNG_HEALTH) {
            "삼성헬스"
        } else {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            }.getOrDefault(packageName)
        }

    companion object {
        const val SAMSUNG_HEALTH = "com.sec.android.app.shealth"
    }
}
