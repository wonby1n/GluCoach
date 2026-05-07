package com.ssafy.s309.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.data.model.FoodGradeInfo
import com.ssafy.s309.data.model.FoodTrend
import com.ssafy.s309.data.model.GradeFoodItem
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

@Composable
internal fun FoodReportDetailContent(
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
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(gradeColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = food.name.take(1),
                color = gradeColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

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
