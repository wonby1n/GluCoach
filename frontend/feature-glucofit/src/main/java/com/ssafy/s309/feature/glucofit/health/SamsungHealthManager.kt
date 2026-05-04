package com.ssafy.s309.feature.glucofit.health

import android.app.Activity
import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.helper.aggregate
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

    private companion object {
        // 혈당 단위 변환: 1 mmol/L = 18.0182 mg/dL (의료 표준 변환 계수).
        const val MMOL_L_TO_MG_DL = 18.0182f
    }

    private val store = HealthDataService.getStore(activity)

    private val permissions =
        setOf(
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.EXERCISE, AccessType.READ),
            Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
            Permission.of(DataTypes.NUTRITION, AccessType.READ),
            Permission.of(DataTypes.HEART_RATE, AccessType.READ),
            Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ),
            Permission.of(DataTypes.STEPS, AccessType.READ),
        )

    suspend fun requestPermissions() {
        try {
            store.requestPermissions(permissions, activity)
        } catch (e: Exception) {
            Log.e(tag, "권한 요청 실패", e)
        }
    }

    /**
     * [혈당] 최근 1건 (mg/dL, Float).
     *
     * Samsung Health SDK 의 GLUCOSE_LEVEL 필드는 국제 표준 단위인 mmol/L 로 저장되며
     * 한국 의료 환경에서 통용되는 mg/dL 로 변환해서 반환한다 (× 18.0182).
     * 예: SDK 4.99 mmol/L → 약 90 mg/dL.
     */
    suspend fun getLatestBloodGlucose(): Float? {
        return try {
            val response =
                store.read(DataTypes.BLOOD_GLUCOSE) {
                    setOrdering(Ordering.DESC)
                    setLimit(1)
                }
            val mmolPerL =
                response.dataList.firstOrNull()
                    ?.getValue(DataType.BloodGlucoseType.GLUCOSE_LEVEL)
            val mgPerDl = mmolPerL?.let { it * MMOL_L_TO_MG_DL }
            Log.d(tag, "최근 혈당: ${mgPerDl?.let { "%.1f".format(it) } ?: "기록 없음"} mg/dL (SDK raw: ${mmolPerL ?: "-"} mmol/L)")
            mgPerDl
        } catch (e: Exception) {
            Log.e(tag, "혈당 읽기 실패", e)
            null
        }
    }

    /**
     * [활동 칼로리] 오늘 누적 활동 칼로리 (kcal, 정수).
     *
     * Samsung Health 앱 대시보드에 표시되는 "활동 칼로리"와 일치한다.
     * ACTIVITY_SUMMARY 의 TOTAL_ACTIVE_CALORIES_BURNED 집계를 읽는 것이라
     * 명시적 운동 세션이 없어도 걸음 등 일상 활동 기반 누적 값이 잡힌다.
     */
    suspend fun getTodayActiveCalories(): Int {
        return try {
            val startOfDay = LocalDate.now().atStartOfDay()
            val response =
                store.aggregate(DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED) {
                    setLocalTimeFilter(LocalTimeFilter.of(startOfDay, LocalDateTime.now(), true, true))
                }
            val calories = (response.dataList.firstOrNull()?.value ?: 0f).toInt()
            Log.d(tag, "오늘 활동 칼로리: $calories kcal")
            calories
        } catch (e: Exception) {
            Log.e(tag, "활동 칼로리 읽기 실패", e)
            0
        }
    }

    /**
     * [걸음수] 오늘 누적 걸음수 (보).
     *
     * STEPS 의 TOTAL 집계를 읽으며 Samsung Health 앱 대시보드의 걸음수와 일치한다.
     */
    suspend fun getTodaySteps(): Long {
        return try {
            val startOfDay = LocalDate.now().atStartOfDay()
            val response =
                store.aggregate(DataType.StepsType.TOTAL) {
                    setLocalTimeFilter(LocalTimeFilter.of(startOfDay, LocalDateTime.now(), true, true))
                }
            val steps = response.dataList.firstOrNull()?.value ?: 0L
            Log.d(tag, "오늘 걸음수: $steps 보")
            steps
        } catch (e: Exception) {
            Log.e(tag, "걸음수 읽기 실패", e)
            0L
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
