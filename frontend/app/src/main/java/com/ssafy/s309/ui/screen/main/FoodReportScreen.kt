package com.ssafy.s309.ui.screen.main

import androidx.annotation.DrawableRes
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

// ── 데이터 ──────────────────────────────────────────────

private data class FoodGradeInfo(
    val grade: String,
    val title: String,
    val glucoseRange: String,
    val description: String,
    val count: Int,
)

private data class GradeFoodItem(
    val name: String,
    @DrawableRes val imageResId: Int,
    val frequency: Int,
    val lastEaten: String,
    val glucoseRise: Int,
    val trend: FoodTrend,
    val measureCount: Int,
)

private enum class FoodTrend { UP, DOWN, STABLE }

// ── 등급 색상 ──────────────────────────────────────────────

private fun gradeColor(grade: String): Color =
    when (grade) {
        "S" -> GlucoachColors.GradeS
        "A" -> GlucoachColors.GradeA
        "B" -> GlucoachColors.GradeB
        "C" -> GlucoachColors.GradeC
        "D" -> GlucoachColors.GradeD
        "F" -> GlucoachColors.GradeF
        else -> Color(0xFF9E9E9E)
    }

private fun gradeBgColor(grade: String): Color =
    when (grade) {
        "S" -> GlucoachColors.GradeSBg
        "A" -> GlucoachColors.GradeABg
        "B" -> GlucoachColors.GradeBBg
        "C" -> GlucoachColors.GradeCBg
        "D" -> GlucoachColors.GradeDBg
        "F" -> GlucoachColors.GradeFBg
        else -> Color(0xFFF5F5F5)
    }

// ── 목 데이터 ──────────────────────────────────────────────

private val gradeInfoList =
    listOf(
        FoodGradeInfo(
            "S",
            "내 몸에 딱 맞아요",
            "혈당 상승 0~20 mg/dL",
            "이 음식들은 혈당이 거의 안 오를 만큼 잘 맞아요.\n자주 드시면 혈당 관리에 최고예요!",
            12,
        ),
        FoodGradeInfo(
            "A",
            "잘 맞는 편이에요",
            "혈당 상승 20~40 mg/dL",
            "이 음식들은 혈당이 소폭 오르지만 안정적이에요.\n안심하고 드셔도 좋아요!",
            20,
        ),
        FoodGradeInfo(
            "B",
            "가끔은 괜찮아요",
            "혈당 상승 40~60 mg/dL",
            "이 음식들은 혈당이 어느 정도 오르는 편이에요.\n가끔 드시는 건 괜찮지만 양 조절이 필요해요!",
            18,
        ),
        FoodGradeInfo(
            "C",
            "주의가 필요해요",
            "혈당 상승 60~80 mg/dL",
            "이 음식들은 혈당을 꽤 올리는 편이에요.\n드실 때 양을 줄이거나 다른 음식과 함께 드세요!",
            12,
        ),
        FoodGradeInfo(
            "D",
            "가급적 피해주세요",
            "혈당 상승 80~100 mg/dL",
            "이 음식들은 혈당을 많이 올려요.\n가급적 피하시거나 소량만 드세요!",
            5,
        ),
        FoodGradeInfo(
            "F",
            "드시지 않는 게 좋아요",
            "혈당 상승 100+ mg/dL",
            "이 음식들은 혈당을 급격히 올려요.\n건강을 위해 드시지 않는 것을 권장해요!",
            3,
        ),
    )

private val gradeFoodItems =
    mapOf(
        "S" to
            listOf(
                GradeFoodItem("연어 샐러드", R.drawable.salmon_salad, 8, "3일 전", 18, FoodTrend.DOWN, 8),
                GradeFoodItem("달걀프라이", R.drawable.grilled_mackerel, 12, "오늘", 12, FoodTrend.STABLE, 12),
                GradeFoodItem("브로콜리볶음", R.drawable.keto_kimbap, 5, "1주 전", 9, FoodTrend.DOWN, 6),
                GradeFoodItem("고등어구이", R.drawable.grilled_mackerel, 6, "4일 전", 15, FoodTrend.DOWN, 6),
                GradeFoodItem("아보카도", R.drawable.salmon_salad, 3, "2주 전", 11, FoodTrend.STABLE, 3),
            ),
        "A" to
            listOf(
                GradeFoodItem("키토 김밥", R.drawable.keto_kimbap, 10, "2일 전", 25, FoodTrend.DOWN, 10),
                GradeFoodItem("연어구이", R.drawable.salmon_salad, 7, "5일 전", 30, FoodTrend.STABLE, 7),
                GradeFoodItem("두부 샐러드", R.drawable.keto_kimbap, 4, "1주 전", 22, FoodTrend.DOWN, 4),
                GradeFoodItem("닭가슴살", R.drawable.grilled_mackerel, 9, "오늘", 35, FoodTrend.DOWN, 9),
            ),
        "B" to
            listOf(
                GradeFoodItem("현미밥", R.drawable.keto_kimbap, 15, "오늘", 45, FoodTrend.STABLE, 15),
                GradeFoodItem("잡곡밥", R.drawable.keto_kimbap, 12, "1일 전", 50, FoodTrend.UP, 12),
                GradeFoodItem("고구마", R.drawable.salmon_salad, 8, "3일 전", 55, FoodTrend.STABLE, 8),
            ),
        "C" to
            listOf(
                GradeFoodItem("짬뽕", R.drawable.jjambbong, 3, "3일 전", 65, FoodTrend.UP, 3),
                GradeFoodItem("우동", R.drawable.jjajangmyeon, 5, "1주 전", 70, FoodTrend.STABLE, 5),
            ),
        "D" to
            listOf(
                GradeFoodItem("짜장면", R.drawable.jjajangmyeon, 4, "5일 전", 85, FoodTrend.UP, 4),
                GradeFoodItem("떡볶이", R.drawable.keto_kimbap, 2, "2주 전", 95, FoodTrend.UP, 2),
            ),
        "F" to
            listOf(
                GradeFoodItem("탕수육", R.drawable.jjajangmyeon, 1, "3주 전", 110, FoodTrend.UP, 1),
            ),
    )

