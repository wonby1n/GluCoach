package com.ssafy.s309.ui.screen.main

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.s309.data.model.WeeklyFoodItem
import com.ssafy.s309.data.model.WeeklyReportResponse
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── 내부 UI 모델 ─────────────────────────────────────────

private data class SummaryCard(
    val title: String,
    val value: String,
    val unit: String,
    val changeText: String,
    val changeColor: Color,
)

private data class ReportFoodCard(
    val name: String,
    @DrawableRes val imageResId: Int = 0,
    val grade: String,
    val maxGlucose: Int = 0,
    val recoveryTime: String = "",
)

private data class FoodMealRecord(
    val date: String,
    val mealType: String,
    val foodName: String,
    val peakGlucose: Int,
    val recoveryTime: String,
)

private data class PatternItem(
    val icon: ImageVector,
    val iconBgColor: Color,
    val title: String,
    val description: String,
)

// ── 변환 헬퍼 ────────────────────────────────────────────

private fun WeeklyFoodItem.toReportFoodCard(gradeMap: Map<Int, String>) =
    ReportFoodCard(
        name = foodName,
        imageResId = 0,
        grade = gradeMap[foodId] ?: if (type == "GOOD") "A" else "D",
    )

private fun WeeklyReportResponse.toSummaryCards() =
    listOf(
        SummaryCard("평균 혈당", avgGlucose.toInt().toString(), "mg/dL", "SD ${glucoseSd.toInt()}", GlucoachColors.Primary),
        SummaryCard("혈당 변동폭", glucoseSd.toInt().toString(), "mg/dL", "안정적", GlucoachColors.Primary),
        SummaryCard("목표 범위 내", timeInRange.toInt().toString(), "%", "▲ ${timeAboveRange.toInt()}% 고혈당", GlucoachColors.Primary),
    )

private fun WeeklyReportResponse.toPatternItems(): List<PatternItem> =
    buildList {
        if (aiSummary.isNotBlank()) {
            add(PatternItem(Icons.AutoMirrored.Outlined.TrendingDown, GlucoachColors.Primary, "이번 주 패턴", aiSummary))
        }
        if (aiSuggest.isNotBlank()) {
            add(PatternItem(Icons.AutoMirrored.Outlined.TrendingUp, GlucoachColors.SpikeBadgeText, "개선 제안", aiSuggest))
        }
        if (isEmpty()) {
            add(PatternItem(Icons.Outlined.Bedtime, GlucoachColors.Primary, "분석 중", "AI가 주간 패턴을 분석하고 있어요."))
        }
    }

private fun formatWeekRange(weekStart: String): String =
    runCatching {
        val start = LocalDate.parse(weekStart)
        val end = start.plusDays(6)
        val fmt = DateTimeFormatter.ofPattern("yyyy.MM.dd")
        "${start.format(fmt)}~${end.format(fmt)}"
    }.getOrDefault(weekStart)

// ── 진입점 ──────────────────────────────────────────────

@Composable
fun AIReportContent(
    modifier: Modifier = Modifier,
    viewModel: AIReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    when (val state = uiState) {
        is AIReportViewModel.UiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlucoachColors.Primary)
            }
        }
        is AIReportViewModel.UiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        color = GlucoachColors.TextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.load() },
                        colors = ButtonDefaults.buttonColors(containerColor = GlucoachColors.Primary),
                    ) {
                        Text("다시 시도")
                    }
                }
            }
        }
        is AIReportViewModel.UiState.Success -> {
            AIReportSuccessContent(
                state = state,
                modifier = modifier,
                onPreviousClick = { viewModel.showPrevious() },
                onNextClick = { viewModel.showNext() },
                onPdfClick = {
                    viewModel.getPdfUrl(
                        id = state.current.id,
                        onSuccess = { url ->
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        onError = { },
                    )
                },
            )
        }
    }
}

