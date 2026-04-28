package com.ssafy.s309.data.ble

/**
 * 패치와의 BLE 연결 상태를 표현한다.
 *
 * UI 는 이 상태값만 구독해서 화면을 그리고, [BleManager] 는 BLE 콜백 결과에 따라
 * 이 값을 업데이트한다. sealed class 이므로 `when` 으로 모든 분기를 컴파일러가 강제한다.
 */
sealed class BleConnectionState {
    /** 아직 아무 동작도 시작하지 않은 초기 상태. */
    data object Idle : BleConnectionState()

    /** 주변 기기 스캔 중. 발견된 기기 목록은 [BleManager.scannedDevices] 에서 별도 관찰. */
    data object Scanning : BleConnectionState()

    /** 사용자가 선택한 기기에 연결 시도 중. */
    data class Connecting(val device: ScannedDevice) : BleConnectionState()

    /** 연결 성공. 어느 칩셋([module])과 통신 중인지 함께 보관한다. */
    data class Connected(
        val device: ScannedDevice,
        val module: BleModule,
    ) : BleConnectionState()

    /**
     * 연결이 끊어진 상태. 사용자가 명시적으로 끊었거나(reason = null), 통신 중 끊긴 경우.
     * [Idle] 과 달리 "이전에 연결돼있었음" 을 의미한다.
     */
    data class Disconnected(val reason: String? = null) : BleConnectionState()

    /** 스캔/연결/통신 중 복구 불가능한 오류. */
    data class Error(val message: String) : BleConnectionState()
}

/**
 * 스캔으로 발견한 BLE 기기 한 개의 정보.
 *
 * @property name 광고된 기기 이름. null 일 수 있음.
 * @property address MAC 주소 (예: "AA:BB:CC:DD:EE:FF"). 기기 식별 키로 사용.
 * @property rssi 신호 세기 (dBm, 음수). 0 에 가까울수록 강함.
 */
data class ScannedDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
)
