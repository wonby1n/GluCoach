package com.ssafy.s309.ui.screen.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

// ── 데이터 ──────────────────────────────────────────────

private data class SummaryCard(
    val title: String,
    val value: String,
    val unit: String,
    val changeText: String,
    val changeColor: Color,
)

private data class ReportFoodCard(
    val name: String,
    @DrawableRes val imageResId: Int,
    val grade: String,
    val maxGlucose: Int,
    val recoveryTime: String,
)

private data class PatternItem(
    val icon: ImageVector,
    val iconBgColor: Color,
    val title: String,
    val description: String,
)

private val summaryCards =
    listOf(
        SummaryCard("평균 혈당", "112", "mg/dL", "▼ 8 개선", GlucoachColors.Primary),
        SummaryCard("혈당 변동폭", "31", "mg/dL", "안정적", GlucoachColors.Primary),
        SummaryCard("목표 범위 내", "74", "%", "▲ +2 향상", GlucoachColors.Primary),
    )

private val stableFoods =
    listOf(
        ReportFoodCard("연어 샐러드", R.drawable.salmon_salad, "A", 125, "1시간"),
        ReportFoodCard("고등어 구이 정식", R.drawable.grilled_mackerel, "A", 135, "1시간 12분"),
        ReportFoodCard("키토 김밥", R.drawable.keto_kimbap, "A", 137, "1시간 16분"),
    )

private val cautionFoods =
    listOf(
        ReportFoodCard("짜장면", R.drawable.jjajangmyeon, "C", 185, "2시간 15분"),
        ReportFoodCard("짬뽕", R.drawable.jjambbong, "B", 165, "1시간 50분"),
        ReportFoodCard("떡볶이", R.drawable.keto_kimbap, "D", 210, "2시간 40분"),
    )

private val weeklyGlucose = listOf(105f, 118f, 110f, 125f, 108f, 132f, 112f)

private val patternItems =
    listOf(
        PatternItem(
            icon = Icons.AutoMirrored.Outlined.TrendingDown,
            iconBgColor = GlucoachColors.Primary,
            title = "오후 3시 혈당 하락 패턴",
            description = "매일 오후 3시경 규칙적으로 혈당이 낮아지는 패턴이 감지됐어요.",
        ),
        PatternItem(
            icon = Icons.AutoMirrored.Outlined.TrendingUp,
            iconBgColor = GlucoachColors.SpikeBadgeText,
            title = "점심 시간 혈당이 저녁보다 28% 높아요",
            description = "점심 식후 스파이크를 줄이는 게 이번 주 핵심 과제에요.",
        ),
        PatternItem(
            icon = Icons.Outlined.Bedtime,
            iconBgColor = GlucoachColors.Primary,
            title = "수면 중 혈당 안정적",
            description = "수면 중 평균 혈당 95 mg/dL로 목표 범위를 잘 유지하고 있어요.",
        ),
    )

// ── 진입점 ──────────────────────────────────────────────

@Composable
fun AIReportContent(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

            Text(
                text = "AI 주간 리포트",
                color = GlucoachColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Text(
                text = "이번주 요약",
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))
        }

        // 가로 스크롤 요약 카드 (패딩 바깥에서 시작해야 끝까지 스크롤 가능)
        WeeklySummaryRow()

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            WeeklyGlucoseChart()

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Text(
                text = "음식 선정 TOP / WORST",
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))
        }

        FoodTopWorstSection()

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            WeeklyPatternAnalysis()

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            ) {
                OutlinedButton(
                    onClick = { },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = GlucoachColors.TextSecondary,
                        ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlucoachColors.Border),
                ) {
                    Text(
                        text = "지난 요약 보기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = { },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = GlucoachColors.Primary,
                            contentColor = Color.White,
                        ),
                ) {
                    Text(
                        text = "PDF 내보내기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }
    }
}

// ── 이번주 요약 카드 ─────────────────────────────────────

@Composable
private fun WeeklySummaryRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
    ) {
        summaryCards.forEach { card ->
            SummaryStatItem(card = card, modifier = Modifier.width(130.dp))
        }
    }
}

@Composable
private fun SummaryStatItem(
    card: SummaryCard,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(120.dp)
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.lg),
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = card.title,
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = card.value,
                    color = GlucoachColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = card.unit,
                    color = GlucoachColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = card.changeText,
                color = card.changeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── 7일 혈당 추이 차트 ──────────────────────────────────

