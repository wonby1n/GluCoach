package com.ssafy.s309.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.data.model.FoodGradeInfo
import com.ssafy.s309.data.repository.FoodReportMockData
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

// ── 등급 색상 (같은 패키지 내 Detail 에서도 사용) ──────────────

internal fun gradeColor(grade: String): Color =
    when (grade) {
        "S" -> GlucoachColors.GradeS
        "A" -> GlucoachColors.GradeA
        "B" -> GlucoachColors.GradeB
        "C" -> GlucoachColors.GradeC
        "D" -> GlucoachColors.GradeD
        "F" -> GlucoachColors.GradeF
        else -> Color(0xFF9E9E9E)
    }

internal fun gradeBgColor(grade: String): Color =
    when (grade) {
        "S" -> GlucoachColors.GradeSBg
        "A" -> GlucoachColors.GradeABg
        "B" -> GlucoachColors.GradeBBg
        "C" -> GlucoachColors.GradeCBg
        "D" -> GlucoachColors.GradeDBg
        "F" -> GlucoachColors.GradeFBg
        else -> Color(0xFFF5F5F5)
    }

// ── 진입점 ──────────────────────────────────────────────

@Composable
fun FoodReportContent(modifier: Modifier = Modifier) {
    var selectedGrade by remember { mutableStateOf<String?>(null) }

    if (selectedGrade != null) {
        BackHandler { selectedGrade = null }
    }

    Crossfade(
        targetState = selectedGrade,
        animationSpec = tween(300),
        label = "food-report-crossfade",
    ) { grade ->
        if (grade == null) {
            FoodReportMainContent(
                onGradeClick = { selectedGrade = it },
                modifier = modifier,
            )
        } else {
            val gradeInfo =
                FoodReportMockData.gradeInfoList.firstOrNull { it.grade == grade }
                    ?: return@Crossfade
            val foods = FoodReportMockData.gradeFoodItems[grade] ?: emptyList()
            FoodReportDetailContent(
                gradeInfo = gradeInfo,
                foods = foods,
                onBack = { selectedGrade = null },
                modifier = modifier,
            )
        }
    }
}

// ── 메인 페이지 ──────────────────────────────────────────

@Composable
private fun FoodReportMainContent(
    onGradeClick: (String) -> Unit,
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
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "내 음식 성적표",
                color = GlucoachColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "알림",
                tint = GlucoachColors.PrimaryDark,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.height(GlucoachSpacing.xl))

        AIAnalysisCard()

        Spacer(Modifier.height(GlucoachSpacing.xl))

        GradeSummaryRow()

        Spacer(Modifier.height(GlucoachSpacing.xl))

        Text(
            text = "등급별 음식",
            color = GlucoachColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(GlucoachSpacing.md))

        FoodReportMockData.gradeInfoList.forEach { gradeInfo ->
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

// ── 등급 요약 뱃지 행 ──────────────────────────────────────

@Composable
private fun GradeSummaryRow() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm),
    ) {
        FoodReportMockData.gradeInfoList.forEach { info ->
            GradeSummaryBadge(grade = info.grade, count = info.count)
        }
    }
}

@Composable
private fun GradeSummaryBadge(
    grade: String,
    count: Int,
) {
    val color = gradeColor(grade)

    Column(
        modifier =
            Modifier
                .width(56.dp)
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$count",
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${grade}등급",
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
        )
    }
}

// ── 등급 리스트 아이템 ──────────────────────────────────────

@Composable
private fun GradeListItem(
    gradeInfo: FoodGradeInfo,
    onClick: () -> Unit,
) {
    val color = gradeColor(gradeInfo.grade)
    val bgColor = gradeBgColor(gradeInfo.grade)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(bgColor)
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = gradeInfo.grade,
                color = Color.White,
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
