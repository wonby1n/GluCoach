package com.ssafy.s309.data.model

import kotlinx.serialization.Serializable

/** 실시간 혈당 측정 값 (BLE 패치 / UI 표시용) */
@Serializable
data class GlucoseReading(
    val timestampMillis: Long,
    val valueMgDl: Int,
)

/** 목표 혈당 범위 (그래프 회색 박스) */
@Serializable
data class GlucoseRange(
    val minMgDl: Int,
    val maxMgDl: Int,
)

/** 식사 이벤트 (그래프 밥그릇 핀) */
@Serializable
data class MealEvent(
    val id: Long,
    val timestampMillis: Long,
    val label: String = "식사",
)

/** 하루 누적 건강 요약 (칼로리, 수면) */
@Serializable
data class DailyHealthSummary(
    val caloriesBurnedKcal: Int,
    val sleepMinutes: Int,
)

/** 알림 패널 항목 */
@Serializable
data class NotificationItem(
    val id: Long,
    val title: String,
    val message: String,
    val timeAgoText: String,
    val isUnread: Boolean,
)

// ── 백엔드 응답 DTO ─────────────────────────────────────────────────

/** GET /api/glucose-records 응답 항목 */
@Serializable
data class CgmRecordResponse(
    val id: Long,
    val value: Double,
    // ISO-8601 "2026-05-06T10:30:00"
    val measuredAt: String,
)

/** GET /api/health/daily-summary 응답 항목 */
@Serializable
data class DailyHealthSummaryResponse(
    val date: String,
    val steps: Int? = null,
    val caloriesBurned: Double? = null,
    val sleepMinutes: Int? = null,
    val avgHeartRate: Double? = null,
    val updatedAt: String? = null,
)

/** GET /api/meals 응답 항목 */
@Serializable
data class MealRecordResponse(
    val mealId: Int,
    val foodId: Int? = null,
    val foodName: String? = null,
    val memo: String? = null,
    // ISO-8601
    val recordedAt: String,
    val imageUrl: String? = null,
)

/** GET /api/v1/alerts 응답 항목 */
@Serializable
data class AlertItem(
    val id: Int,
    val alertType: String,
    val message: String,
    val isRead: Boolean,
    // ISO-8601
    val createdAt: String,
)

/** GET /api/v1/alerts 페이지 응답 */
@Serializable
data class AlertListResponse(
    val content: List<AlertItem>,
    val unreadCount: Long,
    val page: Int,
    val size: Int,
    val total: Long,
)

/** POST /api/sleep-sessions 요청 — 워치 수면 세션 송신 */
@Serializable
data class SleepSessionCreateRequest(
    // ISO-8601 LocalDateTime "2026-05-06T23:30:00"
    val startedAt: String,
    val endedAt: String,
    val source: String? = null,
)

/** POST /api/sleep-sessions 응답 */
@Serializable
data class SleepSessionResponse(
    val id: Int,
    val startedAt: String,
    val endedAt: String,
    val source: String? = null,
)