// ── 진입점 ──────────────────────────────────────────────

@Composable
fun FoodReportContent(modifier: Modifier = Modifier) {
    var selectedGrade by remember { mutableStateOf<String?>(null) }

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
            val gradeInfo = gradeInfoList.first { it.grade == grade }
            val foods = gradeFoodItems[grade] ?: emptyList()
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
        gradeInfoList.forEach { info ->
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

// ── 상세 페이지 ──────────────────────────────────────────

@Composable
private fun FoodReportDetailContent(
    gradeInfo: FoodGradeInfo,
    foods: List<GradeFoodItem>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFilter by remember { mutableIntStateOf(0) }
    val filterLabels = listOf("전체", "혈당순", "섭취순")

    val sortedFoods =
        when (selectedFilter) {
            1 -> foods.sortedBy { it.glucoseRise }
            2 -> foods.sortedByDescending { it.frequency }
            else -> foods
        }

    val color = gradeColor(gradeInfo.grade)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
    ) {
        DetailTopBar(
            title = "${gradeInfo.grade}등급 음식",
            onBack = onBack,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
        ) {
            Spacer(Modifier.height(GlucoachSpacing.lg))

            GradeHeaderCard(gradeInfo = gradeInfo)

            Spacer(Modifier.height(GlucoachSpacing.md))

            GradeDescriptionCard(
                description = gradeInfo.description,
                color = color,
            )

            Spacer(Modifier.height(GlucoachSpacing.xl))

            Row(horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm)) {
                filterLabels.forEachIndexed { index, label ->
                    FilterChip(
                        text = label,
                        isSelected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                    )
                }
            }

            Spacer(Modifier.height(GlucoachSpacing.lg))

            sortedFoods.forEach { food ->
                FoodDetailItem(food = food, gradeColor = color)
                Spacer(Modifier.height(GlucoachSpacing.md))
            }

            Spacer(Modifier.height(GlucoachSpacing.xxl))
        }
    }
}

// ── 상세 상단바 ──────────────────────────────────────────

@Composable
private fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(GlucoachColors.Surface)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = GlucoachColors.PrimaryDark,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.width(GlucoachSpacing.sm))

        Text(
            text = title,
            color = GlucoachColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ── 등급 헤더 카드 ──────────────────────────────────────────

@Composable
private fun GradeHeaderCard(gradeInfo: FoodGradeInfo) {
    val color = gradeColor(gradeInfo.grade)
    val bgColor = gradeBgColor(gradeInfo.grade)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(bgColor)
                .padding(GlucoachSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = gradeInfo.grade,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(GlucoachSpacing.lg))

        Column {
            Text(
                text = gradeInfo.title,
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(GlucoachSpacing.xs))
            Text(
                text = gradeInfo.glucoseRange,
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
            )
        }
    }
}

// ── 등급 설명 카드 ──────────────────────────────────────────

@Composable
private fun GradeDescriptionCard(
    description: String,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = GlucoachSpacing.xl, vertical = GlucoachSpacing.lg),
    ) {
        Text(
            text = description,
            color = GlucoachColors.TextPrimary,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── 필터 칩 ──────────────────────────────────────────────

@Composable
private fun FilterChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .then(
                    if (isSelected) {
                        Modifier.border(1.5.dp, GlucoachColors.TextPrimary, RoundedCornerShape(20.dp))
                    } else {
                        Modifier.border(1.dp, GlucoachColors.Border, RoundedCornerShape(20.dp))
                    },
                )
                .background(Color.White)
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.sm),
    ) {
        Text(
            text = text,
            color = if (isSelected) GlucoachColors.TextPrimary else GlucoachColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

// ── 음식 상세 아이템 ──────────────────────────────────────

@Composable
private fun FoodDetailItem(
    food: GradeFoodItem,
    gradeColor: Color,
) {
    val trendSymbol =
        when (food.trend) {
            FoodTrend.DOWN -> "↓"
            FoodTrend.UP -> "↑"
            FoodTrend.STABLE -> "→"
        }
    val trendColor =
        when (food.trend) {
            FoodTrend.DOWN -> GlucoachColors.GradeS
            FoodTrend.UP -> GlucoachColors.GradeD
            FoodTrend.STABLE -> GlucoachColors.TextSecondary
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = food.imageResId),
            contentDescription = food.name,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )

        Spacer(Modifier.width(GlucoachSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = food.name,
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(GlucoachSpacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(gradeColor),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "${food.frequency}회 · ${food.lastEaten}",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "+${food.glucoseRise}",
                color = gradeColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "mg/dL",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = trendSymbol,
                    color = trendColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "${food.measureCount}회",
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