@Composable
private fun WeeklyGlucoseChart() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Text(
            text = "7일 혈당 추이",
            color = GlucoachColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        val chartHeight = 150.dp

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val minVal = 50f
                val maxVal = 200f
                val range = maxVal - minVal

                fun yFor(v: Float) = h - ((v - minVal) / range) * h

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                val gridColor = GlucoachColors.ChartGrid

                drawLine(
                    color = gridColor,
                    start = Offset(0f, yFor(180f)),
                    end = Offset(w, yFor(180f)),
                    strokeWidth = 1f,
                    pathEffect = dashEffect,
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, yFor(60f)),
                    end = Offset(w, yFor(60f)),
                    strokeWidth = 1f,
                    pathEffect = dashEffect,
                )

                val points = weeklyGlucose
                if (points.size >= 2) {
                    val step = w / (points.size - 1)

                    val path =
                        Path().apply {
                            moveTo(0f, yFor(points[0]))
                            for (i in 1 until points.size) {
                                val x0 = (i - 1) * step
                                val x1 = i * step
                                val cx = (x0 + x1) / 2f
                                cubicTo(
                                    cx,
                                    yFor(points[i - 1]),
                                    cx,
                                    yFor(points[i]),
                                    x1,
                                    yFor(points[i]),
                                )
                            }
                        }
                    drawPath(
                        path = path,
                        color = GlucoachColors.Primary,
                        style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                    )

                    points.forEachIndexed { i, v ->
                        val cx = i * step
                        val cy = yFor(v)
                        drawCircle(
                            color = GlucoachColors.Surface,
                            radius = 6f,
                            center = Offset(cx, cy),
                        )
                        drawCircle(
                            color = GlucoachColors.Primary,
                            radius = 5f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.5f),
                        )
                    }
                }
            }

            Text(
                text = "180",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Text(
                text = "60",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("월", "화", "수", "목", "금", "토", "일").forEach { day ->
                Text(
                    text = day,
                    color = GlucoachColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

// ── 음식 선정 TOP / WORST ───────────────────────────────

@Composable
private fun FoodTopWorstSection() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val foods = if (selectedTab == 0) stableFoods else cautionFoods

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface),
    ) {
        // 탭
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GlucoachSpacing.lg)
                    .padding(top = GlucoachSpacing.lg),
        ) {
            FoodTab(
                text = "안정적인 음식",
                isSelected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.weight(1f),
            )
            FoodTab(
                text = "주의할 음식",
                isSelected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        // 음식 카드 스와이프
        LazyRow(
            contentPadding = PaddingValues(horizontal = GlucoachSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
        ) {
            items(foods) { food ->
                FoodGradeCard(food = food)
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
    }
}

@Composable
private fun FoodTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(vertical = GlucoachSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = if (isSelected) GlucoachColors.Primary else GlucoachColors.TextSecondary,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(GlucoachSpacing.xs))
        Box(
            modifier =
                Modifier
                    .width(80.dp)
                    .height(2.dp)
                    .background(
                        if (isSelected) GlucoachColors.Primary else Color.Transparent,
                    ),
        )
    }
}

@Composable
private fun FoodGradeCard(food: ReportFoodCard) {
    val gradeColor =
        when (food.grade) {
            "A" -> GlucoachColors.Primary
            "B" -> GlucoachColors.GradeB
            "C" -> GlucoachColors.SpikeBadgeText
            else -> GlucoachColors.GradeD
        }

    Column(
        modifier =
            Modifier
                .width(140.dp)
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .border(1.dp, GlucoachColors.Border, RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Image(
                painter = painterResource(id = food.imageResId),
                contentDescription = food.name,
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(gradeColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = food.grade,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Text(
            text = food.name,
            color = GlucoachColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "최고 ${food.maxGlucose}mg/dL",
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
        )
        Text(
            text = "복귀 ${food.recoveryTime}",
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
        )
    }
}

// ── 이번 주 패턴 분석 ───────────────────────────────────

@Composable
private fun WeeklyPatternAnalysis() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Text(
            text = "이번 주 패턴 분석",
            color = GlucoachColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        patternItems.forEachIndexed { index, item ->
            PatternRow(item)
            if (index < patternItems.lastIndex) {
                Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
            }
        }
    }
}

@Composable
private fun PatternRow(item: PatternItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.iconBgColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = item.iconBgColor,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(modifier = Modifier.width(GlucoachSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = GlucoachColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(GlucoachSpacing.xs))
            Text(
                text = item.description,
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}
