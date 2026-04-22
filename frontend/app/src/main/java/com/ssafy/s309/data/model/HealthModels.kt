package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

/**
 * 실시간 혈당 측정 값 (CGM 등)
 *
 * @property timestampMillis 측정 시각 (epoch millis)
 * @property valueMgDl 혈당 수치 mg/dL
 */
@Serializable
data class GlucoseReading(
    val timestampMillis: Long,
    val valueMgDl: Int,
)

/**
 * 사용자가 설정한 목표 혈당 범위. 그래프의 회색 박스로 표시된다.
 *
 * @property minMgDl 하한 (예: 90)
 * @property maxMgDl 상한 (예: 180)
 */
@Serializable
data class GlucoseRange(
    val minMgDl: Int,
    val maxMgDl: Int,
)

/**
 * 사용자가 입력한 식사 이벤트. 그래프 위에 밥그릇 핀으로 표시된다.
 */
@Serializable
data class MealEvent(
    val id: Long,
    val timestampMillis: Long,
    val label: String = "식사",
)

/**
 * 하루 누적 건강 요약 (칼로리, 수면 등).
 *
 * @property caloriesBurnedKcal 하루 누적 소모 칼로리
 * @property sleepMinutes 수면 시간 (분)
 */
@Serializable
data class DailyHealthSummary(
    val caloriesBurnedKcal: Int,
    val sleepMinutes: Int,
)

/**
 * 알림 패널에 표시될 알림 항목.
 *
 * @property isUnread true 이면 굵게 표시
 */
@Serializable
data class NotificationItem(
    val id: Long,
    val title: String,
    val message: String,
    val timeAgoText: String,
    val isUnread: Boolean,
)
