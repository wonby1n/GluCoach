
package com.ssafy.s309.ui.screen.main

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.R
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.ui.component.MarkdownText
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing
import com.ssafy.s309.voice.VoiceQueryManager
import com.ssafy.s309.voice.WakeWordManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// ── 모델 ─────────────────────────────────────────────────────────────

sealed class ChatMessage {
    data class KikiMessage(val item: NotificationItem) : ChatMessage()

    data class UserMessage(
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
        val createdAt: String = "",
    ) : ChatMessage()

    data class DateSeparator(val label: String) : ChatMessage()
}

// ── ViewModel ────────────────────────────────────────────────────────

@HiltViewModel
class KikiChatViewModel
    @Inject
    constructor(
        private val healthRepository: HealthRepository,
        private val voiceQueryManager: VoiceQueryManager,
        private val wakeWordManager: WakeWordManager,
    ) : ViewModel() {
        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        private val _isLoadingMore = MutableStateFlow(false)
        val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

        private val _hasMore = MutableStateFlow(true)
        val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

        private val _fontSize = MutableStateFlow(FONT_SIZE_DEFAULT)
        val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

        private val _isWaitingForAgent = MutableStateFlow(false)
        val isWaitingForAgent: StateFlow<Boolean> = _isWaitingForAgent.asStateFlow()

        // 음성 입력 상태 그대로 노출 (Composable이 마이크 버튼 외형 결정에 사용)
        val voiceState: StateFlow<VoiceQueryManager.State> = voiceQueryManager.state
        val voicePartial: StateFlow<String> = voiceQueryManager.partial

        private var timeoutJob: Job? = null
        private var currentPage = -1

        init {
            loadNextPage()
            // 실시간 혈당 알림 스트림
            viewModelScope.launch {
                healthRepository.glucoseAlertStream.collect { alert ->
                    // AGENT_ 알림은 chatFcmEvent가 백엔드 재조회로 처리 → 여기서 skip해야 중복 방지
                    if (alert.alertType.startsWith("AGENT_")) return@collect
                    val raw = _messages.value.filterNot { it is ChatMessage.DateSeparator }
                    _messages.value = withDateSeparators(listOf(ChatMessage.KikiMessage(alert)) + raw)
                }
            }
            // FCM 채팅 이벤트 — 인디케이터 OFF + page=0 재조회
            viewModelScope.launch {
                healthRepository.chatFcmEvent.collect {
                    onFcmReceived()
                }
            }
            // "하이 키키" 음성 호출 이벤트 — 채팅 화면이 살아있을 때만 로컬 말풍선 prepend.
            // 백엔드 거치지 않으므로 refresh()/페이지 재조회 시엔 사라짐 (의도된 동작).
            viewModelScope.launch {
                wakeWordManager.wakeCallEvents.collect { timestamp ->
                    prependWakeCallMessages(timestamp)
                }
            }
        }

        /**
         * Wake 감지 시 채팅에 두 줄을 즉시 추가한다:
         *  1. "하이 키키" — 사용자 말풍선
         *  2. "네, 부르셨어요?" — 키키 말풍선
         *
         * 백엔드에 저장되지 않으므로, 채팅 새로고침/페이지 재조회 시엔 사라진다.
         * 데모 시 wake 호출이 화면에 바로 보이게 하기 위한 로컬 UI 트릭.
         */
        private fun prependWakeCallMessages(timestamp: Long) {
            val nowIso = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val userMsg =
                ChatMessage.UserMessage(
                    text = "하이 키키",
                    timestamp = timestamp,
                    createdAt = nowIso,
                )
            val kikiMsg =
                ChatMessage.KikiMessage(
                    NotificationItem(
                        // 백엔드 ID 와 충돌하지 않도록 음수 + ms 타임스탬프 사용
                        id = -timestamp,
                        title = "키키",
                        message = "네, 부르셨어요?",
                        timeAgoText = "방금",
                        isUnread = false,
                        alertType = "LOCAL_WAKE_CALL",
                        createdAt = nowIso,
                        displayTrace = null,
                    ),
                )
            val raw = _messages.value.filterNot { it is ChatMessage.DateSeparator }
            _messages.value = withDateSeparators(listOf(kikiMsg, userMsg) + raw)
        }

        fun loadNextPage() {
            if (_isLoadingMore.value || !_hasMore.value) return
            val nextPage = currentPage + 1
            viewModelScope.launch {
                _isLoadingMore.value = true
                try {
                    val response = healthRepository.getChatMessagesPage(nextPage)
                    val newMessages: List<ChatMessage> =
                        response.content
                            .sortedByDescending { it.id }
                            .map { m ->
                                if (m.sender == "user") {
                                    ChatMessage.UserMessage(
                                        text = m.message ?: "",
                                        timestamp = parseIsoTimestamp(m.createdAt),
                                        createdAt = m.createdAt,
                                    )
                                } else {
                                    ChatMessage.KikiMessage(
                                        NotificationItem(
                                            id = m.id,
                                            title = "키키",
                                            message = m.message ?: "",
                                            timeAgoText = healthRepository.formatTimeAgo(m.createdAt),
                                            isUnread = !m.isRead,
                                            alertType = m.messageType ?: "",
                                            createdAt = m.createdAt,
                                            displayTrace = m.displayTrace,
                                            payload = m.payload,
                                        ),
                                    )
                                }
                            }
                    _hasMore.value = (nextPage.toLong() + 1) * PAGE_SIZE < response.total
                    val existing = _messages.value.filterNot { it is ChatMessage.DateSeparator }
                    _messages.value = withDateSeparators(existing + newMessages)
                    currentPage = nextPage
                } catch (e: Exception) {
                    Log.w(TAG, "page=$nextPage 로드 실패", e)
                } finally {
                    _isLoadingMore.value = false
                }
            }
        }

        /**
         * 화면 진입/포그라운드 복귀 시 호출 — page=0 응답으로 메시지 전체 교체.
         * 다른 단말이 보낸 user 메시지까지 가져오고, timeAgo도 재계산됨.
         */
        fun refresh() {
            viewModelScope.launch {
                runCatching { healthRepository.getChatMessagesPage(page = 0) }
                    .onSuccess { response ->
                        val fresh: List<ChatMessage> =
                            response.content
                                .sortedByDescending { it.id }
                                .map { m ->
                                    if (m.sender == "user") {
                                        ChatMessage.UserMessage(
                                            text = m.message ?: "",
                                            timestamp = parseIsoTimestamp(m.createdAt),
                                            createdAt = m.createdAt,
                                        )
                                    } else {
                                        ChatMessage.KikiMessage(
                                            NotificationItem(
                                                id = m.id,
                                                title = "키키",
                                                message = m.message ?: "",
                                                timeAgoText = healthRepository.formatTimeAgo(m.createdAt),
                                                isUnread = !m.isRead,
                                                alertType = m.messageType ?: "",
                                                createdAt = m.createdAt,
                                                displayTrace = m.displayTrace,
                                                payload = m.payload,
                                            ),
                                        )
                                    }
                                }
                        _messages.value = withDateSeparators(fresh)
                        currentPage = 0
                        _hasMore.value = PAGE_SIZE < response.total
                    }
                    .onFailure { Log.w(TAG, "refresh 실패", it) }
            }
        }

        /** FCM data.chatMessageId 수신 시 호출 — 인디케이터 OFF + page=0 재조회 */
        fun onFcmReceived() {
            timeoutJob?.cancel()
            _isWaitingForAgent.value = false
            viewModelScope.launch {
                runCatching { healthRepository.getChatMessagesPage(page = 0) }
                    .onSuccess { response ->
                        val existingCreatedAts =
                            _messages.value
                                .filterNot { it is ChatMessage.DateSeparator }
                                .map { msg ->
                                    when (msg) {
                                        is ChatMessage.KikiMessage -> msg.item.createdAt
                                        is ChatMessage.UserMessage -> msg.createdAt
                                        else -> ""
                                    }
                                }.toSet()
                        val newMessages =
                            response.content
                                .sortedByDescending { it.id }
                                // 이미 로컬에 있는 createdAt은 dedupe. 다른 단말이 보낸 user 메시지는 받기 위해 sender 필터 제거.
                                .filter { it.createdAt !in existingCreatedAts }
                                .map { m ->
                                    if (m.sender == "user") {
                                        ChatMessage.UserMessage(
                                            text = m.message ?: "",
                                            timestamp = parseIsoTimestamp(m.createdAt),
                                            createdAt = m.createdAt,
                                        )
                                    } else {
                                        ChatMessage.KikiMessage(
                                            NotificationItem(
                                                id = m.id,
                                                title = "키키",
                                                message = m.message ?: "",
                                                timeAgoText = healthRepository.formatTimeAgo(m.createdAt),
                                                isUnread = !m.isRead,
                                                alertType = m.messageType ?: "",
                                                createdAt = m.createdAt,
                                                displayTrace = m.displayTrace,
                                                payload = m.payload,
                                            ),
                                        )
                                    }
                                }
                        if (newMessages.isNotEmpty()) {
                            // 옵티미스틱 user 메시지의 createdAt 은 FE clock 으로 생성돼 서버 DB clock 과
                            // 미세하게 어긋난다. 위 createdAt 기반 dedup 으로는 잡히지 않아 같은 user
                            // 메시지가 [local optimistic + server] 둘 다 화면에 남는 버그가 있었다.
                            // 서버 응답에 동일 텍스트 user 메시지가 있고 local 의 createdAt 이 서버에 없으면
                            // 그 local 은 옵티미스틱이라고 판정하고 제거한다.
                            val serverCreatedAts = response.content.map { it.createdAt }.toSet()
                            val fetchedUserTexts =
                                response.content
                                    .asSequence()
                                    .filter { it.sender == "user" }
                                    .mapNotNull { it.message }
                                    .toSet()
                            val raw =
                                _messages.value
                                    .filterNot { it is ChatMessage.DateSeparator }
                                    .filterNot { msg ->
                                        msg is ChatMessage.UserMessage &&
                                            msg.text in fetchedUserTexts &&
                                            msg.createdAt !in serverCreatedAts
                                    }
                            _messages.value = withDateSeparators(newMessages + raw)
                            // 음성 응답 자동 발화는 제거 — FcmService 가 푸시 도착 시 TTS 로 본문을 읽어준다 (중복 방지).
                        }
                    }
                    .onFailure { Log.w(TAG, "FCM 후 메시지 재조회 실패", it) }
            }
        }

        fun sendFoodRecommendCommand() {
            dispatchRecommendCommand(userQuery = null)
        }

        /**
         * 마이크 STT 결과를 그대로 recommend_food 경로로 흘려보낸다.
         * AI 프롬프트가 payload["query"] 존재 시 [자유 발화 모드] 로 분기한다.
         *
         * user 말풍선은 BE 저장 후 onFcmReceived 의 page=0 재조회에서 함께 가져온다 (로컬 prepend
         * 하면 클라이언트/BE createdAt 차이로 dedupe가 깨져 중복 표시됨).
         * 음성 응답 TTS 는 FcmService 가 푸시 도착 시 본문을 읽어주므로 여기서는 별도 처리하지 않는다.
         */
        fun sendVoiceQuery(transcript: String) {
            val trimmed = transcript.trim()
            if (trimmed.isEmpty()) return
            dispatchRecommendCommand(userQuery = trimmed)
        }

        /**
         * 마이크 버튼 탭. STT 한 세션 시작. 권한/엔진 미비 또는 발화 미감지 시 [onError] 호출.
         * 결과 확보 시 자동으로 [sendVoiceQuery] 로 흘려보낸다.
         */
        fun startVoiceQuery(onError: (VoiceQueryManager.FailureReason) -> Unit) {
            wakeWordManager.enterListeningStateForExternalStt()
            voiceQueryManager.startOnce(
                onResult = { transcript ->
                    wakeWordManager.enterThinkingStateForExternalStt(transcript)
                    sendVoiceQuery(transcript)
                },
                onError = { reason ->
                    wakeWordManager.enterIdleStateForExternalStt()
                    onError(reason)
                },
            )
        }

        fun cancelVoiceQuery() {
            voiceQueryManager.cancel()
            wakeWordManager.enterIdleStateForExternalStt()
        }

        private fun dispatchRecommendCommand(userQuery: String?) {
            if (_isWaitingForAgent.value) return
            viewModelScope.launch {
                runCatching { healthRepository.sendFoodRecommendCommand(userQuery) }
                    .onSuccess {
                        _isWaitingForAgent.value = true
                        timeoutJob?.cancel()
                        timeoutJob =
                            viewModelScope.launch {
                                delay(AGENT_TIMEOUT_MS)
                                _isWaitingForAgent.value = false
                            }
                    }
                    .onFailure { Log.w(TAG, "음식 추천 명령 발화 실패", it) }
            }
        }

        fun increaseFontSize() = _fontSize.update { (it + 1f).coerceAtMost(FONT_SIZE_MAX) }

        fun decreaseFontSize() = _fontSize.update { (it - 1f).coerceAtLeast(FONT_SIZE_MIN) }

        /**
         * replyText: AI 서버에 보낼 텍스트 (null 이면 서버 호출 생략 — "괜찮아요" 케이스)
         * displayLabel: 말풍선에 표시할 텍스트
         */
        fun sendUserReply(
            replyText: String?,
            displayLabel: String,
        ) {
            val now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val userMsg =
                ChatMessage.UserMessage(
                    text = displayLabel,
                    timestamp = System.currentTimeMillis(),
                    createdAt = now,
                )
            val raw = _messages.value.filterNot { it is ChatMessage.DateSeparator }
            _messages.value = withDateSeparators(listOf(userMsg) + raw)

            if (replyText != null) {
                viewModelScope.launch {
                    healthRepository.sendPostMealReply(replyText, displayLabel)
                }
            }
        }

        companion object {
            private const val TAG = "KikiChatVM"
            const val PAGE_SIZE = 20L
            private const val FONT_SIZE_DEFAULT = 14f
            private const val FONT_SIZE_MIN = 11f
            private const val FONT_SIZE_MAX = 20f
            private const val AGENT_TIMEOUT_MS = 30_000L
        }
    }

