package com.ssafy.s309.ui.screen.main

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.WaterDrop
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.s309.data.model.DailyGlucoseItem
import com.ssafy.s309.data.model.WeeklyFoodItem
import com.ssafy.s309.data.model.WeeklyReportResponse
import com.ssafy.s309.ui.component.FoodCategoryImageMapper
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
    val isClickable: Boolean = false,
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
        name = foodDisplayName ?: foodName,
        imageResId = FoodCategoryImageMapper.getImageRes(null, foodName),
        grade = gradeMap[foodId] ?: if (type == "GOOD") "A" else "D",
    )

private fun String.toPatternChips(): List<String> =
    split(Regex("[.。\n]+"))
        .map { it.trim().trimEnd('.', ' ') }
        .filter { it.length > 2 }
        .take(5)

private fun weekDayLabel(dateStr: String): String =
    runCatching {
        val d = LocalDate.parse(dateStr)
        listOf("월", "화", "수", "목", "금", "토", "일")[d.dayOfWeek.value - 1] + "요일"
    }.getOrDefault("")

private fun WeeklyReportResponse.toSummaryCards(prevAvgGlucose: Double?) =
    listOf(
        run {
            val (changeText, changeColor) =
                when {
                    prevAvgGlucose == null -> "지난주 데이터 없음" to Color(0xFF9E9E9E)
                    avgGlucose > prevAvgGlucose + 0.5 -> "지난주보다 ${(avgGlucose - prevAvgGlucose).toInt()}mg 높아요" to Color(0xFFE96A6A)
                    avgGlucose < prevAvgGlucose - 0.5 -> "지난주보다 ${(prevAvgGlucose - avgGlucose).toInt()}mg 낮아요" to Color(0xFF2196F3)
                    else -> "지난주와 비슷해요" to Color(0xFF9E9E9E)
                }
            SummaryCard("평균 혈당", avgGlucose.toInt().toString(), "mg/dL", changeText, changeColor)
        },
        run {
            val stable = glucoseSd < 36
            SummaryCard(
                "혈당 변동폭",
                glucoseSd.toInt().toString(),
                "mg/dL",
                if (stable) "안정적" else "불안정적",
                if (stable) Color(0xFF2196F3) else Color(0xFFE96A6A),
            )
        },
        run {
            val label = dailyGlucose.maxByOrNull { it.max }?.let { "${weekDayLabel(it.date)} 기록" } ?: "이번 주 최고"
            SummaryCard("최고 혈당", maxGlucose.toInt().toString(), "mg/dL", label, Color(0xFFE96A6A), isClickable = true)
        },
        run {
            val label = dailyGlucose.minByOrNull { it.min }?.let { "${weekDayLabel(it.date)} 기록" } ?: "이번 주 최저"
            SummaryCard("최저 혈당", minGlucose.toInt().toString(), "mg/dL", label, Color(0xFF2196F3), isClickable = true)
        },
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
        is AIReportViewModel.UiState.DateList -> {
            AIReportDateListContent(
                state = state,
                modifier = modifier,
                onSelectReport = { viewModel.selectReport(it) },
                onGenerate = { viewModel.generateReport() },
            )
        }
        is AIReportViewModel.UiState.Detail -> {
            AIReportDetailContent(
                state = state,
                modifier = modifier,
                onBack = { viewModel.backToList() },
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

// ── 날짜 목록 화면 ──────────────────────────────────────────

@Composable
private fun AIReportDateListContent(
    state: AIReportViewModel.UiState.DateList,
    onSelectReport: (Int) -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
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

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            Button(
                onClick = onGenerate,
                enabled = !state.isGenerating,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlucoachColors.Primary),
            ) {
                if (state.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = if (state.isGenerating) "생성 중..." else "새로 생성하기",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            if (state.reports.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "아직 생성된 주간 보고서가 없어요\n새로 생성하기 버튼을 눌러보세요",
                        color = GlucoachColors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                    )
                }
            } else {
                Text(
                    text = "기간별 리포트",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(GlucoachSpacing.md))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.reports.forEachIndexed { index, report ->
                        ReportDateCard(
                            report = report,
                            onClick = { onSelectReport(index) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }
    }
}

@Composable
private fun ReportDateCard(
    report: WeeklyReportResponse,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = GlucoachColors.Primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = formatWeekRange(report.weekStart),
                    color = GlucoachColors.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = "목표 범위 내 ${report.timeInRange.toInt()}%  ·  평균 ${report.avgGlucose.toInt()} mg/dL",
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = GlucoachColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── 리포트 상세 화면 ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIReportDetailContent(
    state: AIReportViewModel.UiState.Detail,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPdfClick: () -> Unit,
) {
    val report = state.current
    var selectedFood by remember(report.id) { mutableStateOf<ReportFoodCard?>(null) }
    var showDailyDetail by remember(report.id) { mutableStateOf(false) }

    val prevAvgGlucose = remember(state.selectedIndex) { state.reports.getOrNull(state.selectedIndex + 1)?.avgGlucose }
    val summaryCards = remember(report.id) { report.toSummaryCards(prevAvgGlucose) }
    val stableFoods =
        remember(report.id, state.gradeMap) { report.foods.filter { it.type == "GOOD" }.map { it.toReportFoodCard(state.gradeMap) } }
    val cautionFoods =
        remember(report.id, state.gradeMap) { report.foods.filter { it.type == "BAD" }.map { it.toReportFoodCard(state.gradeMap) } }
    val (weeklyAvg, weeklyMax, weeklyMin) =
        remember(report.id) {
            if (report.dailyGlucose.isEmpty()) {
                Triple(
                    List<Float?>(7) { report.avgGlucose.toFloat() },
                    List<Float?>(7) { report.maxGlucose.toFloat() },
                    List<Float?>(7) { report.minGlucose.toFloat() },
                )
            } else {
                val weekStart = LocalDate.parse(report.weekStart)
                val avgSlots = arrayOfNulls<Float>(7)
                val maxSlots = arrayOfNulls<Float>(7)
                val minSlots = arrayOfNulls<Float>(7)
                report.dailyGlucose.forEach { item ->
                    val idx = (LocalDate.parse(item.date).toEpochDay() - weekStart.toEpochDay()).toInt()
                    if (idx in 0..6) {
                        avgSlots[idx] = item.avg.toFloat()
                        maxSlots[idx] = item.max.toFloat()
                        minSlots[idx] = item.min.toFloat()
                    }
                }
                Triple(avgSlots.toList(), maxSlots.toList(), minSlots.toList())
            }
        }
    val (tirColor, tirMessage) =
        when {
            report.timeInRange >= 70 -> Color(0xFF4CAF50) to "이번 주 혈당 관리 정말 대단해요! 키키도 함께 기뻐요 🎉"
            report.timeInRange >= 50 -> Color(0xFFFF9800) to "이번 주도 함께 잘 해가고 있어요! 키키가 항상 응원할게요 💪"
            else -> Color(0xFFE96A6A) to "키키가 함께할게요! 이번 주도 같이 건강하게 해봐요 🌟"
        }
    val patternItems = remember(report.id) { report.toPatternItems() }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "목록으로",
                        tint = GlucoachColors.TextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = "AI 주간 리포트",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlucoachColors.Primary.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    tint = GlucoachColors.Primary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = formatWeekRange(report.weekStart),
                    color = GlucoachColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Text(
                text = "이번 주 요약",
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

            Text(
                text = "목표 범위 내 ${report.timeInRange.toInt()}%",
                color = tirColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = tirMessage,
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))
        }

        WeeklySummaryRow(
            cards = summaryCards,
            onDetailClick = { showDailyDetail = true },
        )

        if (showDailyDetail) {
            GlucoseDailyDetailSheet(
                dailyGlucose = report.dailyGlucose,
                weekRange = formatWeekRange(report.weekStart),
                onDismiss = { showDailyDetail = false },
            )
        }

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            WeeklyGlucoseChart(
                weeklyAvg = weeklyAvg,
                weeklyMax = weeklyMax,
                weeklyMin = weeklyMin,
            )

            val hasSamsungData =
                (report.avgSleepMinutes != null && report.avgSleepMinutes > 0) ||
                    (report.avgSteps != null && report.avgSteps > 0)
            if (hasSamsungData) {
                Spacer(modifier = Modifier.height(GlucoachSpacing.xl))
                Text(
                    text = "건강 활동 데이터",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            report.avgSleepMinutes?.takeIf { it > 0 }?.let {
                Spacer(modifier = Modifier.height(GlucoachSpacing.md))
                SleepWeeklySection(avgSleepMinutes = it)
            }

            report.avgSteps?.takeIf { it > 0 }?.let {
                Spacer(modifier = Modifier.height(GlucoachSpacing.md))
                StepsWeeklySection(avgSteps = it)
            }

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

            if ((report.avgSleepMinutes != null && report.avgSleepMinutes > 0) ||
                (report.avgSteps != null && report.avgSteps > 0)
            ) {
                Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                SamsungHealthDisclaimer()
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }
    }
}

// ── 이번주 요약 카드 ─────────────────────────────────────

@Composable
private fun WeeklySummaryRow(
    cards: List<SummaryCard>,
    onDetailClick: () -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
    ) {
        cards.forEach { card ->
            SummaryStatItem(
                card = card,
                modifier = Modifier.width(130.dp),
                onClick = if (card.isClickable) onDetailClick else null,
            )
        }
    }
}

@Composable
private fun SummaryStatItem(
    card: SummaryCard,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .height(130.dp)
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(GlucoachSpacing.lg),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = card.title,
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Text(
                    text = "상세",
                    color = card.changeColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = card.value, color = GlucoachColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = card.unit, color = GlucoachColors.TextSecondary, fontSize = 11.sp)
        }
        Text(
            text = card.changeText,
            color = card.changeColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            lineHeight = 15.sp,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── 7일 혈당 추이 차트 ──────────────────────────────────

@Composable
private fun WeeklyGlucoseChart(
    weeklyAvg: List<Float?>,
    weeklyMax: List<Float?>,
    weeklyMin: List<Float?>,
) {
    var selectedMode by remember { mutableIntStateOf(0) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val days = listOf("월", "화", "수", "목", "금", "토", "일")
    val highThreshold = 180f
    val lowThreshold = 60f

    val points =
        when (selectedMode) {
            1 -> weeklyMax
            2 -> weeklyMin
            else -> weeklyAvg
        }
    val (lineColor, dangerColor) =
        when (selectedMode) {
            1 -> Color(0xFFE96A6A) to Color(0xFFE96A6A)
            2 -> Color(0xFF2196F3) to Color(0xFF2196F3)
            else -> GlucoachColors.Primary to Color(0xFFE96A6A)
        }
    val tabColors = listOf(GlucoachColors.Primary, Color(0xFFE96A6A), Color(0xFF2196F3))

    val dataMax = points.filterNotNull().maxOrNull() ?: highThreshold
    val dataMin = points.filterNotNull().minOrNull() ?: lowThreshold
    val rawRange = (dataMax - dataMin).coerceAtLeast(60f)
    val dataPadding = (rawRange * 0.25f).coerceAtLeast(20f)

    val maxVal: Float
    val minVal: Float
    if (selectedMode == 0) {
        // 평균: 60·180 기준선 모두 표시
        maxVal = maxOf(highThreshold + dataPadding * 0.5f, dataMax + dataPadding)
        minVal = (minOf(lowThreshold - dataPadding * 0.5f, dataMin - dataPadding)).coerceAtLeast(0f)
    } else {
        // 최고/최저: 실제 데이터 범위 기준으로만 Y축 결정 (데이터 min~max + 패딩)
        maxVal = dataMax + dataPadding
        minVal = (dataMin - dataPadding).coerceAtLeast(0f)
    }
    val range = (maxVal - minVal).coerceAtLeast(1f)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(lineColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WaterDrop,
                        contentDescription = null,
                        tint = lineColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(text = "이번 주 혈당 흐름", color = GlucoachColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "mg/dL", color = GlucoachColors.TextSecondary, fontSize = 11.sp)
                }
            }

            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(GlucoachColors.Border.copy(alpha = 0.5f))
                        .padding(3.dp),
            ) {
                listOf("평균", "최고", "최저").forEachIndexed { idx, label ->
                    val isSelected = selectedMode == idx
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(17.dp))
                                .background(if (isSelected) tabColors[idx] else Color.Transparent)
                                .clickable {
                                    selectedMode = idx
                                    selectedIndex = null
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else GlucoachColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        val chartHeight = 150.dp
        var chartWidthPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val widthDp = with(density) { chartWidthPx.toDp() }
        val stepDp = if (points.size > 1 && chartWidthPx > 0) widthDp / (points.size - 1) else 0.dp
        val showHighLine = highThreshold in minVal..maxVal
        val showLowLine = lowThreshold in minVal..maxVal
        val y180 =
            if (showHighLine) {
                (chartHeight * (1f - (highThreshold - minVal) / range) - 17.dp).coerceIn(
                    0.dp,
                    chartHeight - 14.dp,
                )
            } else {
                null
            }
        val y60 =
            if (showLowLine) {
                (chartHeight * (1f - (lowThreshold - minVal) / range) - 17.dp).coerceIn(
                    0.dp,
                    chartHeight - 14.dp,
                )
            } else {
                null
            }

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
                if (showHighLine) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, yFor(highThreshold)),
                        end = Offset(w, yFor(highThreshold)),
                        strokeWidth = 1f,
                        pathEffect = dashEffect,
                    )
                }
                if (showLowLine) {
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, yFor(lowThreshold)),
                        end = Offset(w, yFor(lowThreshold)),
                        strokeWidth = 1f,
                        pathEffect = dashEffect,
                    )
                }

                if (points.size >= 2) {
                    val step = w / (points.size - 1)
                    for (i in 1 until points.size) {
                        val v0 = points[i - 1] ?: continue
                        val v1 = points[i] ?: continue
                        val x0 = (i - 1) * step
                        val x1 = i * step
                        val cx = (x0 + x1) / 2f
                        if (selectedMode == 0) {
                            val p0High = v0 > highThreshold
                            val p1High = v1 > highThreshold
                            if (p0High == p1High) {
                                val path =
                                    Path().apply {
                                        moveTo(x0, yFor(v0))
                                        cubicTo(cx, yFor(v0), cx, yFor(v1), x1, yFor(v1))
                                    }
                                drawPath(path, if (p0High) dangerColor else lineColor, style = Stroke(2.5f, cap = StrokeCap.Round))
                            } else {
                                val ratio = (highThreshold - v0) / (v1 - v0)
                                val xMid = x0 + ratio * (x1 - x0)
                                val yMid = yFor(highThreshold)
                                val cx0 = (x0 + xMid) / 2f
                                val cx1 = (xMid + x1) / 2f
                                val path0 =
                                    Path().apply {
                                        moveTo(x0, yFor(v0))
                                        cubicTo(cx0, yFor(v0), cx0, yMid, xMid, yMid)
                                    }
                                drawPath(path0, if (p0High) dangerColor else lineColor, style = Stroke(2.5f, cap = StrokeCap.Round))
                                val path1 =
                                    Path().apply {
                                        moveTo(xMid, yMid)
                                        cubicTo(cx1, yMid, cx1, yFor(v1), x1, yFor(v1))
                                    }
                                drawPath(path1, if (p1High) dangerColor else lineColor, style = Stroke(2.5f, cap = StrokeCap.Round))
                            }
                        } else {
                            val path =
                                Path().apply {
                                    moveTo(x0, yFor(v0))
                                    cubicTo(cx, yFor(v0), cx, yFor(v1), x1, yFor(v1))
                                }
                            drawPath(path, lineColor, style = Stroke(2.5f, cap = StrokeCap.Round))
                        }
                    }
                    selectedIndex?.let { idx ->
                        val v = points[idx] ?: return@let
                        val cx = idx * step
                        drawLine(
                            color = GlucoachColors.TextSecondary,
                            start = Offset(cx, yFor(v) + 12f),
                            end = Offset(cx, h),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                        )
                    }
                    points.forEachIndexed { i, v ->
                        if (v == null) return@forEachIndexed
                        val cx = i * step
                        val cy = yFor(v)
                        val pointColor = if (selectedMode == 0 && v > highThreshold) dangerColor else lineColor
                        if (selectedIndex == i) {
                            drawCircle(color = pointColor, radius = 8f, center = Offset(cx, cy))
                            drawCircle(color = GlucoachColors.Surface, radius = 4f, center = Offset(cx, cy))
                        } else {
                            drawCircle(color = GlucoachColors.Surface, radius = 6f, center = Offset(cx, cy))
                            drawCircle(color = pointColor, radius = 5f, center = Offset(cx, cy), style = Stroke(width = 2.5f))
                        }
                    }
                }
            }

            y180?.let { offset ->
                Text(
                    text = "180",
                    color = Color(0xFFE96A6A),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.offset(y = offset),
                )
            }
            y60?.let { offset ->
                Text(
                    text = "60",
                    color = Color(0xFF2196F3),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.offset(y = offset),
                )
            }

            selectedIndex?.let { idx ->
                val v = points.getOrNull(idx) ?: return@let
                val text = "${v.toInt()}"
                val textHalfWidth = (text.length * 3.5f).dp
                val cx = stepDp * idx
                val cyRatio = 1f - ((v - minVal) / range)
                val cy = chartHeight * cyRatio
                val tipX = (cx - textHalfWidth).coerceIn(0.dp, widthDp - textHalfWidth * 2)
                val tipY = maxOf(0.dp, cy - 28.dp)
                Text(
                    text = text,
                    color = lineColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = tipX, y = tipY),
                )
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            days.forEachIndexed { idx, day ->
                val isSelected = selectedIndex == idx
                val defaultTextColor =
                    when (idx) {
                        5 -> Color(0xFF2196F3)
                        6 -> Color(0xFFE96A6A)
                        else -> GlucoachColors.TextSecondary
                    }
                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) lineColor else Color.Transparent)
                            .clickable { selectedIndex = if (selectedIndex == idx) null else idx },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = day, color = if (isSelected) Color.White else defaultTextColor, fontSize = 12.sp)
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
    ) {
        Text(text = "이번 주 패턴 분석", color = GlucoachColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)

        patterns.forEach { item ->
            PatternCard(item)
        }
    }
}

