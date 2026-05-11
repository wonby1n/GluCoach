package com.ssafy.s309.ui.screen.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
import com.ssafy.s309.data.model.FoodGradeInfo
import com.ssafy.s309.data.model.FoodTrend
import com.ssafy.s309.data.model.GradeFoodItem
import com.ssafy.s309.data.model.MealRecordResponse
import com.ssafy.s309.ui.component.FoodCategoryImageMapper
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import kotlinx.coroutines.launch
import java.time.LocalDateTime

@Composable
internal fun FoodReportDetailContent(
    gradeInfoList: List<FoodGradeInfo>,
    gradeFoodItems: Map<String, List<GradeFoodItem>>,
    initialGrade: String,
    onBack: () -> Unit,
    onFoodClick: (GradeFoodItem, String) -> Unit,
    foodHistoryState: FoodHistoryState,
    onDismissFoodHistory: () -> Unit,
    onNavigateToMealLog: (date: String, mealId: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val grades = gradeInfoList.map { it.grade }
    val initialPage = grades.indexOf(initialGrade).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { grades.size }
    val coroutineScope = rememberCoroutineScope()
    val currentGrade = grades.getOrElse(pagerState.currentPage) { initialGrade }
    val showHistory = foodHistoryState.food != null

    if (showHistory) {
        BackHandler { onDismissFoodHistory() }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(GlucoachColors.Background),
        ) {
            Spacer(Modifier.height(GlucoachSpacing.xxl))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp)
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
                    text = "등급별 기록",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(GlucoachSpacing.lg))

            GradeBadgeRow(
                gradeInfoList = gradeInfoList,
                selectedGrade = currentGrade,
                onGradeClick = { grade ->
                    val idx = grades.indexOf(grade)
                    if (idx >= 0) {
                        coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                    }
                },
                modifier = Modifier.padding(horizontal = 22.dp),
            )

            Spacer(Modifier.height(GlucoachSpacing.lg))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val grade = grades[page]
                val foods = gradeFoodItems[grade] ?: emptyList()
                val color = gradeColor(grade)

                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 22.dp),
                ) {
                    foods.forEach { food ->
                        FoodDetailItem(
                            food = food,
                            gradeColor = color,
                            onClick = { onFoodClick(food, grade) },
                        )
                        Spacer(Modifier.height(GlucoachSpacing.md))
                    }

                    if (foods.isEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "이 등급에 해당하는 음식이 없습니다",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(80.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = showHistory,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { onDismissFoodHistory() },
            )
        }

        AnimatedVisibility(
            visible = showHistory,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            FoodHistorySheet(
                state = foodHistoryState,
                onDismiss = onDismissFoodHistory,
                onDateClick = onNavigateToMealLog,
            )
        }
    }
}

// ── 음식 상세 아이템 ──────────────────────────────────────

