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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun NotificationPanel(
    notifications: List<NotificationItem>,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
    onMarkAllRead: () -> Unit = {},
    onNotificationClick: (NotificationItem) -> Unit = {},
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
        NotificationPanelTitleRow(onMarkAllRead = onMarkAllRead, onClearAll = onClearAll)
        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = notifications,
                key = { it.id },
            ) { item ->
                NotificationRow(
                    item = item,
                    onClick = { onNotificationClick(item) },
                )
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
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
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
private fun NotificationPanelTitleRow(
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit,
) {
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "전체 읽음",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onMarkAllRead),
            )
            Text(
                text = "|",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
            )
            Text(
                text = "모두 지우기",
                color = Color.Black,
                fontSize = 10.sp,
                modifier = Modifier.clickable(onClick = onClearAll),
            )
        }
    }
}

@Composable
private fun NotificationRow(
    item: NotificationItem,
    onClick: () -> Unit,
) {
    val textColor = if (item.isUnread) Color.Black else GlucoachColors.TextSecondary
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

@Composable
fun NotificationDetailOverlay(
    notification: NotificationItem,
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(enabled = false, onClick = {})
                    .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = notification.title,
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = notification.timeAgoText,
                    color = GlucoachColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = notification.message,
                color = GlucoachColors.TextPrimary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlucoachColors.Primary)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "확인",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
