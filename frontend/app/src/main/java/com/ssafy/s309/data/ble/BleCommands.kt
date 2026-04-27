package com.ssafy.s309.data.ble

/**
 * 패치에 보낼 제어 명령 패킷의 첫 바이트(명령 코드).
 *
 * 모든 제어 명령은 4바이트 패킷이며 첫 바이트가 명령 종류를 나타낸다.
 * 나머지 바이트의 의미는 명령마다 다르다.
 */
object BleCommandHeader {
    /** 렌즈 옵션 비트마스크. 패킷: `[0xA1, mask, 0x00, 0x00]` */
    const val LENS_OPTION: Byte = -95 // 0xA1

    /** RF 주파수. 패킷: `[0xA2, val2, val1, val0]` (3바이트 freq 값) */
    const val FREQUENCY: Byte = -94 // 0xA2

    /** RF 출력 강도. 패킷: `[0xA3, level, 0x00, 0x00]` */
    const val POWER: Byte = -93 // 0xA3

    /** RF ON/OFF 동작 모드. 패킷: `[0xA4, mode, 0x00, 0x00]` */
    const val RF_ON_OFF: Byte = -92 // 0xA4
}

/**
 * 렌즈 옵션 1바이트 비트마스크.
 *
 * 8개 옵션을 비트별로 OR 연산해 단일 바이트로 만든 뒤
 * `[0xA1, mask, 0x00, 0x00]` 패킷으로 전송한다.
 */
enum class LensOptionFlag(
    val bit: Int,
) {
    DDS_SEL_0(0b0000_0001),
    DDS_SEL_1(0b0000_0010),
    DDS_SEL_2(0b0000_0100),
    TIME_0(0b0000_1000),
    TIME_1(0b0001_0000),
    LED_ON(0b0010_0000),
    IOP_RES_TOGGLE(0b0100_0000),
    CURRENT_OP(0b1000_0000),
    ;

    companion object {
        /** 켜진 플래그 집합을 1바이트 비트마스크로 합산. */
        fun toMask(flags: Set<LensOptionFlag>): Byte =
            flags
                .fold(0) { acc, flag -> acc or flag.bit }
                .toByte()
    }
}

/**
 * RF 동작 모드. 단일 바이트로 전송 (`[0xA4, raw, 0x00, 0x00]`).
 */
enum class RfOnOffMode(
    val rawValue: Byte,
) {
    /** 상시 ON */
    ON(0x00),

    /** OFF */
    OFF(0x01),

    /** 10Hz 주기 스캔 */
    HZ_10(0x02),

    /** 20Hz 주기 스캔 */
    HZ_20(0x03),

    /** 40Hz 주기 스캔 */
    HZ_40(0x04),
}

/**
 * RF 출력 강도. 단일 바이트로 전송 (`[0xA3, raw, 0x00, 0x00]`).
 */
enum class RfPowerLevel(
    val rawValue: Byte,
) {
    LOW(0x05),
    NORMAL(0x06),
    MAX(0x07),
}

/**
 * 혈당 데이터 출력 형식. 파서가 어떤 값을 [GlucoseReading] 으로 내보낼지 결정한다.
 */
enum class GlucoseOutputType {
    /** 센서 raw 값 그대로. 디버깅/연구용. */
    RAW,

    /** mg/dL 로 변환된 혈당 값. 일반 사용자에게 표시되는 값. */
    MG_DL,
}
