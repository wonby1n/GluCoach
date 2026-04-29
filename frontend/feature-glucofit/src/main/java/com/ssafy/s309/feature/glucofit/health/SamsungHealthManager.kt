package com.ssafy.s309.feature.glucofit.health

import android.app.Activity
import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.helper.read
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Samsung Health SDK 래퍼.
 *
 * [activity] 는 반드시 Activity 인스턴스여야 합니다.
 * HealthDataService.getStore() 가 내부적으로 ActivityResultLauncher를 등록하므로
 * Activity.onCreate() 에서 생성하고, applicationContext 등 비-Activity Context를
 * 넘기면 런타임에 ClassCastException 이 발생합니다.
 */
class SamsungHealthManager(private val activity: Activity) {
    private val tag = "SamsungHealthManager"

    private val store = HealthDataService.getStore(activity)

    private val permissions =
        setOf(
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.EXERCISE, AccessType.READ),
            Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
            Permission.of(DataTypes.NUTRITION, AccessType.READ),
            Permission.of(DataTypes.HEART_RATE, AccessType.READ),
        )

    suspend fun requestPermissions() {
        try {
            store.requestPermissions(permissions, activity)
        } catch (e: Exception) {
            Log.e(tag, "권한 요청 실패", e)
        }
    }

    /** [혈당] 최근 1건 (mg/dL, Float) */
    suspend fun getLatestBloodGlucose(): Float? {
        return try {
            val response =
                store.read(DataTypes.BLOOD_GLUCOSE) {
                    setOrdering(Ordering.DESC)
                    setLimit(1)
                }
            val glucose =
                response.dataList.firstOrNull()
                    ?.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)
            Log.d(tag, "최근 혈당: ${glucose ?: "기록 없음"} mg/dL")
            glucose
        } catch (e: Exception) {
            Log.e(tag, "혈당 읽기 실패", e)
            null
        }
    }

    /** [운동] 오늘 운동 소모 칼로리 추정 (세션 수 × 200) */
    suspend fun getTodayExerciseCalories(): Int {
        return try {
            val startOfDay = LocalDate.now().atStartOfDay()
            val response =
                store.read(DataTypes.EXERCISE) {
                    setLocalTimeFilter(LocalTimeFilter.of(startOfDay, LocalDateTime.now(), true, true))
                }
            val calories = response.dataList.size * 200
            Log.d(tag, "운동 세션 수: ${response.dataList.size}, 추정 칼로리: $calories")
            calories
        } catch (e: Exception) {
            Log.e(tag, "운동 칼로리 읽기 실패", e)
            0
        }
    }

    /** [수면] 최근 수면 시간 (분) */
    suspend fun getLastSleepDurationMinutes(): Int {
        return try {
            val response =
                store.read(DataTypes.SLEEP) {
                    setOrdering(Ordering.DESC)
                    setLimit(1)
                }
            val sleep =
                response.dataList.firstOrNull() ?: run {
                    Log.d(tag, "수면 기록 없음")
                    return 0
                }
            val totalMin = Duration.between(sleep.startTime, sleep.endTime).toMinutes().toInt()
            Log.d(tag, "수면 기록: ${totalMin / 60}h ${totalMin % 60}m")
            totalMin
        } catch (e: Exception) {
            Log.e(tag, "수면 읽기 실패", e)
            0
        }
    }

    /** [심박수] 최근 1건 (Float) */
    suspend fun getLatestHeartRate(): Float? {
        return try {
            val response =
                store.read(DataTypes.HEART_RATE) {
                    setOrdering(Ordering.DESC)
                    setLimit(1)
                }
            val heartRate =
                response.dataList.firstOrNull()
                    ?.getValue(DataType.HeartRateType.HEART_RATE)
            Log.d(tag, "최근 심박수: ${heartRate ?: "기록 없음"} bpm")
            heartRate
        } catch (e: Exception) {
            Log.e(tag, "심박수 읽기 실패", e)
            null
        }
    }
}