// ── 성공 화면 ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIReportSuccessContent(
    state: AIReportViewModel.UiState.Success,
    modifier: Modifier = Modifier,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPdfClick: () -> Unit,
) {
    val report = state.current
    var selectedFood by remember(report.id) { mutableStateOf<ReportFoodCard?>(null) }

    val summaryCards = remember(report.id) { report.toSummaryCards() }
    val stableFoods =
        remember(report.id, state.gradeMap) { report.foods.filter { it.type == "GOOD" }.map { it.toReportFoodCard(state.gradeMap) } }
    val cautionFoods =
        remember(report.id, state.gradeMap) { report.foods.filter { it.type == "BAD" }.map { it.toReportFoodCard(state.gradeMap) } }
    val weeklyGlucose = remember(report.id) { List(7) { report.avgGlucose.toFloat() } }
    val patternItems = remember(report.id) { report.toPatternItems() }

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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatWeekRange(report.weekStart),
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
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

        WeeklySummaryRow(cards = summaryCards)

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            WeeklyGlucoseChart(weeklyGlucose = weeklyGlucose)

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Text(
                text = "음식 선정 TOP / WORST",
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))
        }

        FoodTopWorstSection(
            stableFoods = stableFoods,
            cautionFoods = cautionFoods,
            onFoodClick = { selectedFood = it },
        )

        selectedFood?.let { food ->
            FoodMealHistorySheet(
                food = food,
                weekRange = formatWeekRange(report.weekStart),
                records = emptyList(),
                onDismiss = { selectedFood = null },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            WeeklyPatternAnalysis(patterns = patternItems)

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            ) {
                OutlinedButton(
                    onClick = onPreviousClick,
                    enabled = state.hasPrevious,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = GlucoachColors.TextSecondary,
                            disabledContentColor = GlucoachColors.TextSecondary.copy(alpha = 0.4f),
                        ),
                    border =
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.hasPrevious) GlucoachColors.Border else GlucoachColors.Border.copy(alpha = 0.4f),
                        ),
                ) {
                    Text(
                        text = "지난 요약 보기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = onPdfClick,
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
private fun WeeklySummaryRow(cards: List<SummaryCard>) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
    ) {
        cards.forEach { card ->
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
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Text(
                text = card.title,
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = card.value, color = GlucoachColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = card.unit, color = GlucoachColors.TextSecondary, fontSize = 11.sp)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
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
private fun WeeklyGlucoseChart(weeklyGlucose: List<Float>) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val days = listOf("월", "화", "수", "목", "금", "토", "일")
    val minVal = 50f
    val maxVal = 200f
    val range = maxVal - minVal

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Text(text = "7일 혈당 추이", color = GlucoachColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        val chartHeight = 150.dp
        var chartWidthPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val widthDp = with(density) { chartWidthPx.toDp() }
        val stepDp = if (weeklyGlucose.size > 1 && chartWidthPx > 0) widthDp / (weeklyGlucose.size - 1) else 0.dp

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .padding(horizontal = 14.dp)
                    .onSizeChanged { chartWidthPx = it.width },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

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
                                cubicTo(cx, yFor(points[i - 1]), cx, yFor(points[i]), x1, yFor(points[i]))
                            }
                        }
                    drawPath(path = path, color = GlucoachColors.Primary, style = Stroke(width = 2.5f, cap = StrokeCap.Round))

                    selectedIndex?.let { idx ->
                        val cx = idx * step
                        drawLine(
                            color = GlucoachColors.TextSecondary,
                            start = Offset(cx, yFor(points[idx]) + 12f),
                            end = Offset(cx, h),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        )
                    }

                    points.forEachIndexed { i, v ->
                        val cx = i * step
                        val cy = yFor(v)
                        if (selectedIndex == i) {
                            drawCircle(color = GlucoachColors.Primary, radius = 8f, center = Offset(cx, cy))
                            drawCircle(color = GlucoachColors.Surface, radius = 4f, center = Offset(cx, cy))
                        } else {
                            drawCircle(color = GlucoachColors.Surface, radius = 6f, center = Offset(cx, cy))
                            drawCircle(color = GlucoachColors.Primary, radius = 5f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
                        }
                    }
                }
            }

            selectedIndex?.let { idx ->
                val v = weeklyGlucose[idx]
                val text = "${v.toInt()}"
                val textHalfWidth = (text.length * 3.5f).dp
                val cx = stepDp * idx
                val cyRatio = 1f - ((v - minVal) / range)
                val cy = chartHeight * cyRatio
                val tipX = (cx - textHalfWidth).coerceIn(0.dp, widthDp - textHalfWidth * 2)
                val tipY = maxOf(0.dp, cy - 28.dp)
                Text(
                    text = text,
                    color = GlucoachColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = tipX, y = tipY),
                )
            }

            Text(text = "180", color = GlucoachColors.TextSecondary, fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart))
            Text(
                text = "60",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { idx, day ->
                val isSelected = selectedIndex == idx
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) GlucoachColors.Primary else Color.Transparent)
                            .clickable { selectedIndex = if (selectedIndex == idx) null else idx },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = day, color = if (isSelected) Color.White else GlucoachColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

// ── 음식 선정 TOP / WORST ───────────────────────────────

