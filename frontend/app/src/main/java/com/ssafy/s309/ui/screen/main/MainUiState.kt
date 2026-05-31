package com.ssafy.s309.ui.screen.main

import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.NotificationItem

/**
 * 메인(홈) 화면의 UI 상태.
 *
 * 화면은 단일 Flow<MainUiState> 를 collect 하여 렌더링한다.
 *
 * @property isLoading 초기 로딩 여부
 * @property currentGlucoseMgDl 현재 혈당 수치 (없으면 null)
 * @property diffFromPrevious 30분 전 대비 변화
 * @property glucoseSeries 그래프용 시리즈
 * @property glucoseRange 목표 범위 (회색 박스)
 * @property meals 식사 이벤트
 * @property summary 걸음수/수면 요약
 * @property notifications 알림 목록
 * @property isNotificationPanelOpen 알림 패널 열림 여부
 */
data class MainUiState(
    val isLoading: Boolean = true,
    val isDeviceConnected: Boolean = false,
    val currentGlucoseMgDl: Int? = null,
    val diffFromPrevious: Int = 0,
    val trendRateMgDlPerMin: Float = 0f,
    val diabetesType: String = "NORMAL",
    val glucoseSeries: List<GlucoseReading> = emptyList(),
    val glucoseRange: GlucoseRange = GlucoseRange(90, 180),
    val summary: DailyHealthSummary = DailyHealthSummary(0, 0),
    val notifications: List<NotificationItem> = emptyList(),
    val isNotificationPanelOpen: Boolean = false,
    val selectedNotification: NotificationItem? = null,
    val isNewUser: Boolean = false,
    val walkingFeedbackUntil: Long = 0L,
)
