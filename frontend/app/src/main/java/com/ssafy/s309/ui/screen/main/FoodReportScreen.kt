package com.ssafy.s309.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.R
import com.ssafy.s309.data.model.FoodGradeInfo
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

// ── 등급 색상 (같은 패키지 내 Detail 에서도 사용) ──────────────

internal fun gradeColor(grade: String): Color =
    when (grade) {
        "S" -> GlucoachColors.PrimaryDark
        "A" -> GlucoachColors.Primary
        "B" -> GlucoachColors.PrimaryLight
        "C" -> GlucoachColors.Border
        "D" -> com.ssafy.s309.ui.theme.Error
        else -> Color(0xFF9E9E9E)
    }

internal fun gradeBgColor(grade: String): Color =
    when (grade) {
        "S" -> GlucoachColors.GradeSBg
        "A" -> GlucoachColors.GradeABg
        "B" -> GlucoachColors.GradeBBg
        "C" -> GlucoachColors.GradeCBg
        "D" -> GlucoachColors.GradeDBg
        else -> Color(0xFFF5F5F5)
    }

// ── 진입점 ──────────────────────────────────────────────

@Composable
fun FoodReportContent(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FoodReportViewModel = hiltViewModel(),
    onNavigateToMealLog: (date: String, mealId: Int) -> Unit = { _, _ -> },
    targetFoodName: String? = null,
    onTargetFoodHandled: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val foodHistoryState by viewModel.foodHistory.collectAsStateWithLifecycle()
    var selectedGrade by remember { mutableStateOf<String?>(null) }

    if (selectedGrade != null) {
        BackHandler { selectedGrade = null }
    }

    // 키보드 배너 딥링크: targetFoodName 매칭되는 GradeFoodItem 찾아 자동으로 상세 히스토리 시트까지 연다.
    // 매칭 실패 시 (사용자가 안 먹은 음식 등) 메인 화면만 열고 조용히 종료.
    LaunchedEffect(uiState, targetFoodName) {
        if (targetFoodName == null) return@LaunchedEffect
        val state = uiState as? FoodReportUiState.Success ?: return@LaunchedEffect
        val match =
            state.gradeFoodItems.entries
                .asSequence()
                .flatMap { (grade, foods) -> foods.asSequence().map { food -> food to grade } }
                .firstOrNull { (food, _) -> food.name == targetFoodName }
        if (match != null) {
            val (food, grade) = match
            selectedGrade = grade
            viewModel.loadFoodHistory(food, grade)
        }
        onTargetFoodHandled()
    }

    when (val state = uiState) {
        is FoodReportUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GlucoachColors.Primary)
            }
        }

        is FoodReportUiState.Error -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.message,
                        color = GlucoachColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(GlucoachSpacing.md))
                    Button(onClick = { viewModel.loadFoodGrades() }) {
                        Text("다시 시도")
                    }
                }
            }
        }

        is FoodReportUiState.Success -> {
            Crossfade(
                targetState = selectedGrade,
                animationSpec = tween(300),
                label = "food-report-crossfade",
            ) { grade ->
                if (grade == null) {
                    FoodReportMainContent(
                        gradeInfoList = state.gradeInfoList,
                        onGradeClick = { selectedGrade = it },
                        onBack = onBack,
                        modifier = modifier,
                    )
                } else {
                    FoodReportDetailContent(
                        gradeInfoList = state.gradeInfoList,
                        gradeFoodItems = state.gradeFoodItems,
                        initialGrade = grade,
                        onBack = { selectedGrade = null },
                        onFoodClick = { food, g -> viewModel.loadFoodHistory(food, g) },
                        foodHistoryState = foodHistoryState,
                        onDismissFoodHistory = { viewModel.clearFoodHistory() },
                        onNavigateToMealLog = onNavigateToMealLog,
                        modifier = modifier,
                    )
                }
            }
        }
    }
}

// ── 메인 페이지 ──────────────────────────────────────────

@Composable
private fun FoodReportMainContent(
    gradeInfoList: List<FoodGradeInfo>,
    onGradeClick: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(GlucoachSpacing.xxl))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "뒤로가기",
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(GlucoachSpacing.sm))
            Text(
                text = "내 음식 성적표",
                color = GlucoachColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(GlucoachSpacing.xl))

        AIAnalysisCard()

        Spacer(Modifier.height(GlucoachSpacing.xl))

        Text(
            text = "요약",
            color = GlucoachColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(GlucoachSpacing.md))

        GradePieChart(gradeInfoList = gradeInfoList)

        Spacer(Modifier.height(GlucoachSpacing.xl))

        Text(
            text = "등급 상세",
            color = GlucoachColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(GlucoachSpacing.md))

        gradeInfoList.forEach { gradeInfo ->
            GradeListItem(
                gradeInfo = gradeInfo,
                onClick = { onGradeClick(gradeInfo.grade) },
            )
            Spacer(Modifier.height(GlucoachSpacing.md))
        }

        Spacer(Modifier.height(GlucoachSpacing.xxl))
    }
}

// ── AI 분석 카드 ──────────────────────────────────────────

