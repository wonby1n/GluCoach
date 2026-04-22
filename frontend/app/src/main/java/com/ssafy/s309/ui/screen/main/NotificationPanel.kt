package com.ssafy.s309.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.data.model.NotificationItem
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing

/**
 * "메인/알림" 화면. 메인 화면 위에 오른쪽에서 슬라이드 인되는 패널로 사용된다.
 *
 * 애니메이션/슬라이드 진입은 상위 [MainScreen] 의 AnimatedVisibility 에서 제어한다.
 *
 * @param notifications 표시할 알림 목록
 * @param onBack 뒤로가기 아이콘 클릭
 * @param onClearAll "모두 지우기" 클릭
 */
@Composable
fun NotificationPanel(
    notifications: List<NotificationItem>,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(GlucoachColors.Background)
                .padding(horizontal = GlucoachSpacing.xxl),
    ) {
        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        NotificationPanelTopBar(onBack = onBack)
        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        NotificationPanelTitleRow(onClearAll = onClearAll)
        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = notifications,
                key = { it.id },
            ) { item ->
                NotificationRow(item = item)
            }
        }
    }
}

@Composable
private fun NotificationPanelTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 뒤로가기 아이콘 자리 - 추후 ic_chevron_left drawable 로 교체
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            // asset 미주입 상태에서는 "<" 텍스트로 대체 (시각적 플레이스홀더)
            Text(
                text = "<",
                color = Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "알림 설정",
            color = Color.Black,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun NotificationPanelTitleRow(onClearAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "알림",
            color = Color.Black,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "모두 지우기",
            color = Color.Black,
            fontSize = 10.sp,
            modifier = Modifier.clickable(onClick = onClearAll),
        )
    }
}

@Composable
private fun NotificationRow(item: NotificationItem) {
    val textColor = if (item.isUnread) Color.Black else GlucoachColors.TextSecondary
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 알림 아이콘 자리 - 추후 type 별 drawable 로 교체
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GlucoachColors.Border),
        )
        Spacer(modifier = Modifier.size(13.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.title,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (item.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = item.timeAgoText,
                    color = textColor,
                    fontSize = 10.sp,
                )
            }
            Text(
                text = item.message,
                color = textColor,
                fontSize = 12.sp,
            )
        }
    }
}
