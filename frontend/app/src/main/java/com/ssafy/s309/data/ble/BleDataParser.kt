package com.ssafy.s309.data.ble

import com.ssafy.s309.data.model.GlucoseReading
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * 패치에서 도착하는 4바이트 패킷을 [GlucoseReading] 으로 변환한다.
 *
 * 처리 파이프라인은 3단계로 분리되어 있다:
 * 1. [extractRaw] — 패킷 검증 + 16비트 raw 값 추출 + 생리학적 범위 체크
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
        var spikeThreshold: Int = BleConfig.Processing.DEFAULT_SPIKE_THRESHOLD_RAW

        /** raw / mg-dL 출력 모드. */
        @Volatile
        var outputType: GlucoseOutputType = GlucoseOutputType.MG_DL

        // ────────────────────────────────────────
        // 이동평균 상태 (연결 단위)
        // ────────────────────────────────────────

        private val recentRawValues = ArrayDeque<Int>(BleConfig.Processing.MOVING_AVERAGE_WINDOW)

        @Volatile
        private var movingAverage: Int = 0

        /**
         * 이동평균이 한 번이라도 실제 raw 값으로 갱신됐는지 여부.
         * false 인 동안엔 [applyFilterAndConvert] 가 첫 raw 값을 [movingAverage] 로 강제 세팅해
         * 호출 순서를 잘못 지키더라도 첫 패킷이 스파이크 필터에 무조건 걸리지 않도록 한다.
         */
        @Volatile
        private var hasReceivedAnyData: Boolean = false

        // ────────────────────────────────────────
        // 단계별 메서드
        // ────────────────────────────────────────

        /**
         * 바이트 패킷을 검증하고 16비트 raw 값을 추출한다.
         * 길이가 [PACKET_SIZE] 가 아니거나 헤더(`0x2F 0xFF`)가 다르면 null.
         * raw 값이 생리학적 유효 범위([BleConfig.Processing.MIN_VALID_RAW]..[BleConfig.Processing.MAX_VALID_RAW])
         * 를 벗어나면 null — 이동평균 오염을 막기 위해 [trackMovingAverage] 호출 전에 드롭한다.
         */
        fun extractRaw(bytes: ByteArray): Int? {
            if (bytes.size != BleConfig.Protocol.PACKET_SIZE) return null
            if (bytes[0] != BleConfig.Protocol.HEADER_BYTE_0 ||
                bytes[1] != BleConfig.Protocol.HEADER_BYTE_1
            ) {
                return null
            }
            val raw =
                ((bytes[2].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or
                    (bytes[3].toInt() and BYTE_MASK)
            if (raw < BleConfig.Processing.MIN_VALID_RAW || raw > BleConfig.Processing.MAX_VALID_RAW) return null
            return raw
        }

        /**
         * raw 값을 이동평균에 반영한다.
         * 첫 [BleConfig.Processing.MOVING_AVERAGE_WINDOW] 개 샘플은 산술 평균,
         * 그 이후는 (새 값 + 직전 평균) / 2 로 갱신.
         */
        @Synchronized
        fun trackMovingAverage(raw: Int) {
            if (recentRawValues.size < BleConfig.Processing.MOVING_AVERAGE_WINDOW) {
                recentRawValues.addFirst(raw)
                movingAverage = recentRawValues.average().toInt()
            } else {
                movingAverage = (raw + movingAverage) / 2
            }
            hasReceivedAnyData = true
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
            // 방어적 처리: 호출 순서가 잘못되어 [trackMovingAverage] 가 한 번도 호출되지 않은
            // 상태로 진입하면 movingAverage 가 0 이라 첫 raw 가 반드시 스파이크로 판정된다.
            // 그 경우엔 첫 raw 값으로 movingAverage 를 초기화해 필터를 통과시킨다.
            if (!hasReceivedAnyData) {
                movingAverage = raw
                hasReceivedAnyData = true
            }
            if (abs(movingAverage - raw) >= spikeThreshold) return null

            val value =
                when (outputType) {
                    GlucoseOutputType.RAW -> raw
                    GlucoseOutputType.MG_DL ->
                        (
                            (BleConfig.Protocol.RAW_TO_MGDL_SLOPE * raw) -
                                BleConfig.Protocol.RAW_TO_MGDL_OFFSET
                        ).toInt() + correctVal
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
            hasReceivedAnyData = false
        }

        /** 한 번에 여러 설정을 적용. */
        fun applySettings(settings: BleProcessingSettings) {
            correctVal = settings.correctVal
            spikeThreshold = settings.spikeThreshold
            outputType = settings.outputType
        }

        private companion object {
            /** 비트 마스크 (signed byte → unsigned int 변환용). 파서 내부 전용 상수. */
            private const val BYTE_MASK = 0xFF
        }
    }
