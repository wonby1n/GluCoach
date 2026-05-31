package com.ssafy.s309.notification

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.ssafy.s309.voice.WakeWordManager
import dagger.Lazy
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
 * - STREAM_MUSIC 사용 — 미디어 볼륨으로 들림.
 * - urgent=true: 진행 중인 발화를 끊고 즉시 발화 / urgent=false: 큐에 누적.
 *
 * **Wake word 간섭 차단:** TTS 가 스피커로 출력되는 동안 [WakeWordManager.setExternalTtsActive]
 * 로 wake 게이트를 닫아, Vosk 가 자기 음향을 "Hi Kiki" 로 잘못 잡지 않도록 한다.
 * 종료 시 잔향 보호 (tail guard) 후 자동 해제.
 *
 * **Lazy 주입 이유:** [WakeWordManager] 가 [KikiVoice] 를 받고, KikiVoice 와 동시 초기화
 * 경로 상 순환 우려가 약하게 있어 안전하게 Lazy 로 래핑. Hilt 가 사용 시점에 해결.
 */
@Singleton
class TtsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val wakeWordManagerLazy: Lazy<WakeWordManager>,
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
                    // wake word 간섭 차단을 위한 UtteranceProgressListener.
                    // onStart → wake 게이트 닫음, onDone/onError → tail guard 후 다시 염.
                    tts.setOnUtteranceProgressListener(
                        object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                wakeWordManagerLazy.get().setExternalTtsActive(true)
                            }

                            override fun onDone(utteranceId: String?) {
                                wakeWordManagerLazy.get().setExternalTtsActive(false)
                            }

                            @Suppress("OVERRIDE_DEPRECATION")
                            override fun onError(utteranceId: String?) {
                                wakeWordManagerLazy.get().setExternalTtsActive(false)
                            }

                            override fun onError(
                                utteranceId: String?,
                                errorCode: Int,
                            ) {
                                wakeWordManagerLazy.get().setExternalTtsActive(false)
                            }
                        },
                    )
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
            // STREAM_MUSIC: 알림 스트림 볼륨이 0/낮게 설정된 단말에서도 미디어 볼륨으로 들리도록
            // 강제. Google Assistant / Siri 와 동일.
            val params =
                Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
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
