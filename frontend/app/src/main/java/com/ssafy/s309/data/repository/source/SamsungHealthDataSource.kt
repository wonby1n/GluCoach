package com.ssafy.s309.data.repository.source

import android.util.Log
import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.NotificationItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Samsung Health SDK 기반 DataSource.
 *
 * [SamsungHealthHolder] 에 Activity 가 부착되어 있고 매니저 생성에 성공했을 때만 데이터를
 * 반환할 수 있다. 그 외에는 빈 리스트 / null 을 반환하여 Repository 의 mock fallback 으로
 * 떨어진다.
 *
 * 현재 PR 에서는 걸음수 / 수면 요약만 시도하며, 권한 미부여 시 매니저가 0 을 반환하므로
 * (둘 다 0 이면) 데이터 없음으로 간주하여 null 을 반환한다.
 */
@Singleton
class SamsungHealthDataSource
    @Inject
    constructor(
        private val holder: SamsungHealthHolder,
    ) : HealthDataSource {
        suspend fun isReady(): Boolean = holder.manager != null

        override suspend fun getRecentGlucose(hours: Int): List<GlucoseReading> = emptyList()

        override suspend fun getGlucoseTargetRange(): GlucoseRange? = null

        override suspend fun getTodayMeals(): List<MealEvent> = emptyList()

        override suspend fun getTodaySummary(): DailyHealthSummary? {
            val mgr = holder.manager ?: return null
            return try {
                val steps = mgr.getTodaySteps().toInt()
                val sleep = mgr.getLastSleepDurationMinutes()
                if (steps == 0 && sleep == 0) {
                    null
                } else {
                    DailyHealthSummary(steps = steps, sleepMinutes = sleep)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Samsung Health summary fetch 실패", t)
                null
            }
        }

        override suspend fun getNotifications(): List<NotificationItem> = emptyList()

        private companion object {
            const val TAG = "SamsungHealthDataSource"
        }
    }
