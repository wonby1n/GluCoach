package com.ssafy.s309.data.ble

import android.content.Context
import com.ssafy.s309.data.model.GlucoseReading
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 수신된 [GlucoseReading] 을 기기별 로그 파일에 기록하고, 다시 로드한다.
 *
 * 원본 BLE+App+3.0-3 의 `writeTextFile` / `loadTextFile` 를 모던 안드로이드 환경에 맞춰 포팅한 것.
 * 원본은 `/sdcard/BiomedLog/` 를 사용했지만, 안드로이드 10+ 의 scoped storage 에 따라
 * 앱 내부 저장소(`context.filesDir/BiomedLog/`) 로 옮겼다 (별도 권한 불필요).
 *
 * 파일 한 줄 형식: `<epochMillis> <value>` (공백 구분).
 * 원본은 `MM:dd:HH:mm:ss <value>` 였지만 연도 정보가 없어서 모호했고,
 * epoch ms 가 정렬/파싱이 단순해 사용한다.
 */
@Singleton
class BleDataLogger
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val logsDir: File by lazy {
            File(context.filesDir, LOG_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
        }

        /**
         * 한 줄을 기기별 로그 파일 끝에 추가. 파일이 없으면 생성한다.
         * I/O 실패는 호출자에게 전파하지 않고 무시한다 (로그 손실은 기능에 치명적이지 않음).
         */
        @Synchronized
        fun append(
            deviceName: String,
            reading: GlucoseReading,
        ) {
            runCatching {
                logFile(deviceName).appendText(
                    "${reading.timestampMillis} ${reading.valueMgDl}\n",
                )
            }
        }

        /**
         * 기기별 로그 파일을 모두 로드해 [GlucoseReading] 리스트로 반환.
         * 파일이 없거나 읽기 실패 시 빈 리스트.
         */
        fun loadAll(deviceName: String): List<GlucoseReading> {
            val file = logFile(deviceName)
            if (!file.exists()) return emptyList()

            return runCatching {
                file
                    .readLines()
                    .mapNotNull { parseLine(it) }
            }.getOrDefault(emptyList())
        }

        /** 기기별 로그 파일 삭제 (그래프 초기화 메뉴에서 사용). */
        fun clear(deviceName: String) {
            runCatching { logFile(deviceName).delete() }
        }

        private fun logFile(deviceName: String): File {
            // 파일명에 위험한 문자가 들어가지 않도록 sanitize.
            val safeName = deviceName.replace(UNSAFE_FILENAME_REGEX, "_")
            return File(logsDir, "$safeName.txt")
        }

        private fun parseLine(line: String): GlucoseReading? {
            val parts = line.trim().split(" ")
            if (parts.size != 2) return null
            val timestamp = parts[0].toLongOrNull() ?: return null
            val value = parts[1].toIntOrNull() ?: return null
            return GlucoseReading(timestampMillis = timestamp, valueMgDl = value)
        }

        private companion object {
            private const val LOG_DIR_NAME = "BiomedLog"
            private val UNSAFE_FILENAME_REGEX = Regex("[^A-Za-z0-9._-]")
        }
    }
