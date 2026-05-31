package com.ssafy.s309.data.repository.source

import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.NotificationItem

/**
 * 건강/혈당 데이터의 추상 소스.
 *
 * 구현체는 mock, Health Connect, Samsung Health 등 다양하다.
 * Repository 가 가용한 구현체를 우선순위로 시도하고 실패/빈 결과 시 fallback 한다.
 *
 * - 리스트 반환 메서드는 빈 리스트 = 데이터 없음 으로 간주.
 * - 단일 객체 반환 메서드는 null = 데이터 없음 으로 간주.
 * - Mock 구현체는 항상 비-빈 / non-null 을 보장하므로 최종 fallback 으로 동작한다.
 */
interface HealthDataSource {
    suspend fun getRecentGlucose(hours: Int): List<GlucoseReading>

    suspend fun getGlucoseTargetRange(): GlucoseRange?

    suspend fun getTodayMeals(): List<MealEvent>

    suspend fun getTodaySummary(): DailyHealthSummary?

    suspend fun getNotifications(): List<NotificationItem>
}
