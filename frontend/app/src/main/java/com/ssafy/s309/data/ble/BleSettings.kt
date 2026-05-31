package com.ssafy.s309.data.ble

/**
 * 수신된 BLE 데이터 처리 방식에 대한 설정.
 *
 * 모든 값은 사용자가 런타임에 변경할 수 있다 (원본 앱의 데이터 다이얼로그와 동일).
 * 변경 시 즉시 [BleDataParser] 동작에 반영된다.
 *
 * @property correctVal mg/dL 변환 후 더해지는 사용자 보정 오프셋
 * @property spikeThreshold 이동평균과 차이가 이 값 이상이면 노이즈로 간주해 버린다
 * @property outputType raw 값을 그대로 내보낼지, mg/dL 로 변환할지
 * @property gatherIntervalSeconds 0 이면 실시간 emit, 양수면 해당 초마다 평균값 emit
 */
data class BleProcessingSettings(
    val correctVal: Int = 0,
    val spikeThreshold: Int = BleConfig.Processing.DEFAULT_SPIKE_THRESHOLD_RAW,
    val outputType: GlucoseOutputType = GlucoseOutputType.MG_DL,
    val gatherIntervalSeconds: Int = BleConfig.Processing.DEFAULT_DISPLAY_GATHER_SECONDS,
)

/**
 * 패치 자체의 동작을 제어하는 마지막 설정값 스냅샷.
 *
 * 설정은 사용자가 다이얼로그에서 변경 → BLE write 로 패치에 전송될 때 함께 갱신된다.
 * UI 가 다이얼로그를 열 때 현재 값을 보여주는 용도로도 사용한다.
 *
 * 기본값은 원본 앱의 초기 상태와 동일.
 */
data class BlePatchControlSettings(
    val frequencyMhz: Int = 434,
    val rfOnOffMode: RfOnOffMode = RfOnOffMode.ON,
    val rfPowerLevel: RfPowerLevel = RfPowerLevel.MAX,
    val lensOptions: Set<LensOptionFlag> =
        setOf(
            LensOptionFlag.DDS_SEL_1,
            LensOptionFlag.DDS_SEL_2,
        ),
)
