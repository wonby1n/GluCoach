package com.ssafy.s309.data.ble

/**
 * BLE 패치 통신/처리에 사용되는 모든 튜닝값과 사양값을 한 곳에 모은 단일 진실 공급원.
 *
 * 카테고리별 의미:
 * - [Protocol]: 패치 펌웨어가 정한 알림 패킷 사양. 펌웨어가 바뀌면 같이 바꿔야 함.
 * - [Processing]: 앱 측 데이터 처리/표시 정책. UX 튜닝 시 이 값들을 만진다.
 * - [Connection]: FastBLE 라이브러리 동작 파라미터.
 * - [Frequency]: RF 주파수 제어 명령 변환식 + 유효 범위. 펌웨어 스펙.
 *
 * 이 객체에 모든 튜닝값을 모아두는 이유는 한 화면에서 BLE 동작 전체를 조정할 수 있도록
 * 하기 위함이다. 새 상수가 필요하면 여기 추가하고, 기존 코드는 같은 값을 직접 박지 말고
 * 이 객체를 참조해야 한다.
 */
object BleConfig {
    /** 패치가 보내는 알림 패킷의 형식과 raw → mg/dL 변환식. */
    object Protocol {
        /** 알림 패킷 길이(바이트). */
        const val PACKET_SIZE = 4

        /** 알림 패킷 첫 바이트(0x2F). */
        const val HEADER_BYTE_0: Byte = 0x2F

        /** 알림 패킷 두 번째 바이트(0xFF, signed = -1). */
        const val HEADER_BYTE_1: Byte = -1

        /** raw → mg/dL 변환식: `mgDl = SLOPE * raw - OFFSET + correctVal`. */
        const val RAW_TO_MGDL_SLOPE = 0.048f
        const val RAW_TO_MGDL_OFFSET = 37.93f
    }

    /** 앱 측 데이터 처리 정책. UX 튜닝 시 여기를 만진다. */
    object Processing {
        /** raw 값 이동평균 윈도우 크기. 첫 N개는 산술평균, 그 이후는 (새값 + 직전 평균) / 2. */
        const val MOVING_AVERAGE_WINDOW = 3

        /** 이동평균과 차이가 이 값(raw 단위) 이상이면 노이즈로 간주해 drop. */
        const val DEFAULT_SPIKE_THRESHOLD_RAW = 300

        /**
         * 화면 emit 주기(초). 사용자가 런타임에 변경 가능.
         * - 0: 패치가 보내는 매 valid 패킷마다 즉시 emit (실시간)
         * - >0: N초 동안 raw들을 모았다가 평균값을 1번 emit
         *
         * 현재 개발 단계 기본: 0 (실시간). 출시 시 60(1분 평균) 등으로 조정 가능.
         */
        const val DEFAULT_DISPLAY_GATHER_SECONDS = 0

        /**
         * DB 저장용 평균 emit 주기(초). 표시 주기와 별개로 항상 동작.
         * 백엔드 업로드 / AI 예측 입력으로 쓰일 값을 만든다.
         *
         * 시중 CGM 표준에 맞춰 5분(300초). DB 스키마/백엔드 API 확정 시 collect 시작.
         */
        const val DB_AGGREGATION_INTERVAL_SECONDS = 300

        /**
         * BleManager 가 메모리에 보관하는 최근 측정값 개수.
         * 표시 주기에 따라 시간 길이가 달라짐 (1초 주기면 100초, 60초 주기면 100분).
         */
        const val IN_MEMORY_HISTORY_SIZE = 100

        /**
         * 생리학적으로 유효한 raw 값 범위 (correctVal 미반영).
         * 변환식 `mgDl = SLOPE * raw - OFFSET` 기준:
         * - MIN: 20 mg/dL → raw ≈ 1_207
         * - MAX: 600 mg/dL → raw ≈ 13_290
         * 이 범위를 벗어나면 센서 오류로 판단해 이동평균 반영 전에 드롭한다.
         */
        const val MIN_VALID_RAW = 1_207
        const val MAX_VALID_RAW = 13_290
    }

    /** FastBLE 라이브러리 동작 파라미터. */
    object Connection {
        const val RECONNECT_INTERVAL_MS = 5_000L
        const val OPERATE_TIMEOUT_MS = 5_000
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val SCAN_TIMEOUT_MS = 10_000L
        const val SPLIT_WRITE_NUM = 20

        /** notify 등록 실패 시 재시도 횟수. 모두 실패 시 disconnect. */
        const val MAX_NOTIFY_RETRY = 2

        /** notify 재시도 간 대기 시간(ms). */
        const val NOTIFY_RETRY_DELAY_MS = 500L
    }

    /** RF 주파수 제어 명령(`0xA2`) 변환식 + 유효 범위. */
    object Frequency {
        const val MIN_MHZ = 300
        const val MAX_MHZ = 960

        /** freq 레지스터 값 = `mhz * NUMERATOR / DENOMINATOR`. */
        const val SCALE_NUMERATOR = 65536
        const val SCALE_DENOMINATOR = 16

        /** 주파수 자동 스캔(`runFrequencyScan`) 시 최소 간격(ms). */
        const val MIN_SCAN_INTERVAL_MS = 200L
    }
}
