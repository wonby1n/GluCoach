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
import androidx.compose.material.icons.outlined.GraphicEq
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
 *  - RESPONDING — "키키가 응답 중..." (TTS 발화 중)
 *  - LISTENING  — "듣고 있어요" + 펄스 애니메이션 + 실시간 partial transcript
 *
 * 화면 어떤 라우트에 있든 항상 위에 떠 있는 floating 컴포넌트로, MainActivity 의
 * setContent 루트 Box 에 직접 배치된다.
 */
@Composable
fun KikiVoiceOverlay(
    wakeWordManager: WakeWordManager,
    modifier: Modifier = Modifier,
) {
    val uiState by wakeWordManager.uiState.collectAsStateWithLifecycle()
    val partial by wakeWordManager.partialTranscript.collectAsStateWithLifecycle()

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
                if (uiState == WakeWordManager.UiState.LISTENING && partial.isNotBlank()) {
                    PartialTranscriptBubble(text = partial)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                StatusCapsule(state = uiState)
            }
        }
    }
}

@Composable
private fun StatusCapsule(state: WakeWordManager.UiState) {
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

    val (label, icon) =
        when (state) {
            WakeWordManager.UiState.RESPONDING ->
                "키키가 응답중..." to Icons.Outlined.RecordVoiceOver
            WakeWordManager.UiState.LISTENING ->
                "듣고 있어요" to Icons.Outlined.Mic
            WakeWordManager.UiState.IDLE -> "" to Icons.Outlined.Mic // 안 보이지만 컴파일러 만족용
        }

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
        if (state == WakeWordManager.UiState.LISTENING) {
            Spacer(modifier = Modifier.width(2.dp))
            ListeningDots()
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
