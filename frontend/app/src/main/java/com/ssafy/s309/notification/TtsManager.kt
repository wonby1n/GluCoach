package com.ssafy.s309.notification

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 앱 전역 TTS 싱글톤.
 *
 * - [speak] 호출 시 엔진 초기화가 끝나지 않았으면 내부 큐에 적재 후 준비되면 순서대로 재생.
 * - [isEnabled] 를 false 로 설정하면 전역 음소거.
 * - urgent = true 이면 현재 재생 중인 말을 끊고 즉시 재생 (저혈당 긴급 알림용).
 */
@Singleton
class TtsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        @Volatile var isEnabled: Boolean = true

        private var tts: TextToSpeech? = null
        private var isReady = false
        private val pendingQueue = ArrayDeque<Pair<String, Boolean>>()
        private val lock = Any()

        init {
            tts =
                TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val result = tts?.setLanguage(Locale.KOREAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.w(TAG, "한국어 TTS 미지원 — 기본 언어 사용")
                            tts?.setLanguage(Locale.getDefault())
                        }
                        synchronized(lock) {
                            isReady = true
                            drainQueue()
                        }
                    } else {
                        Log.e(TAG, "TTS 초기화 실패: status=$status")
                    }
                }
        }

        /**
         * 텍스트를 음성으로 재생.
         *
         * @param text 읽을 내용 (이모지는 자동 제거됨)
         * @param urgent true 이면 현재 재생 중인 내용을 끊고 즉시 재생
         */
        fun speak(
            text: String,
            urgent: Boolean = false,
        ) {
            if (!isEnabled) return
            val clean = stripEmoji(text)
            if (clean.isBlank()) return

            synchronized(lock) {
                if (isReady) {
                    doSpeak(clean, urgent)
                } else {
                    pendingQueue.addLast(Pair(clean, urgent))
                }
            }
        }

        fun stop() {
            tts?.stop()
        }

        private fun doSpeak(
            text: String,
            urgent: Boolean,
        ) {
            val queueMode = if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(text, queueMode, null, null)
        }

        private fun drainQueue() {
            while (pendingQueue.isNotEmpty()) {
                val (text, urgent) = pendingQueue.removeFirst()
                doSpeak(text, urgent)
            }
        }

        private fun stripEmoji(text: String): String =
            text
                .replace(Regex("\\p{So}|\\p{Cs}"), "")
                .replace(Regex("\\s{2,}"), " ")
                .trim()

        private companion object {
            const val TAG = "TtsManager"
        }
    }