@Composable
private fun AIAnalysisCard() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Primary.copy(alpha = 0.12f))
                .padding(GlucoachSpacing.lg),
        verticalAlignment = Alignment.Top,
    ) {
        Image(
            painter = painterResource(id = R.drawable.kiki_main),
            contentDescription = "AI 마스코트",
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )

        Spacer(Modifier.width(GlucoachSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "AI 분석",
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(GlucoachSpacing.xs))
            Text(
                text = "키키님은 단백질 위주 식사에 잘 반응해요\n밀가루 쌀 같은 탄수화물은 식사 후 혈당을 크게 올리니 양 조절을 꼭 하셔야 해요",
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

// ── 등급 리스트 아이템 ──────────────────────────────────────

@Composable
private fun GradeListItem(
    gradeInfo: FoodGradeInfo,
    onClick: () -> Unit,
) {
    val color = gradeColor(gradeInfo.grade)
    val textOnFlag =
        if (gradeInfo.grade == "B") GlucoachColors.TextPrimary else Color.White

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val path =
                    Path().apply {
                        moveTo(w * 0.1f, 0f)
                        lineTo(w * 0.9f, 0f)
                        quadraticTo(w, 0f, w, h * 0.1f)
                        lineTo(w, h * 0.7f)
                        lineTo(w * 0.5f, h)
                        lineTo(0f, h * 0.7f)
                        lineTo(0f, h * 0.1f)
                        quadraticTo(0f, 0f, w * 0.1f, 0f)
                        close()
                    }
                drawPath(path = path, color = color, style = Fill)
            }
            Text(
                text = gradeInfo.grade,
                color = textOnFlag,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(GlucoachSpacing.lg))

        Text(
            text = gradeInfo.title,
            color = GlucoachColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = "${gradeInfo.count}",
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "개",
            color = color,
            fontSize = 14.sp,
            modifier = Modifier.padding(end = 4.dp),
        )
        Text(
            text = ">",
            color = GlucoachColors.TextSecondary,
            fontSize = 16.sp,
        )
    }
}

// ── 파이 차트 (요약) ──────────────────────────────────────────

private val GRADE_ORDER = listOf("S", "A", "B", "C", "D")

@Composable
private fun GradePieChart(gradeInfoList: List<FoodGradeInfo>) {
    val entries =
        GRADE_ORDER.map { grade ->
            val count = gradeInfoList.firstOrNull { it.grade == grade }?.count ?: 0
            grade to count
        }
    val total = entries.sumOf { it.second }.coerceAtLeast(1)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(Color(0xFFF7F9FB))
                .padding(GlucoachSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(8.dp),
        ) {
            val hasData = entries.any { it.second > 0 }

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (!hasData) {
                    drawArc(
                        color = Color(0xFFE0E0E0),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        size = size,
                    )
                } else {
                    var startAngle = -90f
                    entries.forEach { (grade, count) ->
                        if (count > 0) {
                            val sweep = (count.toFloat() / total) * 360f
                            drawArc(
                                color = gradeColor(grade),
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = true,
                                size = size,
                            )
                            startAngle += sweep
                        }
                    }
                }
            }

            if (!hasData) {
                Text(
                    text = "데이터가 없어요",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Spacer(Modifier.width(GlucoachSpacing.lg))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            entries.forEach { (grade, count) ->
                val pct = if (total > 0) (count * 100) / total else 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(gradeColor(grade)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${grade}등급",
                        color = GlucoachColors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.width(44.dp),
                    )
                    Text(
                        text = "$pct%",
                        color = if (grade == "D") gradeColor("D") else GlucoachColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (grade == "D") FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── 등급 배지 행 ──────────────────────────────────────────

@Composable
internal fun GradeBadgeRow(
    gradeInfoList: List<FoodGradeInfo>,
    selectedGrade: String?,
    onGradeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        gradeInfoList.forEach { gradeInfo ->
            val isSelected = gradeInfo.grade == selectedGrade
            GradeBadge(
                gradeInfo = gradeInfo,
                isSelected = isSelected,
                onClick = { onGradeClick(gradeInfo.grade) },
            )
        }
    }
}

@Composable
private fun GradeBadge(
    gradeInfo: FoodGradeInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = gradeColor(gradeInfo.grade)
    val textOnFlag =
        if (gradeInfo.grade == "B" || gradeInfo.grade == "C") GlucoachColors.TextPrimary else Color.White
    val badgeSize = if (isSelected) 76.dp else 52.dp
    val fontSize = if (isSelected) 30.sp else 20.sp
    val countFontSize = if (isSelected) 15.sp else 12.sp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(badgeSize)) {
                val w = size.width
                val h = size.height
                val path =
                    Path().apply {
                        moveTo(w * 0.1f, 0f)
                        lineTo(w * 0.9f, 0f)
                        quadraticTo(w, 0f, w, h * 0.1f)
                        lineTo(w, h * 0.7f)
                        lineTo(w * 0.5f, h)
                        lineTo(0f, h * 0.7f)
                        lineTo(0f, h * 0.1f)
                        quadraticTo(0f, 0f, w * 0.1f, 0f)
                        close()
                    }
                drawPath(
                    path = path,
                    color = if (isSelected) color else color.copy(alpha = 0.35f),
                    style = Fill,
                )
            }
            Text(
                text = gradeInfo.grade,
                color = textOnFlag,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = (-4).dp),
            )
        }

        Spacer(Modifier.height(GlucoachSpacing.xs))

        Text(
            text = "${gradeInfo.count}개",
            color = if (isSelected) color else color.copy(alpha = 0.5f),
            fontSize = countFontSize,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
