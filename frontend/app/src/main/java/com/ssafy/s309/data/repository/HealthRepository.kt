package com.ssafy.s309.data.repository


import android.util.Log

import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.data.ble.BleManager
import com.ssafy.s309.data.ble.BleProcessingSettings
import com.ssafy.s309.data.ble.ScannedDevice
import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.data.repository.source.HealthConnectDataSource
import com.ssafy.s309.data.repository.source.HealthDataSource
import com.ssafy.s309.data.repository.source.MockHealthDataSource
import com.ssafy.s309.data.repository.source.SamsungHealthDataSource
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 건강/혈당 관련 데이터 + BLE 패치 통신을 단일 진입점으로 노출하는 Repository.
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
        private val bleManager: BleManager,
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
