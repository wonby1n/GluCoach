package com.ssafy.s309.data.repository

import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.NotificationItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 건강/혈당 관련 데이터를 조회하는 Repository.
 *
 * 현재는 백엔드 API 미연동 상태이므로 모든 메서드가 하드코딩된 mock 데이터를 반환한다.
 * BE 연동 시 TODO 표시된 영역의 주석을 해제하고 `healthApi`를 사용하도록 전환한다.
 */
@Singleton
class HealthRepository
    @Inject
    constructor(
        // TODO(BE 연동): 실제 API 연결 시 주입 활성화
        // private val healthApi: HealthApi,
    ) {
        /** 최근 혈당 흐름. 메인 화면 그래프에 사용. */
        suspend fun getRecentGlucose(hours: Int = 6): List<GlucoseReading> {
            // TODO(BE 연동): return healthApi.getRecentGlucose(hours)
            return MOCK_GLUCOSE_SERIES
        }

        /** 사용자 목표 혈당 범위 (그래프의 회색 박스). */
        suspend fun getGlucoseTargetRange(): GlucoseRange {
            // TODO(BE 연동): return healthApi.getGlucoseTargetRange()
            return GlucoseRange(minMgDl = 90, maxMgDl = 180)
        }

        /** 오늘의 식사 이벤트. 그래프 위 밥그릇 핀에 사용. */
        suspend fun getTodayMeals(): List<MealEvent> {
            // TODO(BE 연동): return healthApi.getTodayMeals()
            return MOCK_MEALS
        }

        /** 하루 누적 칼로리/수면 요약. */
        suspend fun getTodaySummary(): DailyHealthSummary {
            // TODO(BE 연동): return healthApi.getTodaySummary()
            return DailyHealthSummary(
                caloriesBurnedKcal = 485,
                sleepMinutes = 7 * 60 + 15,
            )
        }

        /** 알림 리스트. */
        suspend fun getNotifications(): List<NotificationItem> {
            // TODO(BE 연동): return healthApi.getNotifications()
            return MOCK_NOTIFICATIONS
        }

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

            // 식사 이벤트 2개 (9:00, 12:00 경)
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