@Composable
private fun PatternCard(item: PatternItem) {
    val accentColor = item.iconBgColor
    val chips = remember(item.description) { item.description.toPatternChips() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .shadow(2.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface),
    ) {
        Box(
            modifier =
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor),
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.lg),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(accentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Icon(imageVector = item.icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                Text(text = item.title, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                chips.forEach { chip ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = 0.07f))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                        )
                        Text(
                            text = chip,
                            color = GlucoachColors.TextPrimary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── 일별 혈당 상세 BottomSheet ──────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GlucoseDailyDetailSheet(
    dailyGlucose: List<DailyGlucoseItem>,
    weekRange: String,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(text = "일별 혈당 기록", color = GlucoachColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = weekRange, color = GlucoachColors.TextSecondary, fontSize = 13.sp)
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Outlined.Close, contentDescription = "닫기", tint = GlucoachColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(GlucoachColors.Primary.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "날짜",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1.2f),
                )
                Text(
                    text = "평균",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "최고",
                    color = Color(0xFFE96A6A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "최저",
                    color = Color(0xFF2196F3),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
            }

            if (dailyGlucose.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "일별 데이터가 없어요", color = GlucoachColors.TextSecondary, fontSize = 14.sp)
                }
            } else {
                dailyGlucose.forEach { item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = weekDayLabel(item.date),
                            color = GlucoachColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1.2f),
                        )
                        Text(
                            text = "${item.avg.toInt()}",
                            color = GlucoachColors.TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "${item.max.toInt()}",
                            color = Color(0xFFE96A6A),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "${item.min.toInt()}",
                            color = Color(0xFF2196F3),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(GlucoachColors.Border),
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFE96A6A)))
                    Text(text = "최고", color = GlucoachColors.TextSecondary, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2196F3)))
                    Text(text = "최저", color = GlucoachColors.TextSecondary, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(GlucoachColors.TextSecondary))
                    Text(text = "단위 mg/dL", color = GlucoachColors.TextSecondary, fontSize = 12.sp)
                }
            }
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

