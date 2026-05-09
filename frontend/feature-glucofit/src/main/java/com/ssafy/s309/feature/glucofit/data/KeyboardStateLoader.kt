package com.ssafy.s309.feature.glucofit.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File

/**
 * IME 측 컨텍스트 데이터 로더 — 메시지 생성에 필요한 사용자 상태를 메인 앱에서 받음.
 *
 * filesDir/keyboard_state.json (app 모듈이 갱신) → 메모리 캐시 없음, 매번 읽음 (작아서 무관).
 *
 * <p>현재 사용:
 *   - lastMealAtMs: 마지막 식사 시각 (epoch millis). null이면 식사 기록 없음 → OVER_2H 취급.
 *   - 글루코스는 GlucoseSimulator에서 별도로 조회.
 *
 * 파일 없으면 기본값(없음) 반환 — IME는 그 가정 하에 동작.
 */
object KeyboardStateLoader {
    private const val TAG = "KeyboardStateLoader"
    private const val FILE_NAME = "keyboard_state.json"

    data class State(val lastMealAtMs: Long? = null)

    fun read(context: Context): State {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return State()
        return runCatching {
            Gson().fromJson(file.readText(), State::class.java) ?: State()
        }.getOrElse {
            Log.w(TAG, "parse failed", it)
            State()
        }
    }
}
