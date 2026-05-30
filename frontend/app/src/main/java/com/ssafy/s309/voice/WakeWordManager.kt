package com.ssafy.s309.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.notification.KikiVoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
 * "Hi Kiki" wake word 감지기 — Vosk(오프라인 영어 STT) 기반.
 *
 * Lifecycle: [com.ssafy.s309.MainActivity] onStart/onStop 에서 [start]/[stop] 호출.
 *
 * 동작 흐름:
 *  1. Vosk [SpeechService] 가 마이크를 잡고 wake word ("Hi Kiki") 만 감지.
 *     시작/종료 시스템 효과음("띠롱") 없음.
 *  2. wake 매칭 → [KikiVoice] 로 "네, 부르셨어요?" 발화
 *  3. TTS 종료 → Vosk SpeechService 일시 해제(model 캐시 유지) →
 *     [VoiceQueryManager] (Android SpeechRecognizer = Google 클라우드 STT) 가 명령 1회 listen.
 *     SR 은 한/영 자동 처리라 "마라탕 먹을까?" 같은 한국어 자유 발화도 잘 잡음.
 *  4. SR 결과 → [HealthRepository.sendFoodRecommendCommand] 로 흘려 보냄
 *     → 백엔드가 AI 응답을 FCM 으로 push, FcmService 가 본문을 TTS 로 재생
 *  5. Vosk SpeechService 재가동 → 1번부터 루프 (wake 대기 복귀)
 *
 * 자기 TTS 가 wake 로 잘못 들리는 무한 루프는 [isTtsSpeaking] 플래그로 차단.
 * 명령 phase 에는 Vosk 가 꺼져 있어 자기 STT 충돌 없음.
 *
 * **왜 영어 모델인가:** small 한국어 모델은 시연장 한국어 잡담에 false trigger 위험이 있음
 * ("키키"/"지지"/"기기" 비슷한 음절을 grammar 가 wake 로 강제 매핑). 영어 wake word + 영어
 * 모델은 한국어 잡음을 [unk] 로 떨어뜨리고, "Hi Kiki" 의 자음 cluster 가 phonetic 으로
 * distinctive 해 small 모델로도 true positive 안정성이 높다. 명령 STT 는 Google SR 이
 * 한/영 모두 처리하므로 한국어 명령은 제약 없음.
 *
 * 트레이드오프: SR 시작/종료 시 시스템 사운드 한 번 발생, 명령 STT 온라인 필수.
 *
 * 모델: `app/src/main/assets/model-en/` (vosk-model-small-en-us-0.15, ~40MB). wake 전용.
 * 첫 실행 시 [StorageService.unpack] 가 internal storage 로 압축 해제 (1~2초).
 */
