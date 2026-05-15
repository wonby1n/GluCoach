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
 * 키키 음성 응답용 TTS. wake word ("Hi Kiki") 감지 후 "네 ㅇㅇ님!" 같은 짧은 응답을 발화한다.
 *
 * - 한국어 로케일 우선, 미지원 시 영어 폴백
 * - 알림 오디오 스트림으로 출력해 무음/방해금지 모드 존중
 * - TextToSpeech 엔진 초기화는 비동기. ensureInitialized() 호출 후 isReady=true 가 될 때까지 발화 무시
 */
@Singleton
class KikiVoice
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val isReady = AtomicBoolean(false)
        private val utteranceCounter = AtomicLong(0)

        @Volatile private var tts: TextToSpeech? = null

        /** TTS 엔진을 lazy 하게 초기화한다. WakeWordManager.start() 에서 호출. */
        fun ensureInitialized() {
            if (tts != null) return
            tts =
                TextToSpeech(context) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        Log.e(TAG, "TTS 엔진 초기화 실패: status=$status")
                        return@TextToSpeech
                    }
                    val ok = setLocale(Locale.KOREAN) || setLocale(Locale.US)
                    if (ok) {
                        isReady.set(true)
                        Log.i(TAG, "TTS 준비 완료")
                    } else {
                        Log.e(TAG, "TTS 로케일 미지원 (한/영 모두 실패)")
                    }
                }
        }

        private fun setLocale(locale: Locale): Boolean {
            val engine = tts ?: return false
            val result = engine.setLanguage(locale)
            return result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }

        /** "네 ${userName ?: "사용자"}님!" 을 발화. wake 감지 콜백에서 호출. */
        fun respondToWake(userName: String?) {
            val name = userName?.trim().takeUnless { it.isNullOrEmpty() } ?: "사용자"
            speak("네, ${name}님!")
        }

        private fun speak(text: String) {
            val engine = tts
            if (engine == null) {
                Log.w(TAG, "TTS 미초기화 — ensureInitialized() 먼저 호출 필요")
                return
            }
            if (!isReady.get()) {
                Log.w(TAG, "TTS 미준비 — 발화 무시: $text")
                return
            }
            val utteranceId = "kiki-${utteranceCounter.incrementAndGet()}"
            val params =
                Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_NOTIFICATION)
                }
            try {
                engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } catch (e: Exception) {
                Log.e(TAG, "TTS speak 실패: $text", e)
            }
        }

        fun shutdown() {
            isReady.set(false)
            runCatching {
                tts?.stop()
                tts?.shutdown()
            }.onFailure { Log.e(TAG, "TTS shutdown 실패", it) }
            tts = null
        }

        private companion object {
            const val TAG = "KikiVoice"
        }
    }
