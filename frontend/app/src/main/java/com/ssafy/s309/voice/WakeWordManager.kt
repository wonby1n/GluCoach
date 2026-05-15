package com.ssafy.s309.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.s309.data.repository.UserRepository
import com.ssafy.s309.notification.KikiVoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "하이 키키" / "Hi Kiki" wake word 감지기.
 *
 * Android 내장 [SpeechRecognizer] 를 무한 루프로 돌려 partial transcript 에서
 * wake 패턴을 매칭한다. Picovoice 같은 외부 SDK / 가입 불필요.
 *
 * Lifecycle: [MainActivity] onStart/onStop 에서 [start]/[stop] 호출.
 *
 * 동작 특성:
 *  - 한 세션이 끝나면 (NO_MATCH, SPEECH_TIMEOUT 등) 즉시 재시작
 *  - wake 감지 시 [KikiVoice] 로 "네 ㅇㅇ님!" 발화. 발화 중에는 STT 일시 중단
 *    (자기 음성이 다시 인식되는 무한 루프 방지)
 *  - [COOLDOWN_MS] 이내 중복 트리거 무시
 *
 * 한계:
 *  - Google 온라인 STT 호출이므로 네트워크/배터리 소모 있음 (데모용)
 *  - 일부 디바이스에서 SR 세션 시작 시 짧은 비프음 발생 가능
 */
@Singleton
class WakeWordManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val userRepository: UserRepository,
        private val kikiVoice: KikiVoice,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val cachedName = AtomicReference<String?>(null)
        private val shouldKeepListening = AtomicBoolean(false)
        private val sessionActive = AtomicBoolean(false)

        @Volatile private var recognizer: SpeechRecognizer? = null

        @Volatile private var lastWakeAt: Long = 0L

        fun start() {
            if (shouldKeepListening.getAndSet(true)) return // 이미 동작 중

            if (!hasRecordAudioPermission()) {
                Log.w(TAG, "RECORD_AUDIO 권한 없음 — wake word 비활성화")
                shouldKeepListening.set(false)
                return
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.w(TAG, "기기에 음성 인식 엔진 미설치 — wake word 비활성화")
                shouldKeepListening.set(false)
                return
            }

            kikiVoice.ensureInitialized()
            refreshUserName()

            mainHandler.post { startSession() }
            Log.i(TAG, "Wake word 듣기 시작")
        }

        fun stop() {
            if (!shouldKeepListening.getAndSet(false)) return
            mainHandler.post {
                runCatching {
                    recognizer?.stopListening()
                    recognizer?.destroy()
                }.onFailure { Log.w(TAG, "SpeechRecognizer 정리 중 예외", it) }
                recognizer = null
                sessionActive.set(false)
            }
            Log.i(TAG, "Wake word 듣기 중지")
        }

        private fun startSession() {
            if (!shouldKeepListening.get()) return
            if (sessionActive.get()) return

            val sr =
                recognizer
                    ?: SpeechRecognizer.createSpeechRecognizer(context).also {
                        it.setRecognitionListener(buildListener())
                        recognizer = it
                    }

            val intent =
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

            try {
                sessionActive.set(true)
                sr.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startListening 실패", e)
                sessionActive.set(false)
                scheduleRestart(RESTART_DELAY_MS)
            }
        }

        private fun scheduleRestart(delayMs: Long) {
            if (!shouldKeepListening.get()) return
            mainHandler.postDelayed({
                sessionActive.set(false)
                startSession()
            }, delayMs)
        }

        private fun buildListener(): RecognitionListener =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    sessionActive.set(false)
                    // NO_MATCH / SPEECH_TIMEOUT 은 정상 종료 패턴 — 빠르게 재시작
                    val delay =
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            -> SHORT_RESTART_MS
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 600L
                            SpeechRecognizer.ERROR_CLIENT -> 600L
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                Log.e(TAG, "권한 부족 — wake word 정지")
                                shouldKeepListening.set(false)
                                return
                            }
                            else -> RESTART_DELAY_MS
                        }
                    scheduleRestart(delay)
                }

                override fun onResults(results: Bundle?) {
                    sessionActive.set(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    checkForWakeWord(matches)
                    scheduleRestart(RESTART_DELAY_MS)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    if (checkForWakeWord(matches)) {
                        // partial 에서 매칭되면 굳이 final 까지 기다리지 않고 세션을 끊는다
                        runCatching { recognizer?.stopListening() }
                    }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) {}
            }

        /** 매칭되면 true 반환 + KikiVoice 발동. 쿨다운 중이면 false. */
        private fun checkForWakeWord(transcripts: List<String>): Boolean {
            val now = System.currentTimeMillis()
            if (now - lastWakeAt < COOLDOWN_MS) return false

            for (t in transcripts) {
                if (matchesWakeWord(t)) {
                    lastWakeAt = now
                    Log.i(TAG, "Wake word 감지: \"$t\"")
                    kikiVoice.respondToWake(cachedName.get())
                    pauseDuringResponse()
                    return true
                }
            }
            return false
        }

        private fun matchesWakeWord(transcript: String): Boolean {
            val normalized =
                transcript
                    .lowercase(Locale.KOREAN)
                    .replace(Regex("[\\s\\p{Punct}]+"), "")
            return WAKE_PATTERNS.any { it in normalized }
        }

        private fun pauseDuringResponse() {
            runCatching { recognizer?.stopListening() }
            sessionActive.set(false)
            // TTS 발화 시간 만큼 STT 잠시 중단 → 자기 음성 재인식 방지
            scheduleRestart(RESPONSE_PAUSE_MS)
        }

        private fun refreshUserName() {
            scope.launch {
                userRepository.getSettings()
                    .onSuccess { settings -> cachedName.set(settings.name) }
                    .onFailure { Log.w(TAG, "사용자 이름 조회 실패", it) }
            }
        }

        private fun hasRecordAudioPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        private companion object {
            const val TAG = "WakeWordManager"
            const val SHORT_RESTART_MS = 100L
            const val RESTART_DELAY_MS = 300L
            const val RESPONSE_PAUSE_MS = 2_500L
            const val COOLDOWN_MS = 3_000L

            // 한국어 STT 가 "Hi Kiki" / "하이 키키" 를 transcribe 할 때의 변형들.
            // 모두 공백/구두점 제거 후 소문자로 비교한다.
            val WAKE_PATTERNS =
                listOf(
                    "하이키키",
                    "하이키",
                    "hikiki",
                    "haikiki",
                    "키키야",
                )
        }
    }
