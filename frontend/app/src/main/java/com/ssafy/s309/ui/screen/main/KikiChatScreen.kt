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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class ChatMessage {
    data class KikiMessage(val item: NotificationItem) : ChatMessage()

    data class UserMessage(
        val text: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) : ChatMessage()
}

private data class StatusOption(val label: String, val replyText: String)

private val STATUS_OPTIONS =
    listOf(
        StatusOption("알겠어요", "알겠어요."),
        StatusOption("회의 중이에요", "지금 회의 중이에요."),
        StatusOption("괜찮아요", "괜찮아요."),
    )

private const val FONT_SIZE_DEFAULT = 14f
private const val FONT_SIZE_MIN = 11f
private const val FONT_SIZE_MAX = 20f

@HiltViewModel
class KikiChatViewModel
    @Inject
    constructor(
        private val healthRepository: HealthRepository,
        private val tokenManager: TokenManager,
    ) : ViewModel() {
        private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
        val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

        private val _fontSize = MutableStateFlow(FONT_SIZE_DEFAULT)
        val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

        private val httpClient =
            OkHttpClient.Builder()
                .callTimeout(8, TimeUnit.SECONDS)
                .build()

        init {
            viewModelScope.launch {
                _messages.value = healthRepository.getNotifications().map { ChatMessage.KikiMessage(it) }
            }
            viewModelScope.launch {
                healthRepository.glucoseAlertStream.collect { alert ->
                    _messages.update { listOf(ChatMessage.KikiMessage(alert)) + it }
                }
            }
        }

        fun increaseFontSize() = _fontSize.update { (it + 1f).coerceAtMost(FONT_SIZE_MAX) }

        fun decreaseFontSize() = _fontSize.update { (it - 1f).coerceAtLeast(FONT_SIZE_MIN) }

        fun sendUserReply(
            label: String,
            replyText: String,
        ) {
            _messages.update { listOf(ChatMessage.UserMessage(label)) + it }
            val userId = tokenManager.getUserId() ?: return
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val json =
                        JSONObject().apply {
                            put("user_id", userId)
                            put(
                                "trigger",
                                JSONObject().apply {
                                    put("reason", "user_response")
                                    put("meal_time", "")
                                    put("user_reply", replyText)
                                },
                            )
                        }.toString()
                    val request =
                        Request.Builder()
                            .url("$AI_BASE_URL/agent/post-meal")
                            .post(json.toRequestBody("application/json".toMediaType()))
                            .build()
                    httpClient.newCall(request).execute().close()
                } catch (e: Exception) {
                    Log.w(TAG, "reply send failed", e)
                }
            }
        }

        companion object {
            private const val AI_BASE_URL = "https://k14s309.p.ssafy.io/ai"
            private const val TAG = "KikiChatVM"
        }
    }

@Composable
fun KikiChatScreen(
    onBack: () -> Unit,
    onItemClick: (com.ssafy.s309.data.model.NotificationItem) -> Unit = {},
    viewModel: KikiChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
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

        if (messages.isEmpty()) {
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
                items(messages) { message ->
                    when (message) {
                        is ChatMessage.KikiMessage ->
                            KikiChatBubble(
                                item = message.item,
                                fontSize = fontSize,
                                onClick = { onItemClick(message.item) },
                            )
                        is ChatMessage.UserMessage -> UserChatBubble(message = message, fontSize = fontSize)
                    }
                }
                item { Spacer(modifier = Modifier.height(GlucoachSpacing.sm)) }
            }
        }

        HorizontalDivider(color = GlucoachColors.Border)
        StatusChipsBar(onChipClick = { label, reply -> viewModel.sendUserReply(label, reply) })
    }
}

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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(GlucoachColors.PrimaryLight)
                    .border(1.dp, GlucoachColors.Primary.copy(alpha = 0.4f), CircleShape)
                    .clickable(enabled = fontSize > FONT_SIZE_MIN) { onDecrease() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "−",
                color = if (fontSize > FONT_SIZE_MIN) GlucoachColors.PrimaryDark else GlucoachColors.TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "가",
            color = GlucoachColors.TextSecondary,
            fontSize = fontSize.sp,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(GlucoachColors.PrimaryLight)
                    .border(1.dp, GlucoachColors.Primary.copy(alpha = 0.4f), CircleShape)
                    .clickable(enabled = fontSize < FONT_SIZE_MAX) { onIncrease() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                color = if (fontSize < FONT_SIZE_MAX) GlucoachColors.PrimaryDark else GlucoachColors.TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatusChipsBar(onChipClick: (label: String, replyText: String) -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GlucoachColors.Surface)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm, Alignment.CenterHorizontally),
    ) {
        STATUS_OPTIONS.forEach { option ->
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlucoachColors.PrimaryLight)
                        .border(1.dp, GlucoachColors.Primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .clickable { onChipClick(option.label, option.replyText) }
                        .padding(horizontal = GlucoachSpacing.md, vertical = 6.dp),
            ) {
                Text(
                    text = option.label,
                    color = GlucoachColors.PrimaryDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun KikiChatBubble(
    item: NotificationItem,
    fontSize: Float,
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
        }
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

private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(timestamp))

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
