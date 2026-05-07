package com.ssafy.s309.data.repository

import android.util.Log
import com.ssafy.s309.data.api.HealthApi
import com.ssafy.s309.data.api.SleepSessionApi
import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.data.ble.BleManager
import com.ssafy.s309.data.ble.BleProcessingSettings
import com.ssafy.s309.data.ble.ScannedDevice
import com.ssafy.s309.data.model.DailyHealthSummary
import com.ssafy.s309.data.model.DailyHealthSummaryUpsertRequest
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.HealthSnapshotBatchRequest
import com.ssafy.s309.data.model.HealthSnapshotItem
import com.ssafy.s309.data.model.MealCreateRequest
import com.ssafy.s309.data.model.MealCreateResponse
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.data.model.MealRecordResponse
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.data.model.SleepSessionCreateRequest
import com.ssafy.s309.data.repository.source.HealthConnectDataSource
import com.ssafy.s309.data.repository.source.HealthDataSource
import com.ssafy.s309.data.repository.source.MockHealthDataSource
import com.ssafy.s309.data.repository.source.SamsungHealthDataSource
import com.ssafy.s309.notification.GlucoseAlertManager
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 건강/혈당 관련 데이터 + BLE 패치 통신을 단일 진입점으로 노출하는 Repository.
 *
 * 정적 조회 우선순위:
 *  1. 백엔드 API (HealthApi)
 *  2. Samsung Health / Health Connect
 *  3. Mock (항상 동작 보장)
 */
