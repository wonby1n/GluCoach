package com.ssafy.s309.ui.screen.main

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import com.ssafy.s309.ui.component.MarkdownText
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

@Composable
fun KikiAlarmDetailScreen(
    notification: com.ssafy.s309.data.model.NotificationItem? = null,
    isNewUser: Boolean = false,
    onBack: () -> Unit = {},
    onChatClick: () -> Unit = {},
    onMealReply: (replyText: String, displayLabel: String) -> Unit = { _, _ -> },
    onViewed: () -> Unit = {},
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notification?.id) {
        android.util.Log.d("KikiAlarm", "notification changed: id=${notification?.id} alertType=${notification?.alertType}")
        selectedOption = null
    }
    DisposableEffect(Unit) {
        onDispose { onViewed() }
    }
    val isPostMeal =
        notification?.alertType?.let {
            it.startsWith("AGENT_MEAL_FOLLOWUP")
        } == true
    android.util.Log.d("KikiAlarm", "recompose: selectedOption=$selectedOption isPostMeal=$isPostMeal")
    val emptyStateLines =
        if (isNewUser && notification == null) {
            listOf("반가워요. 키키와 함께해요.")
        } else {
            listOf("키키가 오늘 컨디션을 보고 있어요.")
        }
    val rawMessage = notification?.message
    val calendarReminder =
        remember(rawMessage, selectedOption) {
            if (selectedOption == "OKAY" || selectedOption == "BUSY" || rawMessage == null) {
                null
            } else {
                parseCalendarReminder(rawMessage)
            }
        }
    val parsedBody =
        remember(calendarReminder?.body) {
            calendarReminder?.body?.let { parseRecommendBody(it) }
        }
    val speechSource =
        parsedBody?.intro ?: calendarReminder?.body ?: rawMessage
    val speechLines =
        when (selectedOption) {
            "OKAY" -> listOf("좋아요! 지금 바로 움직여봐요.", "조금만 움직여도 혈당 조절에 도움이 돼요.")
            "BUSY" -> listOf("알겠어요!", "회의 끝나고 30분 뒤에 다시 알려드릴게요.")
            "DECLINE" -> speechSource?.split("\n") ?: emptyStateLines
            else -> speechSource?.split("\n") ?: emptyStateLines
        }

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
                    drawableRes = kikiImageRes(notification?.alertType ?: ""),
                    modifier = Modifier.size(220.dp),
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))

            val hasMenuCards = parsedBody != null && parsedBody.menus.isNotEmpty()

            if (calendarReminder != null) {
                CalendarReminderDetailCard(eventTitle = calendarReminder.eventTitle)
                Spacer(modifier = Modifier.height(GlucoachSpacing.md))
                RecommendHeaderCard()
                Spacer(modifier = Modifier.height(GlucoachSpacing.md))
            }

            if (!hasMenuCards) {
                SpeechBubble(lines = speechLines)
            }

            if (hasMenuCards) {
                parsedBody!!.menus.forEachIndexed { index, menu ->
                    if (index > 0) Spacer(modifier = Modifier.height(GlucoachSpacing.md))
                    MenuItemCard(menu)
                }
                parsedBody.outro?.let { outroText ->
                    Spacer(modifier = Modifier.height(GlucoachSpacing.md))
                    OutroTipCard(text = outroText)
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            if (isPostMeal && selectedOption == null && notification?.isUnread == true) {
                PostMealOptionButtons(
                    onOkay = {
                        android.util.Log.d("KikiAlarm", "onOkay clicked")
                        selectedOption = "OKAY"
                        onMealReply("알겠어요.", "알겠어요")
                    },
                    onBusy = {
                        android.util.Log.d("KikiAlarm", "onBusy clicked")
                        selectedOption = "BUSY"
                        onMealReply("지금 회의 중이에요.", "회의 중")
                    },
                    onDecline = {
                        android.util.Log.d("KikiAlarm", "onDecline clicked")
                        selectedOption = "DECLINE"
                    },
                )
                Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
            }

            if (notification?.displayTrace != null) {
                ExpandableInfoCard(
                    label = "키키가 확인한 내용 보기",
                    displayTrace = notification.displayTrace,
                )
            }

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

private data class RecommendMenu(
    val emoji: String,
    val name: String,
    val grade: String,
    val description: String,
)

private data class ParsedRecommendBody(
    val intro: String?,
    val menus: List<RecommendMenu>,
    val outro: String?,
)

private val MENU_LINE_REGEX = Regex("""^(.+?)\s*\(\s*(.+?)\s*\)\s*$""")

private fun parseRecommendBody(body: String): ParsedRecommendBody {
    val paragraphs = body.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
    val intro = StringBuilder()
    val outro = StringBuilder()
    val menus = mutableListOf<RecommendMenu>()
    var sawMenu = false
    for (paragraph in paragraphs) {
        val firstBreak = paragraph.indexOf('\n')
        val firstLine = if (firstBreak > 0) paragraph.substring(0, firstBreak).trim() else paragraph.trim()
        val rest = if (firstBreak > 0) paragraph.substring(firstBreak + 1).trim() else ""
        val match = MENU_LINE_REGEX.find(firstLine)
        val gradeText = match?.groupValues?.get(2)?.trim().orEmpty()
        val isMenu =
            match != null && rest.isNotEmpty() &&
                (gradeText.endsWith("등급") || gradeText.contains("메뉴"))
        if (isMenu) {
            val nameWithEmoji = match!!.groupValues[1].trim()
            val spaceIdx = nameWithEmoji.indexOf(' ')
            val emoji =
                if (spaceIdx > 0) nameWithEmoji.substring(0, spaceIdx).trim() else ""
            val name =
                if (spaceIdx > 0) nameWithEmoji.substring(spaceIdx + 1).trim() else nameWithEmoji
            menus.add(RecommendMenu(emoji = emoji, name = name, grade = gradeText, description = rest))
            sawMenu = true
        } else {
            val target = if (sawMenu) outro else intro
            if (target.isNotEmpty()) target.append("\n\n")
            target.append(paragraph)
        }
    }
    return ParsedRecommendBody(
        intro = intro.toString().trim().ifBlank { null },
        menus = menus,
        outro = outro.toString().trim().ifBlank { null },
    )
}

private fun gradeColors(grade: String): Pair<Color, Color> =
    when {
        grade.startsWith("S") -> GlucoachColors.GradeS to GlucoachColors.GradeSBg
        grade.startsWith("A") -> GlucoachColors.GradeA to GlucoachColors.GradeABg
        grade.startsWith("B") -> GlucoachColors.GradeB to GlucoachColors.GradeBBg
        grade.startsWith("C") -> GlucoachColors.GradeC to GlucoachColors.GradeCBg
        grade.startsWith("D") -> GlucoachColors.GradeD to GlucoachColors.GradeDBg
        grade.startsWith("F") -> GlucoachColors.GradeF to GlucoachColors.GradeFBg
        else -> GlucoachColors.PrimaryDark to GlucoachColors.SelectBadgeBg
    }

@Composable
private fun RecommendHeaderCard(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .shadow(6.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(
                    brush =
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    GlucoachColors.Primary,
                                    GlucoachColors.PrimaryDark,
                                ),
                        ),
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.55f)),
        )
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.RestaurantMenu,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "오늘의 메뉴 추천",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun MenuItemCard(
    menu: RecommendMenu,
    modifier: Modifier = Modifier,
) {
    val (badgeFg, badgeBg) = gradeColors(menu.grade)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface),
    ) {
        Box(
            modifier =
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(badgeFg),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (menu.emoji.isNotEmpty()) {
                    Text(text = menu.emoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = menu.name,
                    color = GlucoachColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(badgeBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = menu.grade,
                        color = badgeFg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            MarkdownText(
                text = menu.description,
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun OutroTipCard(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.TipBg)
                .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        MarkdownText(
            text = text,
            color = GlucoachColors.TextPrimary,
            fontSize = 15.sp,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun CalendarReminderDetailCard(
    eventTitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.CalendarBg)
                .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = GlucoachColors.CalendarAccent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "오늘의 일정",
                color = GlucoachColors.CalendarAccent,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = eventTitle,
            color = GlucoachColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
        )
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
                MarkdownText(
                    text = line,
                    color = GlucoachColors.TextPrimary,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                )
            }
        }
    }
}

@Composable
private fun ExpandableInfoCard(
    label: String,
    displayTrace: com.ssafy.s309.data.model.DisplayTrace,
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

                displayTrace.cards.forEachIndexed { index, card ->
                    val icon =
                        when (card.type) {
                            "meal" -> Icons.Outlined.Restaurant
                            "glucose" -> Icons.Outlined.ShowChart
                            "activity" -> Icons.Outlined.LocationOn
                            "sleep" -> Icons.Outlined.Bedtime
                            else -> Icons.Outlined.HelpOutline
                        }
                    ExpandedDetailRow(
                        icon = icon,
                        label = card.title,
                        value = card.description,
                    )
                    if (index < displayTrace.cards.lastIndex) {
                        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                    }
                }

                if (displayTrace.decision.reason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                    HorizontalDivider(color = GlucoachColors.Border)
                    Spacer(modifier = Modifier.height(GlucoachSpacing.md))
                    Text(
                        text = displayTrace.decision.reason,
                        color = GlucoachColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Start,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PostMealOptionButtons(
    onOkay: () -> Unit,
    onBusy: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm),
    ) {
        PostMealButton(text = "알겠어요", onClick = onOkay, modifier = Modifier.weight(1f))
        PostMealButton(text = "회의 중", onClick = onBusy, modifier = Modifier.weight(1f))
        PostMealButton(text = "괜찮아요", onClick = onDecline, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PostMealButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.PrimaryDark)
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@DrawableRes
private fun kikiImageRes(alertType: String): Int =
    when {
        alertType == "HIGH" -> R.drawable.kiki_severe_tired
        alertType == "LOW" -> R.drawable.kiki_low
        alertType == "SOS" -> R.drawable.kiki_sos
        alertType == "RISING" -> R.drawable.kiki_severe_tired
        alertType == "FALLING" -> R.drawable.kiki_low
        alertType.startsWith("AGENT_") -> R.drawable.kiki_agent
        alertType == "WEEKLY_REPORT" -> R.drawable.kiki_weekly_report
        else -> R.drawable.kiki_main
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
