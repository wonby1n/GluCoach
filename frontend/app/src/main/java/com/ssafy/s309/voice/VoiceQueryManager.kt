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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * KikiChatScreen 마이크 버튼용 — 사용자가 명시적으로 트리거하는 1회성 STT.
 *
 * [WakeWordManager] 와 구분되는 점:
 *  - wake 패턴 매칭이 아니라 전체 발화 transcript 가 필요 (자유 질문)
 *  - 사용자 버튼 탭 → 한 세션만 listen → 결과 콜백 → 자동 종료
 *  - 시작 시 wake 루프를 stop()해 마이크 충돌 회피, 종료 시 start() 로 복귀
 *
 * 권한 없거나 STT 엔진이 없으면 onError 로 즉시 실패 통보.
 */
@Singleton
class VoiceQueryManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val wakeWordManager: WakeWordManager,
    ) {
        private val mainHandler = Handler(Looper.getMainLooper())
        private val listening = AtomicBoolean(false)

        @Volatile private var recognizer: SpeechRecognizer? = null

        private val _state = MutableStateFlow(State.IDLE)
        val state: StateFlow<State> = _state.asStateFlow()

        private val _partial = MutableStateFlow("")
        val partial: StateFlow<String> = _partial.asStateFlow()

        /**
         * 한 세션만 listen. STT 결과(또는 빈 transcript) 시 [onResult] 호출, 실패 시 [onError].
         * 이미 진행 중이면 무시.
         *
         * 권한이나 엔진이 없으면 즉시 [onError] 호출 후 종료.
         */
        fun startOnce(
            onResult: (String) -> Unit,
            onError: (FailureReason) -> Unit = {},
        ) {
            if (listening.getAndSet(true)) return

            if (!hasRecordAudioPermission()) {
                listening.set(false)
                onError(FailureReason.NO_PERMISSION)
                return
            }
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                listening.set(false)
                onError(FailureReason.NO_ENGINE)
                return
            }

            // wake 루프와 마이크 충돌 방지 — 일시 중단. 시스템 STT 서비스는 destroy 후 즉시
            // 정리되지 않아 곧바로 새 SR을 만들면 ERROR_SERVER_DISCONNECTED(11) 가 떨어진다.
            // → 잠깐 지연한 뒤 createSpeechRecognizer 호출.
            wakeWordManager.stop()
            _partial.value = ""
            _state.value = State.LISTENING

            scheduleStart(onResult, onError, attempt = 0)
        }

        private fun scheduleStart(
            onResult: (String) -> Unit,
            onError: (FailureReason) -> Unit,
            attempt: Int,
        ) {
            val delay = if (attempt == 0) WAKE_RELEASE_DELAY_MS else WAKE_RELEASE_RETRY_DELAY_MS
            mainHandler.postDelayed({
                if (!listening.get()) return@postDelayed
                val sr =
                    SpeechRecognizer.createSpeechRecognizer(context).also {
                        it.setRecognitionListener(buildListener(onResult, onError, attempt))
                        recognizer = it
                    }
                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toLanguageTag())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    }
                try {
                    sr.startListening(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "startListening 실패", e)
                    cleanup()
                    onError(FailureReason.START_FAILED)
                }
            }, delay)
        }

        /** 사용자가 화면을 떠나거나 다시 탭해서 취소할 때. 결과 콜백은 호출되지 않는다. */
        fun cancel() {
            if (!listening.get()) return
            mainHandler.post {
                runCatching { recognizer?.cancel() }
                cleanup()
            }
        }

        private fun buildListener(
            onResult: (String) -> Unit,
            onError: (FailureReason) -> Unit,
            attempt: Int,
        ): RecognitionListener =
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _state.value = State.PROCESSING
                }

                override fun onError(error: Int) {
                    Log.w(TAG, "STT 오류 code=$error attempt=$attempt")
                    // ERROR_SERVER_DISCONNECTED(11), ERROR_CLIENT(5), ERROR_RECOGNIZER_BUSY(8):
                    // wake SR 정리가 더디거나 system service 가 아직 점유 중이면 발생.
                    // 첫 시도면 한 번 더 지연 후 재시도.
                    if (attempt == 0 &&
                        (
                            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                                error == SpeechRecognizer.ERROR_CLIENT ||
                                error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                        )
                    ) {
                        Log.i(TAG, "재시도 — wake SR 정리 대기 후 startListening")
                        runCatching {
                            recognizer?.cancel()
                            recognizer?.destroy()
                        }
                        recognizer = null
                        scheduleStart(onResult, onError, attempt = 1)
                        return
                    }
                    cleanup()
                    val reason =
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                            -> FailureReason.NO_SPEECH
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> FailureReason.NO_PERMISSION
                            else -> FailureReason.RECOGNIZER_ERROR
                        }
                    onError(reason)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    val text = matches.firstOrNull().orEmpty().trim()
                    cleanup()
                    if (text.isEmpty()) {
                        onError(FailureReason.NO_SPEECH)
                    } else {
                        onResult(text)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                    _partial.value = matches.firstOrNull().orEmpty()
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) {}
            }

        private fun cleanup() {
            runCatching {
                recognizer?.stopListening()
                recognizer?.destroy()
            }.onFailure { Log.w(TAG, "SpeechRecognizer 정리 중 예외", it) }
            recognizer = null
            listening.set(false)
            _state.value = State.IDLE
            _partial.value = ""
            // wake 루프 복귀 — 권한이 그대로면 즉시 재시작, 없으면 no-op.
            wakeWordManager.start()
        }

        private fun hasRecordAudioPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        enum class State { IDLE, LISTENING, PROCESSING }

        enum class FailureReason {
            NO_PERMISSION,
            NO_ENGINE,
            NO_SPEECH,
            START_FAILED,
            RECOGNIZER_ERROR,
        }

        private companion object {
            const val TAG = "VoiceQueryManager"

            // wake SR.destroy() 후 system service 가 unbind 되기까지 약간의 시간 필요.
            // 300ms 면 대부분 충분. 짧으면 ERROR_SERVER_DISCONNECTED(11) 가 즉시 떨어진다.
            const val WAKE_RELEASE_DELAY_MS = 300L
            const val WAKE_RELEASE_RETRY_DELAY_MS = 600L
        }
    }
