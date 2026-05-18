package com.ssafy.s309.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.notification.KikiVoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "하이 키키" / "Hi Kiki" wake word 감지기 — Vosk(오프라인 한국어 STT) 기반.
 *
 * Lifecycle: [com.ssafy.s309.MainActivity] onStart/onStop 에서 [start]/[stop] 호출.
 *
 * 동작 흐름 ("Hi Bixby" 와 동일한 UX):
 *  1. 단일 [SpeechService] 가 마이크를 한 번만 잡고 끝까지 유지 → 시스템 시작/종료
 *     효과음("띠롱") 발생 안 함.
 *  2. partial transcript 가 "하이 키키" 매칭 → [KikiVoice] 로 "네, 부르셨어요?" 발화
 *  3. TTS 종료 콜백 → mode = COMMAND, recognizer reset → 다음 final result 캡처
 *  4. 명령 transcript 를 [HealthRepository.sendFoodRecommendCommand] 로 흘려 보냄
 *     → 백엔드가 AI 응답을 FCM 으로 push, FcmService 가 본문을 TTS 로 재생
 *  5. mode = WAKE 로 복귀 → 1번부터 루프
 *
 * 자기 TTS 가 wake/명령 으로 잘못 들리는 무한 루프는 [isTtsSpeaking] 플래그로 차단.
 *
 * 모델: `app/src/main/assets/model-ko/` (Vosk small Korean model, ~82MB).
 * 첫 실행 시 [StorageService.unpack] 가 internal storage 로 압축 해제 (2~3초).
 *
 * 한계: small 모델 정확도는 Porcupine 보다 떨어지지만 짧은 wake word 는 잘 잡힘.
 */
