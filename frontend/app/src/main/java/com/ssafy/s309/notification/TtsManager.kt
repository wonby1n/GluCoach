package com.ssafy.s309.notification

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 키키(AI agent) 알림용 한국어 TTS 매니저.
 *
 * - 앱 시작 시 S309Application에서 @Inject 로 사전 인스턴스화되어 TTS 엔진을 비동기 초기화한다.
 *   첫 알림이 도착할 때는 보통 isReady=true.
 * - 한국어 데이터 미설치 기기에서는 영어로 폴백, 둘 다 미지원이면 발화를 시도하지 않고 로그만 남긴다.
 * - 알림 오디오 스트림을 사용해 무음/방해금지 모드를 존중.
 * - urgent=true: 진행 중인 발화를 끊고 즉시 발화 / urgent=false: 큐에 누적.
 */
@Singleton
class TtsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Volatile var isEnabled: Boolean = true

        private val isReady = AtomicBoolean(false)
        private val utteranceCounter = AtomicLong(0)

        private val tts: TextToSpeech =
            TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    Log.e(TAG, "TTS 엔진 초기화 실패: status=$status")
                    return@TextToSpeech
                }
                val ok = setLocale(Locale.KOREAN) || setLocale(Locale.US)
                if (ok) {
                    isReady.set(true)
                } else {
                    Log.e(TAG, "TTS 로케일 미지원 (한/영 모두 실패)")
                }
            }

        private fun setLocale(locale: Locale): Boolean {
            val result = tts.setLanguage(locale)
            return result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }

        fun speak(
            text: String,
            urgent: Boolean = false,
        ) {
            if (!isEnabled || text.isBlank()) return
            if (!isReady.get()) {
                Log.w(TAG, "TTS 미준비 — 발화 무시: $text")
                return
            }
            val spoken = sanitizeForTts(text)
            if (spoken.isBlank()) return
            val mode = if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = "kiki-${utteranceCounter.incrementAndGet()}"
            val params =
                Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
                }
            try {
                tts.speak(spoken, mode, params, utteranceId)
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak 실패: $spoken", e)
            }
        }

        fun stop() {
            runCatching { tts.stop() }.onFailure { Log.e(TAG, "TTS stop 실패", it) }
        }

        fun shutdown() {
            isReady.set(false)
            runCatching {
                tts.stop()
                tts.shutdown()
            }.onFailure { Log.e(TAG, "TTS shutdown 실패", it) }
        }

        private companion object {
            const val TAG = "TtsManager"
        }
    }
