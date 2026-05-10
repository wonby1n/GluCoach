package com.ssafy.s309.ui.screen.main

import android.util.Log
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.R
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing
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
        private val tokenManager: TokenManager,
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

        private var timeoutJob: Job? = null
        private var currentPage = -1

        init {
            loadNextPage()
            // 실시간 혈당 알림 스트림
            viewModelScope.launch {
                healthRepository.glucoseAlertStream.collect { alert ->
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
                                            ),
                                        )
                                    }
                                }
                        if (newMessages.isNotEmpty()) {
                            val raw = _messages.value.filterNot { it is ChatMessage.DateSeparator }
                            _messages.value = withDateSeparators(newMessages + raw)
                        }
                    }
                    .onFailure { Log.w(TAG, "FCM 후 메시지 재조회 실패", it) }
            }
        }

        fun sendFoodRecommendCommand() {
            if (_isWaitingForAgent.value) return
            viewModelScope.launch {
                runCatching { healthRepository.sendFoodRecommendCommand() }
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
                val userId = tokenManager.getUserId() ?: return
                viewModelScope.launch {
                    healthRepository.sendPostMealReply(userId, replyText)
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
        try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (e: Exception) {
            LocalDateTime.parse(iso).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()
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
    viewModel: KikiChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

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

        if (messages.isEmpty() && !isLoadingMore) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                        .weight(1f)
                        .padding(horizontal = GlucoachSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(GlucoachSpacing.lg),
                reverseLayout = true,
            ) {
                item { Spacer(modifier = Modifier.height(GlucoachSpacing.sm)) }

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
    }
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
                text = "GlucoFit AI 어시스턴트",
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
                    Text(
                        text = item.message,
                        color = GlucoachColors.TextPrimary,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.5f).sp,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = item.timeAgoText,
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