@Singleton
class HealthRepository
    @Inject
    constructor(
        private val healthApi: HealthApi,
        private val sleepSessionApi: SleepSessionApi,
        private val mockDataSource: MockHealthDataSource,
        samsungDataSource: SamsungHealthDataSource,
        healthConnectDataSource: HealthConnectDataSource,
        private val bleManager: BleManager,
        private val glucoseAlertManager: GlucoseAlertManager,
    ) {
        private val primarySources: List<HealthDataSource> =
            listOf(samsungDataSource, healthConnectDataSource)

        // ── BLE 상태 / 스트림 ────────────────────────────────────────

        val bleConnectionState: StateFlow<BleConnectionState> = bleManager.connectionState
        val scannedDevices: StateFlow<List<ScannedDevice>> = bleManager.scannedDevices
        val glucoseStream: SharedFlow<GlucoseReading> = bleManager.glucoseReadings
        val glucoseHistory: StateFlow<List<GlucoseReading>> = bleManager.glucoseHistory
        val glucoseAlertStream: SharedFlow<NotificationItem> = glucoseAlertManager.alertStream
        val bleProcessingSettings: StateFlow<BleProcessingSettings> = bleManager.processingSettings

        fun updateAlertThresholds(
            alertLow: Int,
            alertHigh: Int,
        ) {
            glucoseAlertManager.updateThresholds(alertLow, alertHigh)
        }

        // ── BLE 액션 ─────────────────────────────────────────────────

        fun isBleSupported(): Boolean = bleManager.isBleSupported()

        fun isBluetoothEnabled(): Boolean = bleManager.isBluetoothEnabled()

        fun requestEnableBluetooth() = bleManager.requestEnableBluetooth()

        fun startBleScan() = bleManager.startScan()

        fun stopBleScan() = bleManager.stopScan()

        fun connectBleDevice(device: ScannedDevice) = bleManager.connect(device)

        fun disconnectBleDevice() = bleManager.disconnect()

        fun updateBleProcessingSettings(settings: BleProcessingSettings) = bleManager.updateProcessingSettings(settings)

        // ── 정적 조회 (BE → Samsung/HealthConnect → Mock) ────────────

        /** 최근 혈당 흐름. BE 기록이 있으면 우선 사용. */
        suspend fun getRecentGlucose(hours: Int = 6): List<GlucoseReading> {
            runCatching {
                val to = LocalDateTime.now()
                val from = to.minusHours(hours.toLong())
                val records =
                    healthApi.getGlucoseRecords(
                        from = from.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        to = to.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    )
                if (records.isNotEmpty()) {
                    return records.map { r ->
                        GlucoseReading(
                            timestampMillis =
                                LocalDateTime.parse(r.measuredAt)
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            valueMgDl = r.value.toInt(),
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "BE 혈당 조회 실패, 로컬 소스로 fallback", it) }

            return firstNonEmptyList { it.getRecentGlucose(hours) } ?: mockDataSource.getRecentGlucose(hours)
        }

        /** 목표 혈당 범위 — 백엔드에 전용 엔드포인트 없음, 로컬 소스 사용. */
        suspend fun getGlucoseTargetRange(): GlucoseRange =
            firstNonNull { it.getGlucoseTargetRange() } ?: mockDataSource.getGlucoseTargetRange()

        /** 오늘 식사 이벤트. BE 기록이 있으면 우선 사용. */
        suspend fun getTodayMeals(): List<MealEvent> {
            runCatching {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val meals = healthApi.getMeals(date = today)
                if (meals.isNotEmpty()) {
                    return meals.map { m ->
                        MealEvent(
                            id = m.mealId.toLong(),
                            timestampMillis =
                                LocalDateTime.parse(m.recordedAt)
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            label = m.foodName?.ifBlank { null } ?: "식사",
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "BE 식사 조회 실패, 로컬 소스로 fallback", it) }

            return firstNonEmptyList { it.getTodayMeals() } ?: mockDataSource.getTodayMeals()
        }

        /** 오늘 건강 요약. BE 기록이 있으면 우선 사용. */
        suspend fun getTodaySummary(): DailyHealthSummary {
            runCatching {
                val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
                val list = healthApi.getDailyHealthSummary(from = today, to = today)
                val summary = list.firstOrNull()
                if (summary != null) {
                    return DailyHealthSummary(
                        caloriesBurnedKcal = summary.caloriesBurned?.toInt() ?: 0,
                        sleepMinutes = summary.sleepMinutes ?: 0,
                    )
                }
            }.onFailure { Log.w(TAG, "BE 건강 요약 조회 실패, 로컬 소스로 fallback", it) }

            return firstNonNull { it.getTodaySummary() } ?: mockDataSource.getTodaySummary()
        }

        /** 알림 목록. BE 기록이 있으면 우선 사용. */
        suspend fun getNotifications(): List<NotificationItem> {
            runCatching {
                val response = healthApi.getAlerts()
                if (response.content.isNotEmpty()) {
                    return response.content.map { a ->
                        NotificationItem(
                            id = a.id.toLong(),
                            title = resolveAlertTitle(a.alertType),
                            message = a.message,
                            timeAgoText = formatTimeAgo(a.createdAt),
                            isUnread = !a.isRead,
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "BE 알림 조회 실패, mock으로 fallback", it) }

            return firstNonEmptyList { it.getNotifications() } ?: mockDataSource.getNotifications()
        }

        /** 날짜별 식사 기록 조회 */
        suspend fun getMealsByDate(date: String): Result<List<MealRecordResponse>> = runCatching { healthApi.getMeals(date = date) }

        /** 식사 기록 생성 (multipart, 이미지 선택) */
        suspend fun createMealRecord(
            request: MealCreateRequest,
            imageFile: java.io.File? = null,
        ): Result<MealCreateResponse> =
            runCatching {
                val json =
                    kotlinx.serialization.json.Json.encodeToString(
                        MealCreateRequest.serializer(),
                        request,
                    )
                val requestBody = json.toRequestBody("application/json".toMediaType())
                val imagePart =
                    imageFile?.takeIf { it.exists() }?.let {
                        val imageBody = it.readBytes().toRequestBody("image/jpeg".toMediaType())
                        okhttp3.MultipartBody.Part.createFormData("image", it.name, imageBody)
                    }
                healthApi.createMeal(request = requestBody, image = imagePart)
            }

        /** 알림 읽음 처리 (백엔드 반영). 실패해도 UI 상태는 유지. */
        suspend fun markAlertRead(alertId: Int) {
            runCatching { healthApi.markAlertRead(alertId) }
                .onFailure { Log.w(TAG, "알림 읽음 처리 실패 id=$alertId", it) }
        }

        /**
         * 워치 최근 수면 세션을 BE에 송신. 동일 startedAt 재호출 시 BE가 idempotent하게 기존 row 반환.
         * 추가 안전장치: 직전 송신과 동일한 startedAt이면 네트워크 호출 자체 생략.
         * 실패는 swallow — 폴러를 죽이지 않는다.
         */
        suspend fun syncSleepSession(
            startedAt: LocalDateTime,
            endedAt: LocalDateTime,
            source: String,
        ) {
            val key = startedAt.toString()
            if (key == lastSyncedSleepStartedAt) return
            runCatching {
                sleepSessionApi.create(
                    SleepSessionCreateRequest(
                        startedAt = startedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        endedAt = endedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        source = source,
                    ),
                )
                lastSyncedSleepStartedAt = key
                Log.d(TAG, "수면 세션 송신 완료: $startedAt ~ $endedAt")
            }.onFailure { Log.w(TAG, "수면 세션 송신 실패", it) }
        }

        @Volatile private var lastSyncedSleepStartedAt: String? = null

        /**
         * 1분 폴링 시점 메트릭을 in-memory 버퍼에 누적. 5개(=5분) 모이면 batch INSERT.
         * 실패는 swallow — 폴 루프 보호.
         */
        suspend fun bufferSnapshot(item: HealthSnapshotItem) {
            val toFlush: List<HealthSnapshotItem>?
            synchronized(snapshotBuffer) {
                snapshotBuffer.add(item)
                toFlush =
                    if (snapshotBuffer.size >= SNAPSHOT_FLUSH_SIZE) {
                        val copy = snapshotBuffer.toList()
                        snapshotBuffer.clear()
                        copy
                    } else {
                        null
                    }
            }
            if (toFlush != null) {
                runCatching { healthApi.saveSnapshotBatch(HealthSnapshotBatchRequest(items = toFlush)) }
                    .onSuccess { Log.d(TAG, "snapshot batch 송신 ok: inserted=${it.inserted} skipped=${it.skipped}") }
                    .onFailure { Log.w(TAG, "snapshot batch 송신 실패", it) }
            }
        }

        /** 일별 누적값 upsert. 1분 폴링마다 호출 가능 (BE는 같은 (user_id,date) 키에 UPDATE). */
        suspend fun upsertDailySummary(
            date: LocalDate,
            steps: Int?,
            caloriesBurned: Double?,
            sleepMinutes: Int?,
            avgHeartRate: Double?,
        ) {
            runCatching {
                healthApi.upsertDailySummary(
                    DailyHealthSummaryUpsertRequest(
                        date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        steps = steps,
                        caloriesBurned = caloriesBurned,
                        sleepMinutes = sleepMinutes,
                        avgHeartRate = avgHeartRate,
                    ),
                )
            }.onFailure { Log.w(TAG, "daily summary upsert 실패", it) }
        }

        private val snapshotBuffer = mutableListOf<HealthSnapshotItem>()

        // ── helpers ──────────────────────────────────────────────────

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

        private fun resolveAlertTitle(alertType: String): String = "키키"

        private fun formatTimeAgo(isoDateTime: String): String =
            try {
                // OffsetDateTime으로 먼저 시도 ("Z", "+09:00" 등 offset 포함 형식 처리)
                // 실패 시 timezone 없는 LocalDateTime으로 fallback
                val instant =
                    try {
                        java.time.OffsetDateTime.parse(isoDateTime).toInstant()
                    } catch (e: Exception) {
                        LocalDateTime.parse(isoDateTime).atZone(ZoneId.systemDefault()).toInstant()
                    }
                val minutes = java.time.Duration.between(instant, java.time.Instant.now()).toMinutes()
                when {
                    minutes < 1 -> "방금 전"
                    minutes < 60 -> "${minutes}분 전"
                    minutes < 1440 -> "${minutes / 60}시간 전"
                    else -> "${minutes / 1440}일 전"
                }
            } catch (e: Exception) {
                ""
            }

        private companion object {
            const val TAG = "HealthRepository"
            const val SNAPSHOT_FLUSH_SIZE = 5
        }
    }
