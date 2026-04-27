package com.ssafy.s309.data.ble

import com.ssafy.s309.data.model.GlucoseReading
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 패치에서 도착하는 4바이트 패킷을 [GlucoseReading] 으로 변환한다.
 *
 * 처리 파이프라인은 3단계로 분리되어 있다:
 * 1. [extractRaw] — 패킷 검증 + 16비트 raw 값 추출
 * 2. [trackMovingAverage] — 이동평균 갱신 (모든 수신 샘플에 대해 호출)
 * 3. [applyFilterAndConvert] — 스파이크 필터 + 출력 타입에 따른 변환
 *
 * 실시간 모드는 [parse] 한 번 호출로 위 3단계가 모두 처리된다.
 * 주기 평균(gather) 모드에서는 [BleManager] 가 단계를 분리해서 호출한다 — 모든 raw 는
 * 이동평균에 반영하지만, 스파이크/변환은 평균된 값에 한 번만 적용한다.
 *
 * 처리 설정([correctVal] / [spikeThreshold] / [outputType])은 런타임에 변경 가능하며,
 * 새 연결 시작 시 [reset] 으로 이동평균 상태만 초기화된다 (설정값은 유지).
 */
@Singleton
class BleDataParser
    @Inject
    constructor() {
        // ────────────────────────────────────────
        // 런타임 변경 가능한 설정값
        // ────────────────────────────────────────

        /** mg/dL 변환 후 더해지는 사용자 보정값. raw 모드에서는 적용 안 됨. */
        @Volatile
        var correctVal: Int = 0

        /** 이동평균과 차이가 이 값 이상이면 노이즈로 간주해 버린다. */
        @Volatile
        var spikeThreshold: Int = DEFAULT_SPIKE_THRESHOLD

        /** raw / mg-dL 출력 모드. */
        @Volatile
        var outputType: GlucoseOutputType = GlucoseOutputType.MG_DL

        // ────────────────────────────────────────
        // 이동평균 상태 (연결 단위)
        // ────────────────────────────────────────

        private val recentRawValues = ArrayDeque<Int>(MAX_HISTORY)
        private var movingAverage: Int = 0

        // ────────────────────────────────────────
        // 단계별 메서드
        // ────────────────────────────────────────

        /**
         * 바이트 패킷을 검증하고 16비트 raw 값을 추출한다.
         * 길이가 [PACKET_SIZE] 가 아니거나 헤더(`0x2F 0xFF`)가 다르면 null.
         */
        fun extractRaw(bytes: ByteArray): Int? {
            if (bytes.size != PACKET_SIZE) return null
            if (bytes[0] != HEADER_BYTE_0 || bytes[1] != HEADER_BYTE_1) return null
            return ((bytes[2].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or
                (bytes[3].toInt() and BYTE_MASK)
        }

        /**
         * raw 값을 이동평균에 반영한다.
         * 첫 [MAX_HISTORY] 개 샘플은 산술 평균, 그 이후는 (새 값 + 직전 평균) / 2 로 갱신.
         */
        @Synchronized
        fun trackMovingAverage(raw: Int) {
            if (recentRawValues.size < MAX_HISTORY) {
                recentRawValues.addFirst(raw)
                movingAverage = recentRawValues.average().toInt()
            } else {
                movingAverage = (raw + movingAverage) / 2
            }
        }

        /**
         * 스파이크 필터 + 출력 변환을 적용해 [GlucoseReading] 을 만든다.
         * 이동평균과 차이가 [spikeThreshold] 이상이면 null.
         *
         * 이 메서드는 이동평균을 갱신하지 않는다. 갱신이 필요하면 [trackMovingAverage] 를 먼저 호출.
         */
        fun applyFilterAndConvert(
            raw: Int,
            timestampMillis: Long,
        ): GlucoseReading? {
            if (abs(movingAverage - raw) >= spikeThreshold) return null

            val value =
                when (outputType) {
                    GlucoseOutputType.RAW -> raw
                    GlucoseOutputType.MG_DL ->
                        ((RAW_TO_MGDL_SLOPE * raw) - RAW_TO_MGDL_OFFSET).toInt() + correctVal
                }

            return GlucoseReading(
                timestampMillis = timestampMillis,
                valueMgDl = value,
            )
        }

        /**
         * 단일 패킷 → [GlucoseReading] 전체 파이프라인. 실시간 모드용 편의 메서드.
         * 유효하지 않은 패킷이거나 스파이크면 null.
         */
        fun parse(bytes: ByteArray): GlucoseReading? {
            val raw = extractRaw(bytes) ?: return null
            trackMovingAverage(raw)
            return applyFilterAndConvert(raw, System.currentTimeMillis())
        }

        /** 새 연결 시작 시 이동평균 상태만 초기화 (설정값은 유지). */
        @Synchronized
        fun reset() {
            recentRawValues.clear()
            movingAverage = 0
        }

        /** 한 번에 여러 설정을 적용. */
        fun applySettings(settings: BleProcessingSettings) {
            correctVal = settings.correctVal
            spikeThreshold = settings.spikeThreshold
            outputType = settings.outputType
        }

        private companion object {
            private const val PACKET_SIZE = 4
            private const val HEADER_BYTE_0: Byte = 0x2F
            private const val HEADER_BYTE_1: Byte = -1 // 0xFF (signed byte)
            private const val BYTE_MASK = 0xFF
            private const val MAX_HISTORY = 3
            private const val DEFAULT_SPIKE_THRESHOLD = 300
            private const val RAW_TO_MGDL_SLOPE = 0.048f
            private const val RAW_TO_MGDL_OFFSET = 37.93f
        }
    }