// ── 날짜 헬퍼 ────────────────────────────────────────────────────────

private fun parseIsoTimestamp(iso: String): Long =
    try {
        val normalized = iso.replace(' ', 'T')
        try {
            OffsetDateTime.parse(normalized).toInstant().toEpochMilli()
        } catch (e: Exception) {
            LocalDateTime.parse(normalized).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    } catch (e: Exception) {
        System.currentTimeMillis()
    }

private fun getMessageDate(msg: ChatMessage): LocalDate? =
    when (msg) {
        is ChatMessage.KikiMessage -> {
            val iso = msg.item.createdAt
            if (iso.isBlank()) {
                null
            } else {
                try {
                    try {
                        OffsetDateTime.parse(iso).toLocalDate()
                    } catch (e: Exception) {
                        LocalDateTime.parse(iso).toLocalDate()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
        is ChatMessage.UserMessage -> {
            if (msg.createdAt.isNotBlank()) {
                try {
                    try {
                        OffsetDateTime.parse(msg.createdAt).toLocalDate()
                    } catch (
                        e: Exception,
                    ) {
                        LocalDateTime.parse(msg.createdAt).toLocalDate()
                    }
                } catch (e: Exception) {
                    LocalDate.ofEpochDay(msg.timestamp / 86_400_000L)
                }
            } else {
                LocalDate.ofEpochDay(msg.timestamp / 86_400_000L)
            }
        }
        is ChatMessage.DateSeparator -> null
    }

private fun formatDateLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "오늘"
        today.minusDays(1) -> "어제"
        else -> "${date.monthValue}월 ${date.dayOfMonth}일"
    }
}

/**
 * 순수 메시지 목록(DateSeparator 미포함, 최신순)을 받아
 * 날짜가 바뀌는 경계마다 DateSeparator 를 삽입해 반환.
 */
private fun withDateSeparators(messages: List<ChatMessage>): List<ChatMessage> {
    if (messages.isEmpty()) return messages
    val result = mutableListOf<ChatMessage>()
    for (i in messages.indices) {
        result.add(messages[i])
        val cur = getMessageDate(messages[i])
        val next = if (i + 1 < messages.size) getMessageDate(messages[i + 1]) else null
        if (cur != null && next != null && cur != next) {
            result.add(ChatMessage.DateSeparator(formatDateLabel(next)))
        }
    }
    return result
}

private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(timestamp))

// ── Screen ───────────────────────────────────────────────────────────

@Composable
fun KikiChatScreen(
    onBack: () -> Unit,
    onItemClick: (NotificationItem) -> Unit = {},
    onReplySent: () -> Unit = {},
    onCompareClick: (String, String) -> Unit = { _, _ -> },
    viewModel: KikiChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val isWaitingForAgent by viewModel.isWaitingForAgent.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val voicePartial by viewModel.voicePartial.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // STT 트리거 — 권한 있으면 즉시 시작, 없으면 launcher 로 요청.
    val recordAudioLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                viewModel.startVoiceQuery { reason ->
                    Toast.makeText(context, voiceErrorMessage(reason), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "마이크 권한이 필요해요", Toast.LENGTH_SHORT).show()
            }
        }

    val onMicClick: () -> Unit = {
        when (voiceState) {
            VoiceQueryManager.State.LISTENING, VoiceQueryManager.State.PROCESSING ->
                viewModel.cancelVoiceQuery()
            VoiceQueryManager.State.IDLE -> {
                val hasPermission =
                    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    viewModel.startVoiceQuery { reason ->
                        Toast.makeText(context, voiceErrorMessage(reason), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }

    // 화면 진입/포그라운드 복귀 시 page=0 재조회 — FCM 미수신 단말에서도 최신 메시지 보장
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        ChatScreenStateHolder.isActive = true
                        viewModel.refresh()
                    }
                    Lifecycle.Event.ON_PAUSE -> ChatScreenStateHolder.isActive = false
                    else -> {}
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            ChatScreenStateHolder.isActive = false
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 새 메시지 도착 시 맨 아래로 스크롤 (reverseLayout=true 기준 index 0)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // 스크롤 끝(시각적 상단 = 오래된 메시지) 감지 → 다음 페이지 로드
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) return@derivedStateOf false
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    // 응답 버튼을 표시할 AGENT_MEAL_FOLLOWUP 메시지 ID 계산
    val (targetMealId, showReplyButtons) =
        remember(messages) {
            val pure = messages.filterNot { it is ChatMessage.DateSeparator }
            val idx =
                pure.indexOfFirst {
                    it is ChatMessage.KikiMessage && it.item.alertType.startsWith("AGENT_MEAL_FOLLOWUP")
                }
            if (idx < 0) return@remember Pair(-1L, false)
            // 더 최신 메시지(인덱스 < idx) 중 UserMessage 또는 에이전트 후속 메시지가 있으면 이미 처리됨
            val alreadyReplied =
                pure.take(idx).any { msg ->
                    msg is ChatMessage.UserMessage ||
                        (
                            msg is ChatMessage.KikiMessage &&
                                (
                                    msg.item.alertType.startsWith("AGENT_MEAL_REPLY") ||
                                        msg.item.alertType.startsWith("AGENT_MEAL_RETRY")
                                )
                        )
                }
            val id = (pure[idx] as ChatMessage.KikiMessage).item.id
            Pair(id, !alreadyReplied)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
    ) {
        KikiChatTopBar(
            onBack = onBack,
            fontSize = fontSize,
            onIncrease = viewModel::increaseFontSize,
            onDecrease = viewModel::decreaseFontSize,
        )
        HorizontalDivider(color = GlucoachColors.Border)

        Box(modifier = Modifier.weight(1f)) {
            if (messages.isEmpty() && !isLoadingMore) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        KikiAvatar(size = 72)
                        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                        Text(
                            text = "키키가 보낸 알림이 없어요",
                            color = GlucoachColors.TextSecondary,
                            fontSize = 15.sp,
                        )
                        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                        Text(
                            text = "혈당 이상 감지 시 키키가 알려드려요",
                            color = GlucoachColors.TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = GlucoachSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(GlucoachSpacing.lg),
                    reverseLayout = true,
                ) {
                    // 플로팅 버튼에 가리지 않도록 하단 여백
                    item { Spacer(modifier = Modifier.height(52.dp)) }

                    items(
                        items = messages,
                        key = { msg ->
                            when (msg) {
                                is ChatMessage.KikiMessage -> "kiki_${msg.item.id}"
                                is ChatMessage.UserMessage -> "user_${msg.timestamp}"
                                is ChatMessage.DateSeparator -> "sep_${msg.label}"
                            }
                        },
                    ) { message ->
                        when (message) {
                            is ChatMessage.KikiMessage ->
                                KikiChatBubble(
                                    item = message.item,
                                    fontSize = fontSize,
                                    showReplyButtons = showReplyButtons && message.item.id == targetMealId,
                                    onReply = { replyText, displayLabel ->
                                        viewModel.sendUserReply(replyText, displayLabel)
                                        if (replyText != null) onReplySent()
                                    },
                                    onClick = { onItemClick(message.item) },
                                    onCompareClick = onCompareClick,
                                )
                            is ChatMessage.UserMessage ->
                                UserChatBubble(message = message, fontSize = fontSize)
                            is ChatMessage.DateSeparator ->
                                DateSeparatorItem(label = message.label)
                        }
                    }

                    // 로딩 인디케이터 / 추가 패딩
                    item {
                        if (isLoadingMore) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = GlucoachSpacing.md),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = GlucoachColors.Primary,
                                    strokeWidth = 2.dp,
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                        }
                    }
                }
            }

            // ── 음식 추천 / 마이크 / STT 상태 (floating) ──────────
            FloatingChatControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                isWaitingForAgent = isWaitingForAgent,
                voiceState = voiceState,
                voicePartial = voicePartial,
                onMicClick = onMicClick,
                onRecommendClick = { viewModel.sendFoodRecommendCommand() },
            )
        }
    }
}

