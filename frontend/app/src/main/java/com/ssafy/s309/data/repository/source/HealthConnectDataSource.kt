package com.ssafy.s309.data.repository.source

import android.os.Build
import android.util.Log
import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.feature.glucofit.health.HealthConnectManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect 기반 DataSource.
 *
 * 권한 부여 + SDK 가용성이 모두 충족된 경우에만 의미 있는 데이터를 반환할 수 있다.
 * 그 외에는 빈 리스트 / null 을 반환하여 Repository 의 mock fallback 으로 떨어진다.
 *
 * 현재 PR 에서는 권한 흐름 / 가용성 검증 인프라만 노출하며, BloodGlucoseRecord 의 epochMilli
 * 보존을 위한 도메인 모델 매핑은 후속 PR 에서 추가된다.
 */
@Singleton
class HealthConnectDataSource
    @Inject
    constructor(
        private val manager: HealthConnectManager,
    ) : HealthDataSource {
        suspend fun isReady(): Boolean =
            try {
                // HealthConnectClient 자체는 SDK 가용성을 내부에서 처리하지만,
                // API 26 미만 디바이스에서는 SDK 호출 자체가 의미 없음.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    false
                } else {
                    manager.isAvailable() && manager.hasPermissions()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Health Connect 가용성 확인 실패", t)
                false
            }

        override suspend fun getRecentGlucose(hours: Int): List<GlucoseReading> = emptyList()

        override suspend fun getGlucoseTargetRange(): GlucoseRange? = null

        override suspend fun getTodayMeals(): List<MealEvent> = emptyList()

        override suspend fun getTodaySummary(): DailyHealthSummary? = null

        override suspend fun getNotifications(): List<NotificationItem> = emptyList()

        private companion object {
            const val TAG = "HealthConnectDataSource"
        }
    }
