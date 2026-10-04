package io.github.lookie14.runcash.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * 폰의 걸음 센서(Step Counter). 원래 하루 종일 도는 저전력 전용 칩이라, 값을 받아 보는 비용은 거의 없다.
 * 수집하는 동안만 등록하므로 앱이 화면에 있을 때만 쓴다.
 * 안드로이드 10부터 "신체 활동" 권한이 있어야 하고, 권한이나 센서가 없으면 아무것도 내보내지 않는다.
 */
class LiveStepSensor(private val context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val isAvailable: Boolean get() = sensor != null

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    /** 늘어난 걸음 조각. 등록 직후 첫 값은 기준으로만 쓴다. */
    fun increments(): Flow<StepIncrement> = callbackFlow {
        val manager = sensorManager
        val stepSensor = sensor
        if (manager == null || stepSensor == null || !hasPermission()) {
            close()
            return@callbackFlow
        }
        var last: Float? = null
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val total = event.values.firstOrNull() ?: return
                val previous = last
                last = total
                // 재부팅으로 카운터가 처음부터 다시 세면 그 순간은 0으로 본다.
                val delta = if (previous == null || total < previous) 0f else total - previous
                if (delta > 0f) trySend(StepIncrement(System.currentTimeMillis(), delta.toLong()))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }
}