@Composable
private fun FloatingChatControls(
    modifier: Modifier,
    isWaitingForAgent: Boolean,
    voiceState: VoiceQueryManager.State,
    voicePartial: String,
    onMicClick: () -> Unit,
    onRecommendClick: () -> Unit,
) {
    val isListening = voiceState == VoiceQueryManager.State.LISTENING
    val isProcessingStt = voiceState == VoiceQueryManager.State.PROCESSING

    Column(
        modifier = modifier.padding(bottom = GlucoachSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 음성 발화 partial transcript 미니 표시
        if (isListening && voicePartial.isNotBlank()) {
            Box(
                modifier =
                    Modifier
                        .padding(bottom = GlucoachSpacing.sm)
                        .clip(RoundedCornerShape(14.dp))
                        .background(GlucoachColors.Surface)
                        .border(1.dp, GlucoachColors.Border, RoundedCornerShape(14.dp))
                        .padding(horizontal = GlucoachSpacing.md, vertical = 6.dp),
            ) {
                Text(
                    text = voicePartial,
                    color = GlucoachColors.TextPrimary,
                    fontSize = 13.sp,
                )
            }
        }

        if (isWaitingForAgent) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = GlucoachColors.PrimaryDark,
                    strokeWidth = 2.dp,
                )
                Spacer(modifier = Modifier.width(GlucoachSpacing.sm))
                Text(
                    text = "키키가 분석 중...",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm),
            ) {
                MicButton(
                    isListening = isListening,
                    isProcessing = isProcessingStt,
                    onClick = onMicClick,
                )
                RecommendChip(onClick = onRecommendClick)
            }
        }
    }
}

