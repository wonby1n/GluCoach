package com.ssafy.s309.data.repository

import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.data.ble.BleManager
import com.ssafy.s309.data.ble.BleProcessingSettings
import com.ssafy.s309.data.ble.ScannedDevice
import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.notification.GlucoseAlertManager
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 건강/혈당 관련 데이터 + BLE 패치 통신을 단일 진입점으로 노출하는 Repository.
 *
 * UI(ViewModel) 는 이 클래스만 의존하면 되고, 내부적으로 [BleManager] 를 통해 패치와 통신한다.
 *
 * 데이터 소스 현재 상태:
 * - 정적 조회 메서드(`getRecentGlucose` 등): mock 데이터 (BE 연동 시 [TODO] 영역 교체)
 * - [glucoseStream]: BLE 패치에서 도착하는 실시간 측정 값 — 실제 데이터
 * - BLE 연결/제어: [BleManager] 위임
 */
@Singleton
class HealthRepository
    @Inject
    constructor(
        private val bleManager: BleManager,
        private val glucoseAlertManager: GlucoseAlertManager,
        // TODO(BE 연동): 실제 API 연결 시 주입 활성화
        // private val healthApi: HealthApi,
    ) {
        // ────────────────────────────────────────
        // BLE 상태 / 스트림 (UI 가 collect)
        // ────────────────────────────────────────

        /** 패치 연결 상태 (Idle / Scanning / Connecting / Connected / Disconnected / Error). */
        val bleConnectionState: StateFlow<BleConnectionState> = bleManager.connectionState

        /** 스캔으로 발견된 패치 후보 목록. */
        val scannedDevices: StateFlow<List<ScannedDevice>> = bleManager.scannedDevices

        /** 패치에서 도착하는 실시간 혈당 측정 값 (이벤트 스트림). */
        val glucoseStream: SharedFlow<GlucoseReading> = bleManager.glucoseReadings

        /** 패치에서 누적된 혈당 히스토리 (최대 100개, 앱 수명 동안 유지). */
        val glucoseHistory: StateFlow<List<GlucoseReading>> = bleManager.glucoseHistory

        /** GlucoseAlertManager 가 감지한 이상 혈당 알림 스트림 (인앱 패널 표시용). */
        val glucoseAlertStream: SharedFlow<NotificationItem> = glucoseAlertManager.alertStream

        /** 사용자 설정 기반 알림 임계값 갱신. */
        fun updateAlertThresholds(
            alertLow: Int,
            alertHigh: Int,
        ) {
            glucoseAlertManager.updateThresholds(alertLow, alertHigh)
        }

        /** 데이터 처리 설정 스냅샷 (보정값 / 스파이크 임계값 / 출력타입 / 주기평균). */
        val bleProcessingSettings: StateFlow<BleProcessingSettings> = bleManager.processingSettings

        // ────────────────────────────────────────
        // BLE 액션 (UI 의 사용자 입력에 의해 호출)
        // ────────────────────────────────────────

        /** 현재 기기가 BLE 를 지원하는지. */
        fun isBleSupported(): Boolean = bleManager.isBleSupported()

        /** 블루투스가 켜져 있는지. */
        fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()

        /** 블루투스 활성화 시스템 다이얼로그 띄우기. */
        fun requestEnableBluetooth() = bleManager.requestEnableBluetooth()

        /** 주변 패치 스캔 시작. */
        fun startBleScan() = bleManager.startScan()

        /** 진행 중인 스캔 중단. */
        fun stopBleScan() = bleManager.stopScan()

        /** 선택한 패치에 연결. */
        fun connectBleDevice(device: ScannedDevice) = bleManager.connect(device)

        /** 현재 연결된 패치와 끊기. */
        fun disconnectBleDevice() = bleManager.disconnect()

        /** 데이터 처리 설정 변경 (보정값/스파이크 임계값/출력 타입/주기 평균 일괄). */
        fun updateBleProcessingSettings(settings: BleProcessingSettings) = bleManager.updateProcessingSettings(settings)

        // ────────────────────────────────────────
        // 정적 조회 (현재 mock — BE 연동 후 교체)
        // ────────────────────────────────────────

        /** 최근 혈당 흐름. 메인 화면 그래프 초기 로드용. */
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
