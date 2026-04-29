package com.ssafy.s309.data.repository

import android.util.Log
import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.data.repository.source.HealthConnectDataSource
import com.ssafy.s309.data.repository.source.HealthDataSource
import com.ssafy.s309.data.repository.source.MockHealthDataSource
import com.ssafy.s309.data.repository.source.SamsungHealthDataSource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 건강/혈당 관련 데이터를 조회하는 Repository.
 *
 * 외부 시그니처는 변경되지 않는다 — MainViewModel 등 호출부는 영향받지 않는다.
 *
 * 내부 동작:
 *  1. [primarySources] 의 우선순위대로 데이터 조회를 시도한다.
 *  2. 각 소스가 빈 결과 / null / 예외를 반환하면 다음 소스로 이동.
 *  3. 모든 primary 소스가 데이터를 못 주면 [mockDataSource] 의 mock 값을 반환.
 *
 * 권한 미부여 / SDK 미지원 / 매니저 생성 실패 등 어떤 상황에서도 mock 으로 안전하게
 * 떨어지므로, 디바이스 환경과 무관하게 메인 화면은 항상 동일하게 동작한다.
 */
@Singleton
class HealthRepository
    @Inject
    constructor(
        private val mockDataSource: MockHealthDataSource,
        samsungDataSource: SamsungHealthDataSource,
        healthConnectDataSource: HealthConnectDataSource,
    ) {
        // 우선순위: Samsung Health → Health Connect → Mock(=fallback)
        private val primarySources: List<HealthDataSource> =
            listOf(samsungDataSource, healthConnectDataSource)

        /** 최근 혈당 흐름. 메인 화면 그래프에 사용. */
        suspend fun getRecentGlucose(hours: Int = 6): List<GlucoseReading> =
            firstNonEmptyList { it.getRecentGlucose(hours) } ?: mockDataSource.getRecentGlucose(hours)

        /** 사용자 목표 혈당 범위 (그래프의 회색 박스). */
        suspend fun getGlucoseTargetRange(): GlucoseRange =
            firstNonNull { it.getGlucoseTargetRange() } ?: mockDataSource.getGlucoseTargetRange()

        /** 오늘의 식사 이벤트. 그래프 위 밥그릇 핀에 사용. */
        suspend fun getTodayMeals(): List<MealEvent> = firstNonEmptyList { it.getTodayMeals() } ?: mockDataSource.getTodayMeals()

        /** 하루 누적 칼로리/수면 요약. */
        suspend fun getTodaySummary(): DailyHealthSummary = firstNonNull { it.getTodaySummary() } ?: mockDataSource.getTodaySummary()

        /** 알림 리스트. */
        suspend fun getNotifications(): List<NotificationItem> =
            firstNonEmptyList { it.getNotifications() } ?: mockDataSource.getNotifications()

        /** primary 소스에서 첫 번째 비-빈 리스트를 반환. 모두 비어있으면 null. */
        private suspend fun <T> firstNonEmptyList(fetch: suspend (HealthDataSource) -> List<T>): List<T>? {
            for (source in primarySources) {
                val result =
                    try {
                        fetch(source)
                    } catch (t: Throwable) {
                        Log.w(TAG, "primary source ${source::class.simpleName} 조회 실패", t)
                        emptyList()
                    }
                if (result.isNotEmpty()) return result
            }
            return null
        }

        /** primary 소스에서 첫 번째 non-null 결과를 반환. 모두 null 이면 null. */
        private suspend fun <T : Any> firstNonNull(fetch: suspend (HealthDataSource) -> T?): T? {
            for (source in primarySources) {
                val result =
                    try {
                        fetch(source)
                    } catch (t: Throwable) {
                        Log.w(TAG, "primary source ${source::class.simpleName} 조회 실패", t)
                        null
                    }
                if (result != null) return result
            }
            return null
        }

        private companion object {
            const val TAG = "HealthRepository"
        }
    }
