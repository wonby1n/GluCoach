package com.ssafy.s309.data.repository.source

import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.NotificationItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 정적 mock 데이터를 반환하는 DataSource.
 *
 * 기존 HealthRepository 가 반환하던 하드코딩 값과 동일하다. 다른 모든 DataSource 가
 * 사용 불가/실패할 때 최종 fallback 으로 사용된다.
 */
@Singleton
class MockHealthDataSource
    @Inject
    constructor() : HealthDataSource {
        override suspend fun getRecentGlucose(hours: Int): List<GlucoseReading> = MOCK_GLUCOSE_SERIES

        override suspend fun getGlucoseTargetRange(): GlucoseRange = GlucoseRange(minMgDl = 90, maxMgDl = 180)

        override suspend fun getTodayMeals(): List<MealEvent> = MOCK_MEALS

        override suspend fun getTodaySummary(): DailyHealthSummary =
            DailyHealthSummary(
                caloriesBurnedKcal = 485,
                sleepMinutes = 7 * 60 + 15,
            )

        override suspend fun getNotifications(): List<NotificationItem> = MOCK_NOTIFICATIONS

        private companion object {
            // 08:00 ~ 14:00, 30분 간격 하드코딩된 시리즈 (mg/dL)
            private val MOCK_GLUCOSE_SERIES: List<GlucoseReading> =
                listOf(
                    105 to 8 * 60,
                    110 to 8 * 60 + 30,
                    120 to 9 * 60,
                    145 to 9 * 60 + 30,
                    170 to 10 * 60,
                    195 to 10 * 60 + 30,
                    185 to 11 * 60,
                    160 to 11 * 60 + 30,
                    135 to 12 * 60,
                    120 to 12 * 60 + 30,
                    105 to 13 * 60,
                    95 to 13 * 60 + 30,
                    92 to 14 * 60,
                ).map { (value, minutesOfDay) ->
                    GlucoseReading(
                        timestampMillis = minutesOfDay * 60_000L,
                        valueMgDl = value,
                    )
                }

            private val MOCK_MEALS: List<MealEvent> =
                listOf(
                    MealEvent(
                        id = 1L,
                        timestampMillis = (9 * 60) * 60_000L,
                        label = "아침",
                    ),
                    MealEvent(
                        id = 2L,
                        timestampMillis = (12 * 60) * 60_000L,
                        label = "점심",
                    ),
                )

            private val MOCK_NOTIFICATIONS: List<NotificationItem> =
                listOf(
                    NotificationItem(
                        id = 1L,
                        title = "저혈당 알림",
                        message = "저혈당 위기에요. 당분을 섭취하세요.",
                        timeAgoText = "10분 전",
                        isUnread = true,
                    ),
                    NotificationItem(
                        id = 2L,
                        title = "지난 알림 1",
                        message = "알림 상세 알림 상세 알림 상세 알림 상세",
                        timeAgoText = "2시간 전",
                        isUnread = true,
                    ),
                    NotificationItem(
                        id = 3L,
                        title = "지난 알림 2",
                        message = "알림 상세 알림 상세 알림 상세 알림 상세",
                        timeAgoText = "8시간 전",
                        isUnread = false,
                    ),
                    NotificationItem(
                        id = 4L,
                        title = "지난 알림 2",
                        message = "알림 상세 알림 상세 알림 상세 알림 상세",
                        timeAgoText = "17시간 전",
                        isUnread = false,
                    ),
                    NotificationItem(
                        id = 5L,
                        title = "지난 알림 3",
                        message = "알림 상세 알림 상세 알림 상세 알림 상세",
                        timeAgoText = "1일 전",
                        isUnread = false,
                    ),
                )
        }
    }
