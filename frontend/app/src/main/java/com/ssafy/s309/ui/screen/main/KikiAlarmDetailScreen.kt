package com.ssafy.s309.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.component.KikiImage
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

@Composable
fun KikiAlarmDetailScreen(
    onBack: () -> Unit = {},
    onChatClick: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.PrimaryLight.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Outlined.ArrowBackIosNew,
                    contentDescription = "뒤로",
                    tint = GlucoachColors.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
        ) {
            Text(
                text = "키키 알림",
                color = GlucoachColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .size(width = 140.dp, height = 28.dp)
                            .drawBehind {
                                drawOval(
                                    brush =
                                        Brush.radialGradient(
                                            colors =
                                                listOf(
                                                    Color(0xFF8ACDD6).copy(alpha = 0.4f),
                                                    Color.Transparent,
                                                ),
                                            center = Offset(size.width / 2, size.height / 2),
                                            radius = size.width / 2,
                                        ),
                                )
                            },
                )
                KikiImage(
                    drawableRes = R.drawable.kiki_agent,
                    modifier = Modifier.size(220.dp),
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))

            SpeechBubble(
                lines =
                    listOf(
                        "점심 먹고 한 시간이 지났어요.",
                        "최근 움직임이 거의 없는데 스트레칭 어때요?",
                    ),
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            ExpandableInfoCard(label = "키키가 확인한 내용 보기")

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChatClick)
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "지난 대화 보기",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 13.sp,
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = GlucoachColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }
    }
}

@Composable
private fun SpeechBubble(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 1.dp)
                    .size(14.dp)
                    .rotate(45f)
                    .background(GlucoachColors.Surface),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp)
                    .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                    .clip(RoundedCornerShape(GlucoachCorner.card))
                    .background(GlucoachColors.Surface)
                    .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            lines.forEach { line ->
                Text(
                    text = line,
                    color = GlucoachColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun ExpandableInfoCard(
    label: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = null,
                tint = GlucoachColors.Primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = GlucoachColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector =
                    if (expanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                contentDescription = null,
                tint = GlucoachColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                HorizontalDivider(color = GlucoachColors.Border)
                Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

                ExpandedDetailRow(
                    icon = Icons.Outlined.Restaurant,
                    label = "식사 12:00",
                    value = "김치찌개 + 공기밥",
                )
                Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                ExpandedDetailRow(
                    icon = Icons.Outlined.ShowChart,
                    label = "혈당 흐름",
                    value = "125 → 145 → 165 mg/dL",
                )
                Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                ExpandedDetailRow(
                    icon = Icons.Outlined.LocationOn,
                    label = "최근 30분 걸음수",
                    value = "23보",
                )

                Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                HorizontalDivider(color = GlucoachColors.Border)
                Spacer(modifier = Modifier.height(GlucoachSpacing.md))

                Text(
                    text = "그래서 지금은 가벼운 활동을 제안했어요.",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpandedDetailRow(
    icon: ImageVector,
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GlucoachColors.PrimaryDark,
            modifier =
                Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = GlucoachColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