@Composable
private fun FoodTopWorstSection(
    stableFoods: List<ReportFoodCard>,
    cautionFoods: List<ReportFoodCard>,
    onFoodClick: (ReportFoodCard) -> Unit = {},
) {
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GlucoachSpacing.lg)
                    .padding(top = GlucoachSpacing.lg),
        ) {
            FoodTab(text = "안정적인 음식", isSelected = selectedTab == 0, onClick = { selectedTab = 0 }, modifier = Modifier.weight(1f))
            FoodTab(text = "주의할 음식", isSelected = selectedTab == 1, onClick = { selectedTab = 1 }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        if (foods.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(80.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (selectedTab == 0) "안정적인 음식이 없어요" else "주의할 음식이 없어요",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = GlucoachSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            ) {
                items(foods) { food ->
                    FoodGradeCard(food = food, onClick = { onFoodClick(food) })
                }
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
        modifier = modifier.clickable(onClick = onClick).padding(vertical = GlucoachSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = if (isSelected) GlucoachColors.Primary else GlucoachColors.TextSecondary,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(modifier = Modifier.height(GlucoachSpacing.xs))
        Box(modifier = Modifier.width(80.dp).height(2.dp).background(if (isSelected) GlucoachColors.Primary else Color.Transparent))
    }
}

@Composable
private fun FoodGradeCard(
    food: ReportFoodCard,
    onClick: () -> Unit = {},
) {
    val gradeColor =
        when (food.grade) {
            "S" -> GlucoachColors.GradeS
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
                .clickable(onClick = onClick)
                .padding(GlucoachSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            // 이미지가 없으면 이니셜 원형으로 대체
            Box(
                modifier =
                    Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GlucoachColors.Border),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = food.name.firstOrNull()?.toString() ?: "?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlucoachColors.TextSecondary,
                )
            }
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(gradeColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = food.grade, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Text(
            text = food.name,
            color = GlucoachColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (food.maxGlucose > 0) {
            Text(text = "최고 ${food.maxGlucose}mg/dL", color = GlucoachColors.TextSecondary, fontSize = 11.sp)
        }
        if (food.recoveryTime.isNotEmpty()) {
            Text(text = "복귀 ${food.recoveryTime}", color = GlucoachColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

// ── 이번 주 패턴 분석 ───────────────────────────────────

@Composable
private fun WeeklyPatternAnalysis(patterns: List<PatternItem>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Text(text = "이번 주 패턴 분석", color = GlucoachColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        patterns.forEachIndexed { index, item ->
            PatternRow(item)
            if (index < patterns.lastIndex) Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
        }
    }
}

@Composable
private fun PatternRow(item: PatternItem) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(item.iconBgColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = item.icon, contentDescription = null, tint = item.iconBgColor, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(GlucoachSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, color = GlucoachColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(GlucoachSpacing.xs))
            Text(text = item.description, color = GlucoachColors.TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

// ── 음식 식사 기록 BottomSheet ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodMealHistorySheet(
    food: ReportFoodCard,
    weekRange: String,
    records: List<FoodMealRecord>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = GlucoachColors.Surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = 24.dp, bottom = 32.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text(text = food.name, color = GlucoachColors.TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "지난 주 식사 기록", color = GlucoachColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = weekRange, color = GlucoachColors.TextSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "닫기", tint = GlucoachColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            if (records.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(text = "이번 주 식사 기록이 없어요", color = GlucoachColors.TextSecondary, fontSize = 14.sp)
                }
            } else {
                records.forEachIndexed { index, record ->
                    if (index == 0 || records[index - 1].date != record.date) {
                        if (index > 0) Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                        Text(text = record.date, color = GlucoachColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                    }
                    MealRecordCard(record)
                    if (index < records.lastIndex && records[index + 1].date == record.date) {
                        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                    }
                }
            }
        }
    }
}

@Composable
private fun MealRecordCard(record: FoodMealRecord) {
    val icon =
        when (record.mealType) {
            "아침" -> Icons.Outlined.WbSunny
            "점심" -> Icons.Outlined.LightMode
            else -> Icons.Outlined.DarkMode
        }
    val bgColor =
        when (record.mealType) {
            "아침" -> Color(0xFFFFF8E1)
            "점심" -> Color(0xFFDDF3F8)
            else -> Color(0xFFE8EAF6)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(bgColor)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = GlucoachColors.TextSecondary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(GlucoachSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "${record.mealType} 식사", color = GlucoachColors.TextSecondary, fontSize = 12.sp)
            Text(text = record.foodName, color = GlucoachColors.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "최고 ${record.peakGlucose}mg/dL",
                color = GlucoachColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(text = "복귀 ${record.recoveryTime}", color = GlucoachColors.TextSecondary, fontSize = 11.sp)
        }
    }
}
