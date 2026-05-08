package com.ssafy.s309.data.ble

/**
 * 패치에서 사용하는 BLE 3가지 칩셋(모듈) 별 UUID(식별자) 정의.
 *
 * 같은 "BLE 패치"여도 내부에 들어가는 칩셋이 다르면 service / notify / write UUID 가 모두 달라진다.
 * 스캔 결과에서 매칭되는 service UUID 로 [fromServiceUuid] 를 호출해 모듈을 결정한 뒤,
 * 해당 모듈의 [notifyUuid] / [writeUuid] 로 캐릭터리스틱에 접근한다.
 */
enum class BleModule(
    val serviceUuid: String,
    val notifyUuid: String,
    val writeUuid: String,
) {
    /** HM-17 칩셋. read/write 캐릭터리스틱이 동일한 UUID 를 공유한다. */
    HM_17(
        serviceUuid = "0000ffe0-0000-1000-8000-00805f9b34fb",
        notifyUuid = "0000ffe1-0000-1000-8000-00805f9b34fb",
        writeUuid = "0000ffe1-0000-1000-8000-00805f9b34fb",
    ),

    /** Nordic nLE521 칩셋. notify / write 캐릭터리스틱이 분리되어 있다. */
    NLE521(
        serviceUuid = "0000fff0-0000-1000-8000-00805f9b34fb",
        notifyUuid = "0000fff1-0000-1000-8000-00805f9b34fb",
        writeUuid = "0000fff2-0000-1000-8000-00805f9b34fb",
    ),

    /** Nordic nRF52 칩셋. */
    NRF52(
        serviceUuid = "00000001-1212-efde-1523-785fef13d123",
        notifyUuid = "00000003-1212-efde-1523-785fef13d123",
        writeUuid = "00000002-1212-efde-1523-785fef13d123",
    ),
    ;

    companion object {
        /**
         * 스캔된 기기가 광고하는 service UUID 로부터 어떤 모듈인지 판별한다.
         * 매칭되는 모듈이 없으면 null 을 반환한다.
         */
        fun fromServiceUuid(uuid: String): BleModule? = entries.find { it.serviceUuid.equals(uuid, ignoreCase = true) }
    }
}

/**
 * 패치로 인식할 BLE 기기 이름 prefix.
 *
 * BLE 스캔 결과에서 기기 이름이 이 목록 중 하나로 시작할 때만 패치 후보로 본다.
 * (원본 BLE+App+3.0-3 의 `Data.bleNames` 와 동일)
 */
object BleDeviceFilter {
    val NAME_PREFIXES: List<String> = listOf("LSK", "PWM", "BLE", "PHI", "LENS")

    /** 기기 이름이 패치 prefix 중 하나로 시작하는지 여부. */
    fun matches(deviceName: String?): Boolean {
        if (deviceName.isNullOrBlank()) return false
        return NAME_PREFIXES.any { deviceName.startsWith(it, ignoreCase = true) }
    }
}