@Singleton
class WakeWordManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val kikiVoice: KikiVoice,
        private val healthRepository: HealthRepository,
        private val voiceQueryManager: VoiceQueryManager,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val mainHandler = Handler(Looper.getMainLooper())

        private val shouldKeepListening = AtomicBoolean(false)
        private val isTtsSpeaking = AtomicBoolean(false)

        // 외부(예: [com.ssafy.s309.notification.TtsManager] 의 AI 응답 발화) TTS 가 스피커로
        // 흘러나오는 동안 Vosk 가 자기 음향을 wake 로 잘못 잡지 않도록 차단하는 플래그.
        // [setExternalTtsActive] 로 토글. 종료 시 [EXTERNAL_TTS_TAIL_GUARD_MS] 잔향 보호.
        private val externalTtsActive = AtomicBoolean(false)
        private var clearExternalTtsRunnable: Runnable? = null

        // VoiceQueryManager.partial → _partialTranscript 미러링 job.
        // LISTENING 진입 시 launch, 결과/에러/teardown 시 cancel.
        @Volatile private var partialCollectJob: Job? = null

        // SHOWING_RESPONSE 자동 dismiss 타이머. 새 응답 받으면 cancel 후 재시작.
        private var responseDismissRunnable: Runnable? = null

        // THINKING 상태가 너무 길어지면 (FCM 안 옴) 자동으로 IDLE 로 복귀.
        private var thinkingTimeoutRunnable: Runnable? = null

        // ── UI 노출 상태 ─────────────────────────────────────────────
        // 빅스비/시리 스타일 화면 오버레이 + 채팅 화면 wake call 표시 트리거.
        private val _uiState = MutableStateFlow(UiState.IDLE)
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        private val _partialTranscript = MutableStateFlow("")
        val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

        // Siri/Bixby 스타일 모달에 띄울 AI 응답 텍스트.
        // SHOWING_RESPONSE 상태에서 [KikiVoiceOverlay] 가 카드로 렌더링.
        private val _responseText = MutableStateFlow("")
        val responseText: StateFlow<String> = _responseText.asStateFlow()

        // THINKING 상태 진행 hint — 일정 시간마다 메시지 cycling 해서 사용자 체감 latency 단축.
        // 실제 BE 진행 단계와 무관한 fake progress 이지만 무한정 "잠시만요..." 보다 훨씬 자연스러움.
        private val _thinkingHint = MutableStateFlow("")
        val thinkingHint: StateFlow<String> = _thinkingHint.asStateFlow()
        private val thinkingHintRunnables = mutableListOf<Runnable>()

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
         * 외부 컴포넌트 ([com.ssafy.s309.notification.TtsManager] 등) 가 스피커로 TTS 를
         * 재생할 때 호출. 재생 중에는 Vosk wake 매칭을 차단해 자기 음향이 트리거되는 false
         * positive 를 막는다.
         *
         * @param active true: TTS 시작 (즉시 차단), false: TTS 종료
         *                (잔향/버퍼 보호용 [EXTERNAL_TTS_TAIL_GUARD_MS] 후 차단 해제)
         */
        fun setExternalTtsActive(active: Boolean) {
            mainHandler.post {
                if (active) {
                    externalTtsActive.set(true)
                    clearExternalTtsRunnable?.let { mainHandler.removeCallbacks(it) }
                    clearExternalTtsRunnable = null
                } else {
                    // tail guard: 스피커 잔향 + 마이크 버퍼에 남은 자기 음향이 wake 로 잡히는
                    // race 회피. 큐로 여러 발화가 이어지는 경우 다음 onStart 가 미리 cancel 함.
                    clearExternalTtsRunnable?.let { mainHandler.removeCallbacks(it) }
                    val r =
                        Runnable {
                            externalTtsActive.set(false)
                            clearExternalTtsRunnable = null
                        }
                    clearExternalTtsRunnable = r
                    mainHandler.postDelayed(r, EXTERNAL_TTS_TAIL_GUARD_MS)
                }
            }
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
                // Grammar constraint: Vosk small 모델이 자유 발화 모드에서 잡음을 임의의 한국어
                // 단어로 매칭해 정확도가 매우 낮음. JSON 배열로 wake 변형만 허용하고 나머지는
                // [unk] 로 떨어뜨려 잡음 트리거 차단 + 발음 비슷한 입력은 wake 변형 중 하나로 강제 매핑.
                val rec =
                    try {
                        Recognizer(loadedModel, SAMPLE_RATE, WAKE_GRAMMAR_JSON)
                    } catch (e: Exception) {
                        // grammar 미지원 모델이면 free-form 으로 폴백 (정확도 떨어지지만 동작은 유지).
                        Log.w(TAG, "grammar 모드 실패 → free-form 폴백", e)
                        Recognizer(loadedModel, SAMPLE_RATE)
                    }
                val svc = SpeechService(rec, SAMPLE_RATE)
                recognizer = rec
                speechService = svc
                svc.startListening(buildListener())
                Log.i(TAG, "Vosk SpeechService 가동 — wake 대기 (grammar 적용)")
            } catch (e: Exception) {
                Log.e(TAG, "Vosk SpeechService 시작 실패", e)
                shouldKeepListening.set(false)
            }
        }

        private fun teardownVosk() {
            // 진행 중인 SR 명령 세션도 함께 정리.
            runCatching { voiceQueryManager.cancel() }
            partialCollectJob?.cancel()
            partialCollectJob = null
            cancelResponseTimers()
            clearExternalTtsRunnable?.let { mainHandler.removeCallbacks(it) }
            clearExternalTtsRunnable = null
            externalTtsActive.set(false)

            pauseVoskKeepingModel()
            isTtsSpeaking.set(false)
            _partialTranscript.value = ""
            _responseText.value = ""
            _uiState.value = UiState.IDLE
        }

        /**
         * Vosk SpeechService 만 해제하고 [model] 캐시는 유지.
         * SR 명령 phase 진입 시 마이크 점유를 풀어주기 위해 사용.
         */
        private fun pauseVoskKeepingModel() {
            runCatching {
                speechService?.stop()
                speechService?.shutdown()
            }.onFailure { Log.w(TAG, "SpeechService teardown 예외", it) }
            speechService = null
            runCatching { recognizer?.close() }
            recognizer = null
            // model 은 캐시. 다음 start() / resumeVosk() 에서 재사용 (unpack 비용 회피).
        }

        /** SR 명령 phase 종료 후 wake 대기로 복귀. */
        private fun resumeVosk() {
            if (!shouldKeepListening.get()) return
            val m =
                model ?: run {
                    Log.w(TAG, "resumeVosk: model 캐시 없음 — 재로딩")
                    initVoskAndStartListening()
                    return
                }
            createRecognizerAndStart(m)
        }

        /**
         * 오버레이 UI 상태 머신.
         *
         *  - [IDLE]              오버레이 숨김 (평소 wake word 대기 중)
         *  - [RESPONDING]        "네, 부르셨어요?" TTS 발화 중
         *  - [LISTENING]         SR 명령 STT 수집 중 — 펄스 + partial transcript 표시
         *  - [THINKING]          명령 BE 전송 후 AI 응답 대기 중 — "잠시만요..." 로딩
         *  - [SHOWING_RESPONSE]  AI 응답을 [responseText] 카드로 표시 — 자동 dismiss 또는 탭하면 닫힘
         *
         * 흐름: IDLE → RESPONDING → LISTENING → (THINKING → SHOWING_RESPONSE → IDLE) | IDLE
         */
        enum class UiState { IDLE, RESPONDING, LISTENING, THINKING, SHOWING_RESPONSE }

        // Vosk 리스너는 wake word 만 담당. 명령 STT 는 SR 핸드오프 (handoffToSpeechRecognizer).
        private fun buildListener(): RecognitionListener =
            object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    if (isTtsSpeaking.get() || externalTtsActive.get()) return
                    val text = parseVoskJson(hypothesis, KEY_PARTIAL)
                    if (text.isBlank()) return
                    Log.d(TAG, "[partial] \"$text\"")

                    if (matchesWakeWord(text)) {
                        val now = System.currentTimeMillis()
                        if (now - lastWakeAt < COOLDOWN_MS) return
                        lastWakeAt = now
                        Log.i(TAG, "Wake word 감지: \"$text\"")
                        triggerKikiResponse()
                    }
                }

                override fun onResult(hypothesis: String?) {
                    if (isTtsSpeaking.get() || externalTtsActive.get()) return
                    val text = parseVoskJson(hypothesis, KEY_TEXT)
                    if (text.isBlank()) return
                    Log.d(TAG, "[final] \"$text\"")

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

                override fun onFinalResult(hypothesis: String?) { /* stop() 후의 cleanup 결과 — 무시 */ }

                override fun onError(e: Exception?) {
                    Log.w(TAG, "Vosk 오류", e)
                }

                override fun onTimeout() { /* SpeechService 는 timeout 설정 없으면 콜백 안 옴 */ }
            }

        /**
         * Wake 감지 직후 — "네, 부르셨어요?" 발화 후 SR 핸드오프.
         * TTS 종료 콜백 → Vosk 마이크 해제 → SpeechRecognizer 가 명령 1회 listen → Vosk 재개.
         *
         * 응답 자체에 WAKE_RESPONSE_DELAY_MS 만큼 앞 딜레이 — 인간이 wake 듣고 반응하는 느낌
         * (즉답 보다 살짝 텀 두는 게 자연스러움).
         */
        private fun triggerKikiResponse() {
            // 이전 응답 모달/타이머 있으면 즉시 정리하고 새 호출 처리.
            cancelResponseTimers()

            isTtsSpeaking.set(true)
            _uiState.value = UiState.RESPONDING
            _responseText.value = ""
            _wakeCallEvents.tryEmit(System.currentTimeMillis())

            runCatching { recognizer?.reset() }

            mainHandler.postDelayed({
                kikiVoice.respondToWake {
                    mainHandler.postDelayed({
                        handoffToSpeechRecognizer()
                    }, TTS_TAIL_GUARD_MS)
                }
            }, WAKE_RESPONSE_DELAY_MS)
        }

        /**
         * Vosk 마이크 해제 → [VoiceQueryManager] 가 SpeechRecognizer 로 명령 1회 캡처.
         * Vosk SpeechService 가 AudioRecord 를 점유 중이라 먼저 release 해야 SR 가 마이크를 잡을 수 있다.
         * VoiceQueryManager 내부에 이미 300ms WAKE_RELEASE_DELAY_MS 가 있어 race 완충.
         */
        private fun handoffToSpeechRecognizer() {
            pauseVoskKeepingModel()

            _partialTranscript.value = ""
            _uiState.value = UiState.LISTENING
            isTtsSpeaking.set(false)
            Log.i(TAG, "SR 명령 핸드오프 시작")

            // SR partial → 오버레이 transcript 미러링
            partialCollectJob?.cancel()
            partialCollectJob =
                scope.launch {
                    voiceQueryManager.partial.collect { p ->
                        _partialTranscript.value = p
                    }
                }

            voiceQueryManager.startOnce(
                onResult = { text ->
                    Log.i(TAG, "SR 명령 결과: \"$text\"")
                    onSrSessionEnded(text)
                },
                onError = { reason ->
                    Log.w(TAG, "SR 명령 실패: $reason")
                    onSrSessionEnded(null)
                },
            )
        }

        /**
         * SR 세션 종료 후 정리 + Vosk 재시작. 성공 시 transcript 를 채팅 명령으로 송신.
         * SR onResult/onError 는 워커 스레드에서 올 수 있어 main 으로 hop.
         *
         * 성공 시 동작:
         *  1. final transcript 를 _partialTranscript 에 잠깐 더 표시 (사용자가 자기 발화 확인)
         *  2. ack beep 재생 ("들었음" 피드백)
         *  3. UiState = THINKING 으로 전환 → 응답 대기
         *  4. 명령을 BE 로 전송
         *  5. SHOW_TRANSCRIPT_MS 후 _partialTranscript 비움 (transcript 카드만 사라지고 THINKING 유지)
         *
         * 실패 / NO_SPEECH 시: 조용히 IDLE 로 복귀.
         */
        private fun onSrSessionEnded(text: String?) {
            mainHandler.post {
                partialCollectJob?.cancel()
                partialCollectJob = null

                val finalText = text?.takeIf { it.isNotBlank() }

                if (finalText != null) {
                    // 사용자가 자기 발화 완성형을 화면에서 확인할 수 있도록 final 을 잠깐 더 표시.
                    _partialTranscript.value = finalText
                    playAckBeep()
                    _uiState.value = UiState.THINKING
                    sendCommandToChat(finalText)

                    // SHOW_TRANSCRIPT_MS 후 transcript 비우기 (state 는 그대로 THINKING).
                    mainHandler.postDelayed({
                        _partialTranscript.value = ""
                    }, SHOW_FINAL_TRANSCRIPT_MS)

                    // THINKING 너무 길어지면 자동 IDLE (FCM 누락/지연 대비).
                    scheduleThinkingTimeout()
                    // 진행 hint cycling 시작 ("혈당 확인 중..." → "추천 생성 중..." → ...)
                    startThinkingHints()
                } else {
                    _partialTranscript.value = ""
                    _uiState.value = UiState.IDLE
                }

                if (!shouldKeepListening.get()) return@post
                // SR system service 정리 시간 확보 후 Vosk 재기동 (마이크 grab race 회피).
                mainHandler.postDelayed({
                    if (shouldKeepListening.get()) resumeVosk()
                }, VOSK_RESUME_DELAY_MS)
            }
        }

        /**
         * FCM 으로 AI 응답이 도착했을 때 [FcmService] 가 호출. THINKING 상태일 때만 모달에 띄움
         * (IDLE 상태면 wake-activated flow 가 아니므로 일반 채팅 흐름에 맡김).
         */
        fun showResponse(text: String) {
            mainHandler.post {
                if (text.isBlank()) return@post
                if (_uiState.value != UiState.THINKING && _uiState.value != UiState.SHOWING_RESPONSE) {
                    Log.d(TAG, "showResponse: 현재 상태=${_uiState.value} → 모달 표시 skip")
                    return@post
                }
                cancelResponseTimers()
                _responseText.value = text
                _uiState.value = UiState.SHOWING_RESPONSE
                Log.i(TAG, "응답 모달 표시: \"${text.take(60)}${if (text.length > 60) "..." else ""}\"")

                val r =
                    Runnable {
                        if (_uiState.value == UiState.SHOWING_RESPONSE) {
                            _uiState.value = UiState.IDLE
                            _responseText.value = ""
                        }
                    }
                responseDismissRunnable = r
                mainHandler.postDelayed(r, RESPONSE_AUTO_DISMISS_MS)
            }
        }

        /**
         * 채팅 화면 마이크 버튼 — STT 시작 전 호출.
         * Vosk 마이크 해제 + 오버레이 LISTENING 전환 + partial 미러링 시작.
         */
        fun enterListeningStateForExternalStt() {
            mainHandler.post {
                cancelResponseTimers()
                pauseVoskKeepingModel()
                _partialTranscript.value = ""
                _uiState.value = UiState.LISTENING
                partialCollectJob?.cancel()
                partialCollectJob =
                    scope.launch {
                        voiceQueryManager.partial.collect { p -> _partialTranscript.value = p }
                    }
            }
        }

        /**
         * STT 결과 확보 시 호출 — 오버레이 THINKING 전환 + 힌트 cycling + Vosk 재개.
         */
        fun enterThinkingStateForExternalStt(finalTranscript: String) {
            mainHandler.post {
                partialCollectJob?.cancel()
                partialCollectJob = null
                _partialTranscript.value = finalTranscript
                _uiState.value = UiState.THINKING
                scheduleThinkingTimeout()
                startThinkingHints()
                mainHandler.postDelayed({ _partialTranscript.value = "" }, SHOW_FINAL_TRANSCRIPT_MS)
                mainHandler.postDelayed({ if (shouldKeepListening.get()) resumeVosk() }, VOSK_RESUME_DELAY_MS)
            }
        }

        /**
         * STT 실패/취소 시 호출 — 오버레이 IDLE 복귀 + Vosk 재개.
         */
        fun enterIdleStateForExternalStt() {
            mainHandler.post {
                partialCollectJob?.cancel()
                partialCollectJob = null
                cancelResponseTimers()
                _partialTranscript.value = ""
                _uiState.value = UiState.IDLE
                mainHandler.postDelayed({ if (shouldKeepListening.get()) resumeVosk() }, VOSK_RESUME_DELAY_MS)
            }
        }

        /**
         * 뒤로가기 등 외부에서 진행 중인 wake flow 전체를 즉시 중단.
         * TTS·SR 모두 멈추고 IDLE 로 복귀. Vosk wake 대기는 그대로 유지.
         */
        fun abort() {
            mainHandler.post {
                cancelResponseTimers()
                kikiVoice.stop()
                runCatching { voiceQueryManager.cancel() }
                partialCollectJob?.cancel()
                partialCollectJob = null
                isTtsSpeaking.set(false)
                _partialTranscript.value = ""
                _responseText.value = ""
                _uiState.value = UiState.IDLE
            }
        }

        /** 사용자가 모달 탭해서 닫는 경우. */
        fun dismissResponse() {
            mainHandler.post {
                cancelResponseTimers()
                if (_uiState.value == UiState.SHOWING_RESPONSE || _uiState.value == UiState.THINKING) {
                    _uiState.value = UiState.IDLE
                    _responseText.value = ""
                }
            }
        }

        private fun scheduleThinkingTimeout() {
            thinkingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            val r =
                Runnable {
                    if (_uiState.value == UiState.THINKING) {
                        Log.w(TAG, "THINKING 타임아웃 (${THINKING_TIMEOUT_MS}ms) — IDLE 복귀")
                        _uiState.value = UiState.IDLE
                        _responseText.value = ""
                    }
                }
            thinkingTimeoutRunnable = r
            mainHandler.postDelayed(r, THINKING_TIMEOUT_MS)
        }

        private fun cancelResponseTimers() {
            responseDismissRunnable?.let { mainHandler.removeCallbacks(it) }
            responseDismissRunnable = null
            thinkingTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            thinkingTimeoutRunnable = null
            cancelThinkingHints()
        }

        /**
         * THINKING 단계 진행 hint cycling. fake progress 지만 사용자 체감 latency 가 크게
         * 줄어듦. 실제 BE/AI 진행 단계와 무관 — 시간 기반 timer 로 메시지만 바꿈.
         */
        private fun startThinkingHints() {
            cancelThinkingHints()
            _thinkingHint.value = THINKING_HINTS.first().first
            THINKING_HINTS.forEach { (text, delay) ->
                val r =
                    Runnable {
                        if (_uiState.value == UiState.THINKING) {
                            _thinkingHint.value = text
                        }
                    }
                thinkingHintRunnables.add(r)
                mainHandler.postDelayed(r, delay)
            }
        }

        private fun cancelThinkingHints() {
            thinkingHintRunnables.forEach { mainHandler.removeCallbacks(it) }
            thinkingHintRunnables.clear()
            _thinkingHint.value = ""
        }

        /**
         * "들었음" ack beep. Siri 의 listening-stop chime 과 유사한 톤.
         * STREAM_MUSIC 사용 → 미디어 볼륨으로 들림.
         */
        private fun playAckBeep() {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_MUSIC, ACK_BEEP_VOLUME)
                tone.startTone(ToneGenerator.TONE_PROP_ACK, ACK_BEEP_DURATION_MS)
                // ToneGenerator 는 native resource — 재생 끝난 뒤 release.
                mainHandler.postDelayed({ runCatching { tone.release() } }, ACK_BEEP_DURATION_MS + 200L)
            }.onFailure { Log.w(TAG, "ack beep 재생 실패", it) }
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

            // SR onResults/onError → Vosk 재기동까지 짧은 지연. Android SpeechRecognizer
            // 시스템 서비스 unbind 가 끝나기 전에 AudioRecord 를 다시 잡으면 일부 단말에서
            // 마이크가 안 잡힘. VoiceQueryManager.WAKE_RELEASE_DELAY_MS(300) 와 대칭으로 둠.
            const val VOSK_RESUME_DELAY_MS = 300L

            // 외부 TTS 종료 후 wake 게이트 해제까지의 잔향 보호 시간. AI 응답이 길면 스피커
            // 잔향 + 마이크 버퍼 residual 이 이 정도 남음. KikiVoice 자기 TTS 의 300ms 보다
            // 약간 길게 — 외부 TTS 는 보통 더 큰 볼륨/긴 길이.
            const val EXTERNAL_TTS_TAIL_GUARD_MS = 400L

            // wake 감지 → TTS "네, 부르셨어요?" 사이 앞 딜레이. 즉답하면 자동응답기 느낌이라
            // 인간이 반응하는 텀을 살짝 둠.
            const val WAKE_RESPONSE_DELAY_MS = 500L

            // SR final transcript 를 partial 영역에 더 보여주는 시간 (사용자 자기 발화 확인용).
            const val SHOW_FINAL_TRANSCRIPT_MS = 1_500L

            // 명령 BE 전송 후 FCM 응답이 안 오면 자동 IDLE 로 복귀.
            const val THINKING_TIMEOUT_MS = 30_000L

            // 응답 모달 자동 dismiss. 사용자가 읽고 TTS 끝날 정도 시간.
            const val RESPONSE_AUTO_DISMISS_MS = 10_000L

            // ack beep 파라미터. STREAM_MUSIC, 0~100 범위 볼륨.
            const val ACK_BEEP_VOLUME = 60
            const val ACK_BEEP_DURATION_MS = 180

            // THINKING 단계 진행 hint. (label, delayMs) — delayMs 후 해당 label 로 교체.
            // 첫 항목은 즉시 표시되므로 delay 0. 평균 응답이 5~15초인 점을 감안해 펼침.
            val THINKING_HINTS =
                listOf(
                    "잠시만요..." to 0L,
                    "혈당 확인 중..." to 2_500L,
                    "활동량 확인 중..." to 5_500L,
                    "추천 생성 중..." to 9_000L,
                    "거의 다 왔어요..." to 14_000L,
                )

            // assets/model-en 폴더가 internal storage 의 model/ 로 unpack 됨.
            const val MODEL_ASSET_NAME = "model-en"
            const val MODEL_DEST_NAME = "model"

            // Vosk JSON 응답 key
            const val KEY_PARTIAL = "partial"
            const val KEY_TEXT = "text"

            // 영어 small 모델이 "Hi Kiki" 를 transcribe 할 때의 변형들.
            // 모두 공백/구두점 제거 후 소문자로 비교.
            // grammar 모드에서는 Vosk 가 WAKE_GRAMMAR_JSON 안의 phrase 만 출력 → 단순 substring 매칭으로 충분.
            // free-form 폴백 시에도 동작하도록 변형 유지.
            val WAKE_PATTERNS =
                listOf(
                    // wake-prefix 가 있는 형태만 (free-form 폴백 시 "kiki"/"key" 단독은 false positive 위험).
                    "hikiki",
                    "hikeykey",
                    "hikey",
                    "heykiki",
                    "heykey",
                    "haikiki",
                    "haykiki",
                    "highkiki",
                    "highkey",
                    "hekiki",
                )

            // Vosk grammar JSON. 이 phrase 들 + "[unk]" 만 출력 가능 → 잡음 트리거 차단.
            // "Hi Kiki" 영어 발음을 Vosk small 영어 모델이 매핑할 가능성이 있는 음성형들.
            // 매칭은 matchesWakeWord() 가 공백/구두점 제거 후 substring 으로 처리.
            const val WAKE_GRAMMAR_JSON =
                """["hi kiki", "hi key key", "hi key", "hey kiki", "hey key", "high kiki", "high key", "[unk]"]"""
        }
    }
