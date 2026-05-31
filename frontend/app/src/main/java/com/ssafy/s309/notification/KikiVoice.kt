package com.ssafy.s309.notification

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 키키 음성 응답용 TTS. wake word ("Hi Kiki") 감지 후 "네 ㅇㅇ님!" 같은 짧은 응답을 발화한다.
 *
 * - 한국어 로케일 우선, 미지원 시 영어 폴백
 * - 미디어 오디오 스트림(STREAM_MUSIC) 으로 출력 — 알림 볼륨이 0/무음 모드여도 들리도록.
 *   Google Assistant / Siri 와 동일한 패턴. 과거 STREAM_NOTIFICATION 사용 시 시연장에서
 *   알림 볼륨 낮춰져 있어 키키 응답이 무음으로 재생되는 문제 있었음.
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
        private val mainHandler = Handler(Looper.getMainLooper())

        // 발화 완료를 기다리는 콜백 (utteranceId → onDone). 메인 스레드에서 실행.
        private val pendingCallbacks = ConcurrentHashMap<String, () -> Unit>()

        @Volatile private var tts: TextToSpeech? = null

        /** TTS 엔진을 lazy 하게 초기화한다. 음성 응답이 필요한 호출 지점에서 호출. */
        fun ensureInitialized() {
            if (tts != null) return
            tts =
                TextToSpeech(context) { status ->
                    if (status != TextToSpeech.SUCCESS) {
                        Log.e(TAG, "TTS 엔진 초기화 실패: status=$status")
                        return@TextToSpeech
                    }
                    val engine = tts ?: return@TextToSpeech
                    val ok = setLocale(Locale.KOREAN) || setLocale(Locale.US)
                    if (ok) {
                        engine.setOnUtteranceProgressListener(
                            object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) {}

                                override fun onDone(utteranceId: String?) {
                                    fireCallback(utteranceId)
                                }

                                @Suppress("OVERRIDE_DEPRECATION")
                                override fun onError(utteranceId: String?) {
                                    fireCallback(utteranceId)
                                }

                                override fun onError(
                                    utteranceId: String?,
                                    errorCode: Int,
                                ) {
                                    fireCallback(utteranceId)
                                }
                            },
                        )
                        isReady.set(true)
                        Log.i(TAG, "TTS 준비 완료")
                    } else {
                        Log.e(TAG, "TTS 로케일 미지원 (한/영 모두 실패)")
                    }
                }
        }

        private fun fireCallback(utteranceId: String?) {
            val cb = utteranceId?.let { pendingCallbacks.remove(it) } ?: return
            mainHandler.post(cb)
        }

        private fun setLocale(locale: Locale): Boolean {
            val engine = tts ?: return false
            val result = engine.setLanguage(locale)
            return result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }

        /**
         * "하이 키키" 감지 직후 키키가 "네, 부르셨어요?" 를 발화한다.
         * 발화가 끝나면 [onDone] 콜백이 메인 스레드에서 호출된다 — 호출 측에서 이후 단계
         * (예: 사용자 명령 listen) 로 넘어갈 트리거로 사용.
         *
         * TTS 미초기화/실패 등으로 발화 자체가 불가능한 경우에도 [onDone] 은 즉시 호출된다.
         */
        fun respondToWake(onDone: () -> Unit = {}) {
            ensureInitialized()
            speakWithCallback("네, 부르셨어요?", onDone)
        }

        /**
         * 임의의 메시지 음성 출력. STT 자유 발화 응답을 키키 목소리로 읽어줄 때 호출.
         * KikiChatScreen 이 음성 입력으로 보낸 query 에 대한 응답에만 사용 (텍스트 입력은 음성 출력 안 함).
         */
        fun speakMessage(text: String) {
            if (text.isBlank()) return
            ensureInitialized()
            speak(text)
        }

        private fun speak(text: String) {
            speakWithCallback(text, onDone = null)
        }

        private fun speakWithCallback(
            text: String,
            onDone: (() -> Unit)?,
        ) {
            val engine = tts
            if (engine == null) {
                Log.w(TAG, "TTS 미초기화 — ensureInitialized() 먼저 호출 필요")
                onDone?.let { mainHandler.post(it) }
                return
            }
            if (!isReady.get()) {
                Log.w(TAG, "TTS 미준비 — 발화 무시: $text")
                onDone?.let { mainHandler.post(it) }
                return
            }
            val spoken = sanitizeForTts(text)
            if (spoken.isBlank()) {
                onDone?.let { mainHandler.post(it) }
                return
            }
            val utteranceId = "kiki-${utteranceCounter.incrementAndGet()}"
            if (onDone != null) pendingCallbacks[utteranceId] = onDone
            val params =
                Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                }
            try {
                engine.speak(spoken, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
            } catch (e: Exception) {
                pendingCallbacks.remove(utteranceId)
                Log.e(TAG, "TTS speak 실패: $spoken", e)
                onDone?.let { mainHandler.post(it) }
            }
        }

        /** 현재 재생 중인 TTS를 즉시 중단. 엔진은 유지해 이후 발화에 재사용 가능. */
        fun stop() {
            pendingCallbacks.clear()
            runCatching { tts?.stop() }.onFailure { Log.w(TAG, "TTS stop 실패", it) }
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
