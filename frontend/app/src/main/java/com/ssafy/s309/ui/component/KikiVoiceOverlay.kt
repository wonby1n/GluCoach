package com.ssafy.s309.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.voice.WakeWordManager

/**
 * 빅스비/시리 스타일 화면 하단 음성 인식 오버레이.
 *
 *  - WakeWordManager.uiState 가 IDLE 이면 숨김 (앱 평소 화면 그대로)
 *  - RESPONDING        — "키키가 응답 중..." (TTS 발화 중)
 *  - LISTENING         — "듣고 있어요" + 펄스 애니메이션 + 실시간 partial transcript
 *  - THINKING          — "잠시만요..." 모래시계 + (1.5초간 final transcript 도 위에 표시)
 *  - SHOWING_RESPONSE  — AI 응답 카드. 본문 탭 → 채팅 화면 이동 (+ dismiss),
 *                        우상단 X 버튼 → dismiss. 안 누르면 10초 후 자동 dismiss.
 *
 * 화면 어떤 라우트에 있든 항상 위에 떠 있는 floating 컴포넌트로, MainActivity 의
 * setContent 루트 Box 에 직접 배치된다.
 *
 * @param onResponseTapped 응답 카드 본문이 탭됐을 때 호출. 보통 채팅 화면으로 navigate
 *                         + [WakeWordManager.dismissResponse] 같은 후속 동작.
 */
@Composable
fun KikiVoiceOverlay(
    wakeWordManager: WakeWordManager,
    modifier: Modifier = Modifier,
    onResponseTapped: () -> Unit = {},
) {
    val uiState by wakeWordManager.uiState.collectAsStateWithLifecycle()
    val partial by wakeWordManager.partialTranscript.collectAsStateWithLifecycle()
    val response by wakeWordManager.responseText.collectAsStateWithLifecycle()
    val thinkingHint by wakeWordManager.thinkingHint.collectAsStateWithLifecycle()

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = uiState != WakeWordManager.UiState.IDLE,
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 2 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 },
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(bottom = 92.dp, start = 24.dp, end = 24.dp)
                        .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 응답 카드 (SHOWING_RESPONSE 상태 메인 UI). 본문 탭 → 채팅 이동, X → 닫기.
                if (uiState == WakeWordManager.UiState.SHOWING_RESPONSE && response.isNotBlank()) {
                    ResponseCard(
                        text = response,
                        onBodyTap = onResponseTapped,
                        onClose = { wakeWordManager.dismissResponse() },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                // LISTENING / THINKING 직후의 transcript 표시 (사용자 발화 확인용).
                if (
                    (uiState == WakeWordManager.UiState.LISTENING || uiState == WakeWordManager.UiState.THINKING) &&
                    partial.isNotBlank()
                ) {
                    PartialTranscriptBubble(text = partial)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                // SHOWING_RESPONSE 일 땐 status capsule 안 띄움 — 카드가 메인.
                if (uiState != WakeWordManager.UiState.SHOWING_RESPONSE) {
                    // THINKING 일 때만 dynamic hint 로 capsule label 덮어씀.
                    val overrideLabel =
                        if (uiState == WakeWordManager.UiState.THINKING && thinkingHint.isNotBlank()) {
                            thinkingHint
                        } else {
                            null
                        }
                    StatusCapsule(state = uiState, customLabel = overrideLabel)
                }
            }
        }
    }
}

@Composable
private fun StatusCapsule(
    state: WakeWordManager.UiState,
    customLabel: String? = null,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice-pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (state == WakeWordManager.UiState.LISTENING) 1.15f else 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "voice-pulse-scale",
    )

    val (defaultLabel, icon) =
        when (state) {
            WakeWordManager.UiState.RESPONDING ->
                "키키가 응답중..." to Icons.Outlined.RecordVoiceOver
            WakeWordManager.UiState.LISTENING ->
                "듣고 있어요" to Icons.Outlined.Mic
            WakeWordManager.UiState.THINKING ->
                "잠시만요..." to Icons.Outlined.HourglassEmpty
            // 아래 두 케이스는 호출 측에서 capsule 자체를 안 띄우지만 when exhaustive 만족용.
            WakeWordManager.UiState.SHOWING_RESPONSE -> "" to Icons.Outlined.AutoAwesome
            WakeWordManager.UiState.IDLE -> "" to Icons.Outlined.Mic
        }
    // THINKING 동안 cycling hint 가 들어오면 그걸로 덮어씀.
    val label = customLabel?.takeIf { it.isNotBlank() } ?: defaultLabel

    Row(
        modifier =
            Modifier
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(GlucoachColors.PrimaryDark)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .scale(pulseScale)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (state == WakeWordManager.UiState.LISTENING || state == WakeWordManager.UiState.THINKING) {
            Spacer(modifier = Modifier.width(2.dp))
            ListeningDots()
        }
    }
}

/**
 * Siri/Bixby 스타일 응답 카드.
 *  - 본문 영역 (header 행 + 응답 텍스트) 탭 → [onBodyTap] (보통 채팅 화면으로 이동)
 *  - 우상단 X 버튼 탭 → [onClose] (그 자리에서 닫기만)
 *  - 사용자 입력 없으면 [WakeWordManager.RESPONSE_AUTO_DISMISS_MS] 후 자동 dismiss
 *
 * X 버튼은 별도 clickable 로 두고 카드 본체 clickable 과 분리해서, 버튼 탭이 본문 탭으로
 * 전파되지 않게 한다.
 */
@Composable
private fun ResponseCard(
    text: String,
    onBodyTap: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(GlucoachColors.Surface)
                .border(1.dp, GlucoachColors.PrimaryDark.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                .clickable(onClick = onBodyTap)
                .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            modifier = Modifier.padding(end = 32.dp), // X 버튼 자리 확보
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(GlucoachColors.PrimaryDark),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = "키키",
                    color = GlucoachColors.PrimaryDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "탭해서 채팅 열기",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
            Text(
                text = text,
                color = GlucoachColors.TextPrimary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            )
        }
        // 우상단 닫기 버튼 — 본문 clickable 과 별도 clickable 로 분리해 탭 이벤트 격리.
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "닫기",
                tint = GlucoachColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 듣는 중 표시용 펄스 점 3개. 각자 다른 위상으로 깜빡이는 흔한 패턴.
 */
@Composable
private fun ListeningDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0, 200, 400).forEach { delay ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 600, delayMillis = delay, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "dot-alpha-$delay",
            )
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun PartialTranscriptBubble(text: String) {
    Row(
        modifier =
            Modifier
                .widthIn(max = 320.dp)
                .shadow(elevation = 4.dp, shape = RoundedCornerShape(18.dp))
                .clip(RoundedCornerShape(18.dp))
                .background(GlucoachColors.Surface)
                .border(1.dp, GlucoachColors.Border, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.GraphicEq,
            contentDescription = null,
            tint = GlucoachColors.PrimaryDark,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            color = GlucoachColors.TextPrimary,
            fontSize = 13.sp,
        )
    }
}
