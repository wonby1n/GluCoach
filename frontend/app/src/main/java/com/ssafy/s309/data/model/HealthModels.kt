package com.ssafy.s309.data.model

import kotlinx.serialization.SerialName
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

/** 하루 누적 건강 요약 (걸음 수, 수면) */
@Serializable
data class DailyHealthSummary(
    val steps: Int,
    val sleepMinutes: Int,
)

/** 키키가 확인한 내용 — 카드 1개 */
@Serializable
data class DisplayTraceCard(
    val type: String = "",
    val title: String = "",
    val description: String = "",
    val severity: String = "normal",
)

/** 키키가 확인한 내용 — decision 객체 */
@Serializable
data class DisplayTraceDecision(
    val reason: String = "",
)

/** 키키가 확인한 내용 — 전체 구조 */
@Serializable
data class DisplayTrace(
    val summary: String = "",
    val cards: List<DisplayTraceCard> = emptyList(),
    val decision: DisplayTraceDecision = DisplayTraceDecision(),
)

/** Agent A/B 비교 결과 — 음식 단일 항목 */
@Serializable
data class AgentComparisonFood(
    val name: String,
    @SerialName("peak_mg_dl") val peakMgDl: Double,
    @SerialName("risk_level") val riskLevel: String,
)

/** Agent A/B 비교 결과 */
@Serializable
data class AgentComparison(
    @SerialName("food_a") val foodA: AgentComparisonFood,
    @SerialName("food_b") val foodB: AgentComparisonFood,
    val winner: String,
)

/** Agent 응답 payload */
@Serializable
data class AgentPayload(
    val comparison: AgentComparison? = null,
)

/** 알림 패널 항목 */
@Serializable
data class NotificationItem(
    val id: Long,
    val title: String,
    val message: String,
    val timeAgoText: String,
    val isUnread: Boolean,
    val alertType: String = "",
    val createdAt: String = "",
    val displayTrace: DisplayTrace? = null,
    val payload: AgentPayload? = null,
)

/** 안드로이드 캘린더 일정 항목 */
@Serializable
data class CalendarEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean = false,
    val description: String? = null,
    val calendarName: String? = null,
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
    // BE 가 채워주면 사용자 노출용. 없으면 foodName fallback.
    val foodDisplayName: String? = null,
    // foods 테이블 join 결과. 호환성 위해 nullable.
    val kcal: Double? = null,
    val carbsG: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    // 스케줄러가 계산한 식후 최고 혈당. 데이터 부족이면 null.
    val peakGlucose: Double? = null,
)

/** POST /api/meals 요청 (multipart "request" 파트) */
@Serializable
data class MealCreateRequest(
    val foodId: Int,
    val memo: String? = null,
    // ISO-8601 "2026-05-06T12:30:00"
    val recordedAt: String,
)

/** POST /api/meals 응답 */
@Serializable
data class MealCreateResponse(
    val mealId: Int,
)

/** GET /api/chat/messages 응답 항목 */
@Serializable
data class ChatMessageItemResponse(
    val id: Long,
    val sender: String,
    val message: String? = null,
    val messageType: String? = null,
    val commandType: String? = null,
    val displayTrace: DisplayTrace? = null,
    val payload: AgentPayload? = null,
    val isRead: Boolean,
    val createdAt: String,
)

/** GET /api/chat/messages 페이지 응답 */
@Serializable
data class ChatMessageListResponse(
    val content: List<ChatMessageItemResponse>,
    val page: Int,
    val size: Int,
    val total: Long,
    val unreadCount: Long,
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

/** POST /api/health/snapshots 요청 항목 — 1분 한 점 메트릭 */
@Serializable
data class HealthSnapshotItem(
    // ISO-8601 LocalDateTime
    val recordedAt: String,
    val stepsTotal: Int? = null,
    val caloriesBurned: Double? = null,
    val heartRate: Double? = null,
)

/** POST /api/health/snapshots 요청 — 5분 batch */
@Serializable
data class HealthSnapshotBatchRequest(
    val items: List<HealthSnapshotItem>,
)

/** POST /api/health/snapshots 응답 */
@Serializable
data class HealthSnapshotBatchResponse(
    val inserted: Int,
    val skipped: Int,
)

/** POST /api/health/daily-summary 요청 — 일별 누적값 upsert */
@Serializable
data class DailyHealthSummaryUpsertRequest(
    // ISO-8601 LocalDate "2026-05-06"
    val date: String,
    val steps: Int? = null,
    val caloriesBurned: Double? = null,
    val sleepMinutes: Int? = null,
    val avgHeartRate: Double? = null,
)

/** POST /api/chat/messages/command 요청 */
@Serializable
data class ChatCommandRequest(
    val commandType: String,
    val message: String,
    val payload: Map<String, String> = emptyMap(),
)

/** POST /api/chat/messages/command 응답 */
@Serializable
data class ChatCommandResponse(
    val id: Long,
    val sender: String,
    val commandType: String,
    val message: String,
    val createdAt: String,
)

/** GET /api/chat/messages/unread-count 응답 */
@Serializable
data class UnreadCountResponse(
    val unreadCount: Long,
)

/** POST /ai/agent/post-meal trigger 페이로드 */
@Serializable
data class PostMealTriggerRequest(
    val reason: String,
    val meal_time: String = "",
    val user_reply: String? = null,
)

/** POST /ai/agent/post-meal 요청 바디 */
@Serializable
data class PostMealReplyRequest(
    val user_id: String,
    val trigger: PostMealTriggerRequest,
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