@Composable
private fun MicButton(
    isListening: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit,
) {
    val (bgColor, iconTint, icon, label) =
        when {
            isListening ->
                MicVisual(
                    bg = GlucoachColors.PrimaryDark,
                    tint = Color.White,
                    icon = Icons.Outlined.Stop,
                    label = "듣고 있어요",
                )
            isProcessing ->
                MicVisual(
                    bg = GlucoachColors.PrimaryLight,
                    tint = GlucoachColors.PrimaryDark,
                    icon = Icons.Outlined.Mic,
                    label = "인식 중",
                )
            else ->
                MicVisual(
                    bg = GlucoachColors.Surface,
                    tint = GlucoachColors.PrimaryDark,
                    icon = Icons.Outlined.Mic,
                    label = "음성으로 물어보기",
                )
        }
    Box(
        modifier =
            Modifier
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(bgColor)
                .border(1.5.dp, GlucoachColors.PrimaryDark, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = iconTint,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private data class MicVisual(
    val bg: Color,
    val tint: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

@Composable
private fun RecommendChip(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .shadow(elevation = 6.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(GlucoachColors.Surface)
                .border(1.5.dp, GlucoachColors.PrimaryDark, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.sm),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Restaurant,
                contentDescription = null,
                tint = GlucoachColors.PrimaryDark,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "음식 추천",
                color = GlucoachColors.PrimaryDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun voiceErrorMessage(reason: VoiceQueryManager.FailureReason): String =
    when (reason) {
        VoiceQueryManager.FailureReason.NO_PERMISSION -> "마이크 권한이 필요해요"
        VoiceQueryManager.FailureReason.NO_ENGINE -> "음성 인식을 사용할 수 없어요"
        VoiceQueryManager.FailureReason.NO_SPEECH -> "잘 못 들었어요, 다시 한 번 말씀해주세요"
        VoiceQueryManager.FailureReason.START_FAILED -> "마이크를 시작할 수 없어요"
        VoiceQueryManager.FailureReason.RECOGNIZER_ERROR -> "음성 인식 중 오류가 났어요"
    }

// ── TopBar ───────────────────────────────────────────────────────────

@Composable
private fun KikiChatTopBar(
    onBack: () -> Unit,
    fontSize: Float,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GlucoachColors.Surface)
                .padding(horizontal = GlucoachSpacing.md, vertical = GlucoachSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.ArrowBackIosNew,
                contentDescription = "뒤로",
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(GlucoachSpacing.sm))
        KikiAvatar(size = 40)
        Spacer(modifier = Modifier.width(GlucoachSpacing.md))
        Column {
            Text(
                text = "키키",
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "내 손 안의 작은 비서",
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        FontSizeControl(fontSize = fontSize, onIncrease = onIncrease, onDecrease = onDecrease)
    }
}

@Composable
private fun FontSizeControl(
    fontSize: Float,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
) {
    val minSize = 11f
    val maxSize = 20f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(GlucoachColors.PrimaryLight)
                    .border(1.dp, GlucoachColors.Primary.copy(alpha = 0.4f), CircleShape)
                    .clickable(enabled = fontSize > minSize) { onDecrease() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "−",
                color = if (fontSize > minSize) GlucoachColors.PrimaryDark else GlucoachColors.TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "가", color = GlucoachColors.TextSecondary, fontSize = fontSize.sp)
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(GlucoachColors.PrimaryLight)
                    .border(1.dp, GlucoachColors.Primary.copy(alpha = 0.4f), CircleShape)
                    .clickable(enabled = fontSize < maxSize) { onIncrease() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = if (fontSize < maxSize) GlucoachColors.PrimaryDark else GlucoachColors.TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── 말풍선 ────────────────────────────────────────────────────────────

@Composable
private fun KikiChatBubble(
    item: NotificationItem,
    fontSize: Float,
    showReplyButtons: Boolean = false,
    onReply: (replyText: String?, displayLabel: String) -> Unit = { _, _ -> },
    onClick: () -> Unit = {},
    onCompareClick: (String, String) -> Unit = { _, _ -> },
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.Top,
    ) {
        KikiAvatar(size = 40)
        Spacer(modifier = Modifier.width(GlucoachSpacing.sm))
        Column {
            Text(
                text = "키키",
                color = GlucoachColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    modifier =
                        Modifier
                            .widthIn(max = 260.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp))
                            .background(GlucoachColors.PrimaryLight)
                            .padding(horizontal = GlucoachSpacing.md, vertical = GlucoachSpacing.sm),
                ) {
                    MarkdownText(
                        text = item.message,
                        color = GlucoachColors.TextPrimary,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.5f).sp,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatTimestamp(parseIsoTimestamp(item.createdAt)),
                    color = GlucoachColors.TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            // AGENT_MEAL_FOLLOWUP 응답 버튼 (최신 미응답 메시지에만 표시)
            if (showReplyButtons) {
                Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                ChatReplyButtons(onReply = onReply)
            }
            // A/B 비교 결과가 있으면 비교 화면으로 이동하는 버튼 표시
            item.payload?.comparison?.let { comparison ->
                Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                CompareButton(
                    foodAName = comparison.foodA.name,
                    foodBName = comparison.foodB.name,
                    onClick = { onCompareClick(comparison.foodA.name, comparison.foodB.name) },
                )
            }
        }
    }
}

@Composable
private fun ChatReplyButtons(onReply: (replyText: String?, displayLabel: String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm)) {
        ChatReplyButton(
            text = "알겠어요",
            modifier = Modifier.weight(1f),
            onClick = { onReply("알겠어요.", "알겠어요") },
        )
        ChatReplyButton(
            text = "회의 중",
            modifier = Modifier.weight(1f),
            onClick = { onReply("지금 회의 중이에요.", "회의 중") },
        )
        ChatReplyButton(
            text = "괜찮아요",
            modifier = Modifier.weight(1f),
            onClick = { onReply(null, "괜찮아요") },
        )
    }
}

@Composable
private fun ChatReplyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(GlucoachColors.PrimaryDark)
                .clickable(onClick = onClick)
                .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun CompareButton(
    foodAName: String,
    foodBName: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(GlucoachColors.PrimaryLight)
                .border(1.5.dp, GlucoachColors.PrimaryDark, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.md, vertical = 8.dp),
    ) {
        Text(
            text = "A/B 비교: $foodAName vs $foodBName →",
            color = GlucoachColors.PrimaryDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UserChatBubble(
    message: ChatMessage.UserMessage,
    fontSize: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = formatTimestamp(message.timestamp),
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 2.dp, end = 6.dp),
        )
        Box(
            modifier =
                Modifier
                    .widthIn(max = 200.dp)
                    .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 2.dp))
                    .background(GlucoachColors.Surface)
                    .border(
                        1.dp,
                        GlucoachColors.Border,
                        RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 2.dp),
                    ).padding(horizontal = GlucoachSpacing.md, vertical = GlucoachSpacing.sm),
        ) {
            Text(
                text = message.text,
                color = GlucoachColors.TextPrimary,
                fontSize = fontSize.sp,
            )
        }
    }
}

@Composable
private fun DateSeparatorItem(label: String) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = GlucoachSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(color = GlucoachColors.Border)
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlucoachColors.Background)
                    .padding(horizontal = GlucoachSpacing.md, vertical = 4.dp),
        ) {
            Text(
                text = label,
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

// ── Avatar ───────────────────────────────────────────────────────────

@Composable
private fun KikiAvatar(size: Int) {
    Box(
        modifier =
            Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F6F9))
                .border(1.dp, GlucoachColors.Primary.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.kiki_main),
            contentDescription = "키키",
            modifier =
                Modifier
                    .size((size * 0.85f).dp)
                    .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}