// ── 일별 mock 데이터 생성 ─────────────────────────────────

private fun generateMockDailyData(
    average: Double,
    multipliers: List<Float>,
): List<Float> {
    val raw = multipliers.map { (average * it).toFloat() }
    val rawAvg = raw.sum() / raw.size
    val scale = average.toFloat() / rawAvg
    return raw.map { it * scale }
}

// ── 공용 주간 막대 차트 ──────────────────────────────────

@Composable
private fun WeeklyBarChart(
    dailyValues: List<Float>,
    barColor: Color,
    maxValue: Float,
    labelFor: (Float) -> String,
    goalValue: Float? = null,
    modifier: Modifier = Modifier,
) {
    val days = listOf("월", "화", "수", "목", "금", "토", "일")

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            dailyValues.forEach { value ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(text = labelFor(value), color = barColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val slotW = size.width / days.size
            val barW = slotW * 0.55f
            val r = 8f

            goalValue?.let { goal ->
                val goalY = size.height * (1f - (goal / maxValue).coerceIn(0f, 1f))
                drawLine(
                    color = barColor.copy(alpha = 0.30f),
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }

            dailyValues.forEachIndexed { i, value ->
                val barH = (value / maxValue).coerceIn(0f, 1f) * size.height
                val x = i * slotW + (slotW - barW) / 2f
                val topY = size.height - barH
                val meetsGoal = goalValue == null || value >= goalValue

                val path =
                    Path().apply {
                        moveTo(x, topY + r)
                        quadraticBezierTo(x, topY, x + r, topY)
                        lineTo(x + barW - r, topY)
                        quadraticBezierTo(x + barW, topY, x + barW, topY + r)
                        lineTo(x + barW, size.height)
                        lineTo(x, size.height)
                        close()
                    }
                drawPath(path, if (meetsGoal) barColor else barColor.copy(alpha = 0.45f))
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEachIndexed { idx, day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = day,
                        color =
                            when (idx) {
                                5 -> Color(0xFF2196F3)
                                6 -> Color(0xFFE96A6A)
                                else -> GlucoachColors.TextSecondary
                            },
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

// ── 삼성 헬스: 수면 섹션 ────────────────────────────────

@Composable
private fun SleepWeeklySection(avgSleepMinutes: Double) {
    val sleepColor = Color(0xFF3F51B5)
    val totalHours = (avgSleepMinutes / 60).toInt()
    val remainMins = (avgSleepMinutes % 60).toInt()
    val (statusColor, statusText) =
        when {
            avgSleepMinutes >= 420 -> sleepColor to "권장 수면 시간을 달성했어요"
            avgSleepMinutes >= 360 -> Color(0xFFFF9800) to "수면이 조금 부족해요"
            else -> Color(0xFFE96A6A) to "수면이 많이 부족해요"
        }
    val dailyMinutes =
        remember(avgSleepMinutes) {
            generateMockDailyData(avgSleepMinutes, listOf(0.68f, 1.12f, 0.82f, 1.30f, 0.92f, 0.75f, 1.08f))
        }
    val chartMax = maxOf(480f, dailyMinutes.maxOrNull() ?: 480f) * 1.15f

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(sleepColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Outlined.Bedtime, contentDescription = null, tint = sleepColor, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "수면", color = GlucoachColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(text = "이번 주 일별 수면 시간", color = GlucoachColors.TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = "${totalHours}시간", color = GlucoachColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    if (remainMins > 0) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${remainMins}분",
                            color = GlucoachColors.TextSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
                Text(text = "평균", color = GlucoachColors.TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        WeeklyBarChart(
            dailyValues = dailyMinutes,
            barColor = sleepColor,
            maxValue = chartMax,
            labelFor = { v -> "${(v / 60).toInt()}h" },
            goalValue = 480f,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = statusText, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "목표 8시간", color = GlucoachColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

// ── 삼성 헬스: 걸음 수 섹션 ──────────────────────────────

@Composable
private fun StepsWeeklySection(avgSteps: Double) {
    val stepsColor = Color(0xFF4CAF50)
    val weeklyTotal = (avgSteps * 7).toLong()
    val (statusColor, statusText) =
        when {
            avgSteps >= 10000 -> stepsColor to "목표 달성! 활발하게 움직이고 있어요"
            avgSteps >= 7000 -> Color(0xFF2196F3) to "목표까지 조금만 더 걸어봐요"
            else -> Color(0xFFFF9800) to "조금 더 활동적으로 움직여봐요"
        }
    val dailySteps =
        remember(avgSteps) {
            generateMockDailyData(avgSteps, listOf(0.87f, 1.18f, 0.92f, 1.28f, 0.97f, 0.83f, 1.14f))
        }
    val chartMax = maxOf(10000f, dailySteps.maxOrNull() ?: 10000f) * 1.15f

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(stepsColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DirectionsWalk,
                    contentDescription = null,
                    tint = stepsColor,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = "걸음 수", color = GlucoachColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(text = "이번 주 일별 걸음 수", color = GlucoachColors.TextSecondary, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "%,d".format(avgSteps.toInt()),
                        color = GlucoachColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(text = "보", color = GlucoachColors.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text(text = "일평균 · 주간 %,d보".format(weeklyTotal), color = GlucoachColors.TextSecondary, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        WeeklyBarChart(
            dailyValues = dailySteps,
            barColor = stepsColor,
            maxValue = chartMax,
            labelFor = { v -> "%.1fk".format(v / 1000f) },
            goalValue = 10000f,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = statusText, color = statusColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = "목표 10,000보", color = GlucoachColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

// ── 삼성 헬스 데이터 출처 안내 ────────────────────────────

@Composable
private fun SamsungHealthDisclaimer() {
    Text(
        text = "※ 수면 및 걸음 수 데이터는 삼성 헬스(Samsung Health)에서 수집된 데이터를 기반으로 분석되었습니다. 해당 데이터는 건강 참고 목적으로 제공되며, 의료적 진단이나 치료에 활용되지 않습니다.",
        color = GlucoachColors.TextSecondary.copy(alpha = 0.6f),
        fontSize = 10.sp,
        lineHeight = 15.sp,
        textAlign = TextAlign.Start,
    )
}

// ── 음식 식사 기록 MealRecordCard ────────────────────────

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