@Composable
private fun FoodDetailItem(
    food: GradeFoodItem,
    gradeColor: Color,
    onClick: () -> Unit,
) {
    val trendSymbol =
        when (food.trend) {
            FoodTrend.DOWN -> "↓"
            FoodTrend.UP -> "↑"
            FoodTrend.STABLE -> "→"
        }
    val trendColor =
        when (food.trend) {
            FoodTrend.DOWN -> GlucoachColors.PrimaryDark
            FoodTrend.UP -> com.ssafy.s309.ui.theme.Error
            FoodTrend.STABLE -> GlucoachColors.TextSecondary
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlucoachColors.Surface)
                .clickable(onClick = onClick)
                .padding(GlucoachSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = FoodCategoryImageMapper.getImageRes(null, food.name)),
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

// ── 혈당 히스토리 바텀시트 ──────────────────────────────────

@Composable
private fun FoodHistorySheet(
    state: FoodHistoryState,
    onDismiss: () -> Unit,
    onDateClick: (date: String, mealId: Int) -> Unit,
) {
    val food = state.food ?: return
    val color = gradeColor(state.grade)
    val textOnFlag =
        if (state.grade == "B" || state.grade == "C") GlucoachColors.TextPrimary else Color.White

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(GlucoachColors.Surface)
                .padding(horizontal = 22.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .padding(top = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(GlucoachColors.Border),
            )
        }

        Spacer(Modifier.height(GlucoachSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(40.dp)) {
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
                    text = state.grade,
                    color = textOnFlag,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Spacer(Modifier.width(GlucoachSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "음식 성적표",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 13.sp,
                )
                Text(
                    text = food.name,
                    color = GlucoachColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "닫기",
                    tint = GlucoachColors.TextSecondary,
                )
            }
        }

        Spacer(Modifier.height(GlucoachSpacing.xl))

        Text(
            text = "혈당 히스토리",
            color = GlucoachColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(GlucoachSpacing.lg))

        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = GlucoachColors.Primary, modifier = Modifier.size(32.dp))
            }
        } else if (state.meals.isEmpty()) {
            Text(
                text = "기록이 없습니다",
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                val groupedByMonth = groupMealsByMonth(state.meals)
                groupedByMonth.forEach { (yearMonth, dayGroups) ->
                    Text(
                        text = "${yearMonth.first}",
                        color = GlucoachColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "${yearMonth.second}월",
                        color = GlucoachColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )

                    Spacer(Modifier.height(GlucoachSpacing.md))

                    dayGroups.forEach { (day, meals) ->
                        Text(
                            text = "${day}일",
                            color = GlucoachColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(GlucoachSpacing.sm))

                        meals.forEach { meal ->
                            val mealType = guessMealTypeFromTime(meal.recordedAt)
                            val date = extractDate(meal.recordedAt)
                            HistoryMealCard(
                                mealType = mealType,
                                foodName = meal.foodName ?: food.name,
                                onClick = { onDateClick(date, meal.mealId) },
                            )
                            Spacer(Modifier.height(GlucoachSpacing.sm))
                        }

                        Spacer(Modifier.height(GlucoachSpacing.sm))
                    }

                    Spacer(Modifier.height(GlucoachSpacing.md))
                }

                Spacer(Modifier.height(GlucoachSpacing.xl))
            }
        }
    }
}

@Composable
private fun HistoryMealCard(
    mealType: String,
    foodName: String,
    onClick: () -> Unit,
) {
    val icon =
        when {
            mealType.contains("아침") -> Icons.Outlined.WbSunny
            mealType.contains("점심") -> Icons.Outlined.LightMode
            mealType.contains("저녁") -> Icons.Outlined.DarkMode
            else -> Icons.Outlined.LocalCafe
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.PrimaryLight)
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GlucoachColors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(GlucoachSpacing.md))
        Column {
            Text(
                text = mealType,
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = foodName,
                color = GlucoachColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── 유틸 ──────────────────────────────────────────────

private fun groupMealsByMonth(meals: List<MealRecordResponse>): List<Pair<Pair<Int, Int>, List<Pair<Int, List<MealRecordResponse>>>>> =
    runCatching {
        meals
            .sortedByDescending { it.recordedAt }
            .groupBy { meal ->
                val dt = LocalDateTime.parse(meal.recordedAt)
                dt.year to dt.monthValue
            }
            .map { (yearMonth, monthMeals) ->
                val dayGroups =
                    monthMeals
                        .groupBy { LocalDateTime.parse(it.recordedAt).dayOfMonth }
                        .toSortedMap(compareByDescending { it })
                        .map { (day, dayMeals) -> day to dayMeals }
                yearMonth to dayGroups
            }
    }.getOrDefault(emptyList())

private fun guessMealTypeFromTime(isoDateTime: String): String =
    runCatching {
        val hour = LocalDateTime.parse(isoDateTime).hour
        when {
            hour < 10 -> "아침 식사"
            hour < 14 -> "점심 식사"
            hour < 18 -> "저녁 식사"
            else -> "간식"
        }
    }.getOrDefault("식사")

private fun extractDate(isoDateTime: String): String =
    runCatching {
        LocalDateTime.parse(isoDateTime).toLocalDate().toString()
    }.getOrDefault("")