@Singleton
class WakeWordManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val kikiVoice: KikiVoice,
        private val healthRepository: HealthRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mainHandler = Handler(Looper.getMainLooper())

        private val shouldKeepListening = AtomicBoolean(false)
        private val isTtsSpeaking = AtomicBoolean(false)

        private enum class Mode { WAKE, COMMAND }

        @Volatile private var mode: Mode = Mode.WAKE

        // ── UI 노출 상태 ─────────────────────────────────────────────
        // 빅스비/시리 스타일 화면 오버레이 + 채팅 화면 wake call 표시 트리거.
        private val _uiState = MutableStateFlow(UiState.IDLE)
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        private val _partialTranscript = MutableStateFlow("")
        val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

        // KikiChatViewModel 이 구독해 "하이 키키 (음성 호출)" + "네, 부르셨어요?" 두 줄을
        // 로컬 채팅 말풍선으로 prepend 한다. replay=0 — 채팅 화면 진입 후 발생한 wake 만 표시.
        private val _wakeCallEvents = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 4)
        val wakeCallEvents: SharedFlow<Long> = _wakeCallEvents.asSharedFlow()

        @Volatile private var lastWakeAt: Long = 0L

        @Volatile private var model: Model? = null

        @Volatile private var recognizer: Recognizer? = null

        @Volatile private var speechService: SpeechService? = null

        fun start() {
            if (shouldKeepListening.getAndSet(true)) return // 이미 동작 중

            if (!hasRecordAudioPermission()) {
                Log.w(TAG, "RECORD_AUDIO 권한 없음 — wake word 비활성화")
                shouldKeepListening.set(false)
                return
            }

            kikiVoice.ensureInitialized()
            initVoskAndStartListening()
            Log.i(TAG, "Wake word 듣기 시작 (Vosk)")
        }

        fun stop() {
            if (!shouldKeepListening.getAndSet(false)) return
            mainHandler.post { teardownVosk() }
            Log.i(TAG, "Wake word 듣기 중지")
        }

        /**
         * Vosk 모델을 internal storage 로 unpack 한 뒤 [SpeechService] 한 개를 띄워서
         * 끝까지 유지. 모델 로드 비용은 첫 실행 시 한 번만.
         */
        private fun initVoskAndStartListening() {
            val loaded = model
            if (loaded != null) {
                createRecognizerAndStart(loaded)
                return
            }
            StorageService.unpack(
                context,
                MODEL_ASSET_NAME,
                MODEL_DEST_NAME,
                { m ->
                    if (!shouldKeepListening.get()) {
                        runCatching { m.close() }
                        return@unpack
                    }
                    model = m
                    createRecognizerAndStart(m)
                },
                { e ->
                    Log.e(TAG, "Vosk 모델 로드 실패 — wake word 비활성화", e)
                    shouldKeepListening.set(false)
                },
            )
        }

        private fun createRecognizerAndStart(loadedModel: Model) {
            try {
                val rec = Recognizer(loadedModel, SAMPLE_RATE)
                val svc = SpeechService(rec, SAMPLE_RATE)
                recognizer = rec
                speechService = svc
                mode = Mode.WAKE
                svc.startListening(buildListener())
                Log.i(TAG, "Vosk SpeechService 가동 — 마이크 점유 시작")
            } catch (e: Exception) {
                Log.e(TAG, "Vosk SpeechService 시작 실패", e)
                shouldKeepListening.set(false)
            }
        }

        private fun teardownVosk() {
            runCatching {
                speechService?.stop()
                speechService?.shutdown()
            }.onFailure { Log.w(TAG, "SpeechService teardown 예외", it) }
            speechService = null
            runCatching { recognizer?.close() }
            recognizer = null
            // model 은 캐시. 다음 start() 에서 재사용 (unpack 비용 회피).
            isTtsSpeaking.set(false)
            mode = Mode.WAKE
            _partialTranscript.value = ""
            _uiState.value = UiState.IDLE
        }

        /**
         * 오버레이 UI 상태.
         *
         *  - [IDLE]       오버레이 숨김 (평소 wake word 대기 중)
         *  - [RESPONDING] "네, 부르셨어요?" TTS 발화 중
         *  - [LISTENING]  사용자 명령 STT 수집 중 — 펄스 + partial transcript 표시
         */
        enum class UiState { IDLE, RESPONDING, LISTENING }

        private fun buildListener(): RecognitionListener =
            object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    if (isTtsSpeaking.get()) return
                    val text = parseVoskJson(hypothesis, KEY_PARTIAL)
                    if (text.isBlank()) return
                    // ⭐ 디버그: Vosk 가 들은 partial 텍스트 — wake 패턴 튜닝용
                    Log.d(TAG, "[partial] \"$text\"")

                    // COMMAND 모드면 실시간 transcript 를 UI 오버레이로 흘려 보냄
                    if (mode == Mode.COMMAND) {
                        _partialTranscript.value = text
                        return
                    }

                    if (matchesWakeWord(text)) {
                        val now = System.currentTimeMillis()
                        if (now - lastWakeAt < COOLDOWN_MS) return
                        lastWakeAt = now
                        Log.i(TAG, "Wake word 감지: \"$text\"")
                        triggerKikiResponse()
                    }
                }

                override fun onResult(hypothesis: String?) {
                    // TTS 중에 들어온 결과는 자기 음성이 잡힌 것 — 무시
                    if (isTtsSpeaking.get()) return
                    val text = parseVoskJson(hypothesis, KEY_TEXT)
                    if (text.isBlank()) return
                    // ⭐ 디버그: Vosk 가 들은 final 텍스트
                    Log.d(TAG, "[final] \"$text\" mode=$mode")

                    when (mode) {
                        Mode.COMMAND -> {
                            Log.i(TAG, "사용자 명령: \"$text\"")
                            sendCommandToChat(text)
                            mode = Mode.WAKE
                            _partialTranscript.value = ""
                            _uiState.value = UiState.IDLE
                            runCatching { recognizer?.reset() }
                        }
                        Mode.WAKE -> {
                            // partial 단계에서 못 잡았던 wake 가 final 에서 잡히는 경우 보완.
                            if (matchesWakeWord(text)) {
                                val now = System.currentTimeMillis()
                                if (now - lastWakeAt >= COOLDOWN_MS) {
                                    lastWakeAt = now
                                    Log.i(TAG, "Wake word 감지 (final): \"$text\"")
                                    triggerKikiResponse()
                                }
                            }
                        }
                    }
                }

                override fun onFinalResult(hypothesis: String?) { /* stop() 후의 cleanup 결과 — 무시 */ }

                override fun onError(e: Exception?) {
                    Log.w(TAG, "Vosk 오류", e)
                }

                override fun onTimeout() { /* SpeechService 는 timeout 설정 없으면 콜백 안 옴 */ }
            }

        /**
         * Wake 감지 직후 — "네, 부르셨어요?" 발화 후 명령 모드로 전환.
         * 마이크는 계속 켜진 상태이므로 모드 전환만 함.
         */
        private fun triggerKikiResponse() {
            isTtsSpeaking.set(true)
            // UI: "키키가 응답중..." 오버레이 표시 트리거
            _uiState.value = UiState.RESPONDING
            // KikiChatScreen 이 wake call 말풍선 두 줄 ("하이 키키" + "네, 부르셨어요?") prepend
            _wakeCallEvents.tryEmit(System.currentTimeMillis())

            // wake STT 잔여 partial 비우기
            runCatching { recognizer?.reset() }

            kikiVoice.respondToWake {
                // TTS 자체 잔향이 마이크로 다시 들어가는 200ms 정도를 추가로 무시
                mainHandler.postDelayed({
                    runCatching { recognizer?.reset() }
                    mode = Mode.COMMAND
                    isTtsSpeaking.set(false)
                    _partialTranscript.value = ""
                    _uiState.value = UiState.LISTENING
                    Log.i(TAG, "이제 명령 들어요 (COMMAND 모드)")
                }, TTS_TAIL_GUARD_MS)
            }
        }

        private fun matchesWakeWord(transcript: String): Boolean {
            val normalized =
                transcript
                    .lowercase(Locale.KOREAN)
                    .replace(Regex("[\\s\\p{Punct}]+"), "")
            return WAKE_PATTERNS.any { it in normalized }
        }

        private fun parseVoskJson(
            hypothesis: String?,
            key: String,
        ): String {
            if (hypothesis.isNullOrBlank()) return ""
            return try {
                JSONObject(hypothesis).optString(key, "")
            } catch (e: Exception) {
                ""
            }
        }

        private fun sendCommandToChat(transcript: String) {
            val trimmed = transcript.trim()
            if (trimmed.isEmpty()) return
            scope.launch {
                runCatching { healthRepository.sendFoodRecommendCommand(trimmed) }
                    .onFailure { Log.w(TAG, "채팅 명령 전송 실패", it) }
            }
        }

        private fun hasRecordAudioPermission(): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED

        private companion object {
            const val TAG = "WakeWordManager"
            const val SAMPLE_RATE = 16000.0f
            const val COOLDOWN_MS = 3_000L
            const val TTS_TAIL_GUARD_MS = 300L

            // assets/model-ko 폴더가 internal storage 의 model/ 로 unpack 됨.
            const val MODEL_ASSET_NAME = "model-ko"
            const val MODEL_DEST_NAME = "model"

            // Vosk JSON 응답 key
            const val KEY_PARTIAL = "partial"
            const val KEY_TEXT = "text"

            // 한국어 STT 가 "Hi Kiki" / "하이 키키" 를 transcribe 할 때의 변형들.
            // 모두 공백/구두점 제거 후 소문자로 비교.
            // Vosk small 모델은 "키키" 를 "기기/끼끼/키기/키이" 등으로 transcribe 하는 경우가 많음.
            val WAKE_PATTERNS =
                listOf(
                    // 정확 매칭
                    "하이키키",
                    "하이키",
                    "키키야",
                    "키키",
                    // Vosk 가 자주 잘못 transcribe 하는 변형
                    "하이기기",
                    "하이끼끼",
                    "하이키이",
                    "하이키기",
                    "하이기키",
                    "아이키키",
                    "아이키",
                    "하이키키야",
                    "헤이키키",
                    "헤이키",
                    // 영문/혼합
                    "hikiki",
                    "haikiki",
                    "hi키키",
                )
        }
    }
