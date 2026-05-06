package com.ssafy.s309.ui.screen.main

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.R
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.model.MealCreateRequest
import com.ssafy.s309.data.model.MealRecordResponse
import com.ssafy.s309.data.repository.FoodRepository
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import com.ssafy.s309.ui.viewmodel.FoodSearchViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import javax.inject.Inject

// ── 데이터 모델 ──────────────────────────────────────────

data class MealRecord(
    val id: Int,
    val year: Int,
    val month: Int,
    val day: Int,
    val mealType: MealType,
    val name: String,
    val description: String,
    @DrawableRes val imageResId: Int? = null,
    val calories: Int,
    val carbs: Float,
    val protein: Float,
    val fat: Float,
    val ingredients: List<String> = emptyList(),
)

enum class MealType(val label: String) {
    BREAKFAST("아침"),
    LUNCH("점심"),
    DINNER("저녁"),
    SNACK("간식"),
}

private fun mealTypeDisplayName(type: MealType): String =
    when (type) {
        MealType.SNACK -> type.label
        else -> "${type.label} 식사"
    }

// ── 목 데이터 ──────────────────────────────────────────

private object MealLogMockData {
    val meals =
        listOf(
            MealRecord(
                id = 1,
                year = 2026,
                month = 4,
                day = 28,
                mealType = MealType.BREAKFAST,
                name = "연어샐러드",
                description =
                    "신선한 연어와 다양한 채소로 구성된 샐러드입니다. " +
                        "오메가-3 지방산이 풍부하며, 단백질과 비타민이 균형 잡힌 건강한 한 끼입니다.",
                imageResId = R.drawable.salmon_salad,
                calories = 192,
                carbs = 11.62f,
                protein = 12.21f,
                fat = 10.6f,
                ingredients = listOf("연어", "양상추", "토마토", "아보카도", "올리브오일"),
            ),
            MealRecord(
                id = 2,
                year = 2026,
                month = 4,
                day = 28,
                mealType = MealType.SNACK,
                name = "초콜릿 2조각, 아이스아메리카노 1잔",
                description =
                    "다크초콜릿 2조각과 아이스 아메리카노 한 잔으로 구성된 간식입니다. " +
                        "카페인과 함께 적당량의 당분을 섭취할 수 있습니다.",
                calories = 145,
                carbs = 18.5f,
                protein = 2.1f,
                fat = 7.3f,
                ingredients = listOf("다크초콜릿", "아메리카노"),
            ),
            MealRecord(
                id = 3,
                year = 2026,
                month = 4,
                day = 27,
                mealType = MealType.LUNCH,
                name = "고등어구이 정식",
                description =
                    "잘 구워진 고등어와 밥, 된장찌개, 반찬으로 구성된 정식입니다. " +
                        "DHA와 EPA가 풍부한 건강한 점심 식사입니다.",
                imageResId = R.drawable.grilled_mackerel,
                calories = 450,
                carbs = 52.3f,
                protein = 28.4f,
                fat = 15.2f,
                ingredients = listOf("고등어", "쌀밥", "된장찌개", "김치", "나물"),
            ),
            MealRecord(
                id = 4,
                year = 2026,
                month = 4,
                day = 27,
                mealType = MealType.DINNER,
                name = "키토김밥",
                description =
                    "밥 없이 채소와 단백질 재료로 만든 저탄수화물 김밥입니다. " +
                        "혈당 관리에 도움이 되는 식사입니다.",
                imageResId = R.drawable.keto_kimbap,
                calories = 280,
                carbs = 8.5f,
                protein = 22.0f,
                fat = 18.3f,
                ingredients = listOf("김", "달걀", "햄", "오이", "당근"),
            ),
            MealRecord(
                id = 5,
                year = 2026,
                month = 4,
                day = 25,
                mealType = MealType.BREAKFAST,
                name = "짜장면",
                description =
                    "중화풍 춘장 소스에 면을 비벼먹는 대표적인 한국식 중식입니다. " +
                        "탄수화물이 높아 혈당 관리 시 주의가 필요합니다.",
                imageResId = R.drawable.jjajangmyeon,
                calories = 580,
                carbs = 78.2f,
                protein = 15.6f,
                fat = 22.1f,
                ingredients = listOf("면", "춘장", "돼지고기", "양파", "감자"),
            ),
            MealRecord(
                id = 6,
                year = 2026,
                month = 4,
                day = 23,
                mealType = MealType.LUNCH,
                name = "짬뽕",
                description =
                    "매콤한 해물 국물에 면을 넣은 한국식 중식입니다. " +
                        "다양한 해산물과 채소가 들어가 영양이 풍부합니다.",
                imageResId = R.drawable.jjambbong,
                calories = 520,
                carbs = 65.0f,
                protein = 22.8f,
                fat = 18.5f,
                ingredients = listOf("면", "오징어", "새우", "홍합", "양배추"),
            ),
        )

    fun mealsForDate(
        year: Int,
        month: Int,
        day: Int,
    ): List<MealRecord> = meals.filter { it.year == year && it.month == month && it.day == day }

    fun daysWithMeals(
        year: Int,
        month: Int,
    ): Set<Int> = meals.filter { it.year == year && it.month == month }.map { it.day }.toSet()
}

// ── ViewModel ──────────────────────────────────────────

@HiltViewModel
class MealLogViewModel
    @Inject
    constructor(
        private val healthRepository: HealthRepository,
        private val foodRepository: FoodRepository,
    ) : ViewModel() {
        private val _beMeals = MutableStateFlow<List<MealRecordResponse>>(emptyList())
        val beMeals: StateFlow<List<MealRecordResponse>> = _beMeals.asStateFlow()

        private val _foodNutritionMap = MutableStateFlow<Map<Int, FoodSearchItem>>(emptyMap())
        val foodNutritionMap: StateFlow<Map<Int, FoodSearchItem>> = _foodNutritionMap.asStateFlow()

        private val _beDaysWithMeals = MutableStateFlow<Set<Int>>(emptySet())
        val beDaysWithMeals: StateFlow<Set<Int>> = _beDaysWithMeals.asStateFlow()

        private var loadedMonth: Pair<Int, Int>? = null

        fun loadMeals(date: String) {
            viewModelScope.launch {
                healthRepository.getMealsByDate(date)
                    .onSuccess { meals ->
                        _beMeals.value = meals
                        if (meals.isNotEmpty()) {
                            val day = LocalDate.parse(date).dayOfMonth
                            _beDaysWithMeals.value = _beDaysWithMeals.value + day
                        }
                        meals.forEach { meal ->
                            val foodId = meal.foodId ?: return@forEach
                            if (foodId in _foodNutritionMap.value) return@forEach
                            val name = meal.foodName ?: return@forEach
                            launch { lookupFoodNutrition(foodId, name) }
                        }
                    }
                    .onFailure { _beMeals.value = emptyList() }
            }
        }

        private suspend fun lookupFoodNutrition(
            foodId: Int,
            foodName: String,
        ) {
            foodRepository.searchFoods(foodName)
                .onSuccess { results ->
                    val match = results.firstOrNull { it.id == foodId }
                    if (match != null) {
                        _foodNutritionMap.value = _foodNutritionMap.value + (foodId to match)
                    }
                }
        }

        fun onMonthChanged(
            year: Int,
            month: Int,
        ) {
            if (loadedMonth == year to month) return
            loadedMonth = year to month
            _beDaysWithMeals.value = emptySet()
        }

        fun createMeal(
            foodId: Int,
            recordedAt: String,
            onSuccess: () -> Unit,
        ) {
            viewModelScope.launch {
                healthRepository.createMealRecord(
                    MealCreateRequest(foodId = foodId, recordedAt = recordedAt),
                ).onSuccess {
                    onSuccess()
                }.onFailure {
                    Log.w("MealLogVM", "식사 기록 생성 실패", it)
                }
            }
        }
    }

// ── 진입점 ──────────────────────────────────────────────

@Composable
fun MealLogContent(
    onBackToHome: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val mealLogViewModel: MealLogViewModel = hiltViewModel()
    val foodSearchViewModel: FoodSearchViewModel = hiltViewModel()
    var selectedMeal by remember { mutableStateOf<MealRecord?>(null) }
    var displayedMeal by remember { mutableStateOf<MealRecord?>(null) }
    if (selectedMeal != null) displayedMeal = selectedMeal

    if (selectedMeal != null) {
        BackHandler { selectedMeal = null }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MealLogCalendarContent(
            mealLogViewModel = mealLogViewModel,
            foodSearchViewModel = foodSearchViewModel,
            onMealClick = { selectedMeal = it },
            onBack = onBackToHome,
        )

        AnimatedVisibility(
            visible = selectedMeal != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            displayedMeal?.let { meal ->
                MealDetailContent(
                    meal = meal,
                    onBack = { selectedMeal = null },
                )
            }
        }
    }
}

// ── 캘린더 메인 ──────────────────────────────────────────

@Composable
private fun MealLogCalendarContent(
    mealLogViewModel: MealLogViewModel,
    foodSearchViewModel: FoodSearchViewModel,
    onMealClick: (MealRecord) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today =
        remember {
            val cal = Calendar.getInstance()
            Triple(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
        }

    var displayYear by remember { mutableIntStateOf(today.first) }
    var displayMonth by remember { mutableIntStateOf(today.second) }
    var selectedDay by remember { mutableIntStateOf(today.third) }
    var showSearchDialog by remember { mutableStateOf(false) }
    val recentKeywords = remember { mutableStateListOf<String>() }

    val beMeals by mealLogViewModel.beMeals.collectAsState()
    val beDaysWithMeals by mealLogViewModel.beDaysWithMeals.collectAsState()
    val nutritionMap by mealLogViewModel.foodNutritionMap.collectAsState()

    LaunchedEffect(displayYear, displayMonth) {
        mealLogViewModel.onMonthChanged(displayYear, displayMonth)
    }

    LaunchedEffect(displayYear, displayMonth, selectedDay) {
        if (selectedDay > 0) {
            val date =
                LocalDate.of(displayYear, displayMonth, selectedDay)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            mealLogViewModel.loadMeals(date)
        }
    }

    val mockMeals =
        remember(displayYear, displayMonth, selectedDay) {
            MealLogMockData.mealsForDate(displayYear, displayMonth, selectedDay)
        }
    val beConvertedMeals =
        remember(beMeals, displayYear, displayMonth, selectedDay, nutritionMap) {
            beMeals.map { m ->
                val food = m.foodId?.let { nutritionMap[it] }
                MealRecord(
                    id = m.mealId,
                    year = displayYear,
                    month = displayMonth,
                    day = selectedDay,
                    mealType = guessMealType(m.recordedAt),
                    name = m.foodName ?: "식사 기록",
                    description = m.memo ?: "",
                    calories = food?.kcal?.toInt() ?: 0,
                    carbs = food?.carbsG?.toFloat() ?: 0f,
                    protein = food?.proteinG?.toFloat() ?: 0f,
                    fat = food?.fatG?.toFloat() ?: 0f,
                )
            }
        }
    val selectedDateMeals = if (beConvertedMeals.isNotEmpty()) beConvertedMeals else mockMeals

    val daysWithMeals =
        remember(displayYear, displayMonth, beDaysWithMeals) {
            MealLogMockData.daysWithMeals(displayYear, displayMonth) + beDaysWithMeals
        }

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
        ) {
            Row(
                modifier =
                    Modifier
                        .weight(1f)
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
                    text = "식사 캘린더",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            IconButton(
                onClick = { showSearchDialog = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "식사 기록 추가",
                    tint = GlucoachColors.Primary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(Modifier.height(GlucoachSpacing.xl))

        if (showSearchDialog) {
            FoodSearchDialog(
                foodSearchViewModel = foodSearchViewModel,
                recentKeywords = recentKeywords,
                onFoodSelected = { food ->
                    showSearchDialog = false
                    val day = if (selectedDay > 0) selectedDay else today.third
                    if (selectedDay <= 0) selectedDay = today.third
                    val recordedAt =
                        LocalDateTime.of(
                            displayYear,
                            displayMonth,
                            day,
                            LocalDateTime.now().hour,
                            LocalDateTime.now().minute,
                        )
                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    mealLogViewModel.createMeal(
                        foodId = food.id,
                        recordedAt = recordedAt,
                        onSuccess = {
                            val date =
                                LocalDate.of(displayYear, displayMonth, day)
                                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            mealLogViewModel.loadMeals(date)
                        },
                    )
                    foodSearchViewModel.clearSearch()
                },
                onDismiss = {
                    foodSearchViewModel.clearSearch()
                    showSearchDialog = false
                },
            )
        }

        MonthCalendar(
            year = displayYear,
            month = displayMonth,
            selectedDay = selectedDay,
            todayYear = today.first,
            todayMonth = today.second,
            todayDay = today.third,
            daysWithMeals = daysWithMeals,
            onDayClick = { selectedDay = it },
            onPrevMonth = {
                if (displayMonth == 1) {
                    displayMonth = 12
                    displayYear--
                } else {
                    displayMonth--
                }
                selectedDay = 0
            },
            onNextMonth = {
                if (displayMonth == 12) {
                    displayMonth = 1
                    displayYear++
                } else {
                    displayMonth++
                }
                selectedDay = 0
            },
        )

        Spacer(Modifier.height(GlucoachSpacing.lg))

        if (selectedDateMeals.isEmpty()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GlucoachCorner.card))
                        .background(GlucoachColors.Primary.copy(alpha = 0.08f))
                        .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GlucoachColors.Primary),
                )
                Spacer(Modifier.width(GlucoachSpacing.sm))
                Text(
                    text = "식사 기록이 있는 날",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
        } else {
            MealListSection(
                year = displayYear,
                month = displayMonth,
                day = selectedDay,
                meals = selectedDateMeals,
                onMealClick = onMealClick,
            )
        }

        Spacer(Modifier.height(80.dp))
    }
}

// ── 캘린더 위젯 ──────────────────────────────────────────

@Composable
private fun MonthCalendar(
    year: Int,
    month: Int,
    selectedDay: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    daysWithMeals: Set<Int>,
    onDayClick: (Int) -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val calendarInfo =
        remember(year, month) {
            val cal = Calendar.getInstance()
            cal.set(year, month - 1, 1)
            val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            Pair(daysInMonth, firstDayOfWeek)
        }
    val daysInMonth = calendarInfo.first
    val firstDayOfWeek = calendarInfo.second
    val isCurrentMonth = year == todayYear && month == todayMonth

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevMonth, modifier = Modifier.size(32.dp)) {
                Text(
                    text = "<",
                    color = GlucoachColors.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "${year}년 ${month}월",
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                Text(
                    text = ">",
                    color = GlucoachColors.Primary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(GlucoachSpacing.lg))

        val dayOfWeekLabels = listOf("일", "월", "화", "수", "목", "금", "토")
        Row(modifier = Modifier.fillMaxWidth()) {
            dayOfWeekLabels.forEachIndexed { idx, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color =
                        when (idx) {
                            0 -> Color(0xFFE57373)
                            6 -> GlucoachColors.Primary
                            else -> GlucoachColors.TextSecondary
                        },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(GlucoachSpacing.md))

        val totalCells = firstDayOfWeek - 1 + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp),
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - (firstDayOfWeek - 1) + 1

                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val isToday = isCurrentMonth && dayNum == todayDay
                            val isSelected = dayNum == selectedDay && !isToday
                            val hasMeal = dayNum in daysWithMeals

                            Column(
                                modifier =
                                    Modifier
                                        .clickable { onDayClick(dayNum) }
                                        .fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(32.dp)
                                            .then(
                                                when {
                                                    isToday ->
                                                        Modifier
                                                            .clip(CircleShape)
                                                            .background(GlucoachColors.Primary)
                                                    isSelected ->
                                                        Modifier.border(
                                                            1.5.dp,
                                                            GlucoachColors.Primary,
                                                            CircleShape,
                                                        )
                                                    else -> Modifier
                                                },
                                            ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color =
                                            if (isToday) Color.White else GlucoachColors.TextPrimary,
                                        fontSize = 14.sp,
                                    )
                                }
                                if (hasMeal && !isToday) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(GlucoachColors.Primary),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── 식사 목록 ──────────────────────────────────────────

@Composable
private fun MealListSection(
    year: Int,
    month: Int,
    day: Int,
    meals: List<MealRecord>,
    onMealClick: (MealRecord) -> Unit,
) {
    val mealTypesPresent = meals.map { it.mealType }.toSet()

    Column {
        Text(
            text = "$year",
            color = GlucoachColors.TextSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = "${month}월 ${day}일",
            color = GlucoachColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(GlucoachSpacing.md))

        Row(horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm)) {
            MealType.entries.forEach { type ->
                val hasRecord = type in mealTypesPresent
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                                if (hasRecord) {
                                    Modifier.background(GlucoachColors.Primary)
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        GlucoachColors.Border,
                                        RoundedCornerShape(20.dp),
                                    )
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = type.label,
                        color = if (hasRecord) Color.White else GlucoachColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(GlucoachSpacing.lg))

        meals.forEach { meal ->
            MealCard(meal = meal, onClick = { onMealClick(meal) })
            Spacer(Modifier.height(GlucoachSpacing.sm))
        }
    }
}

@Composable
private fun MealCard(
    meal: MealRecord,
    onClick: () -> Unit,
) {
    val icon =
        when (meal.mealType) {
            MealType.BREAKFAST -> Icons.Outlined.WbSunny
            MealType.LUNCH -> Icons.Outlined.LightMode
            MealType.DINNER -> Icons.Outlined.DarkMode
            MealType.SNACK -> Icons.Outlined.LocalCafe
        }
    val bgColor =
        when (meal.mealType) {
            MealType.BREAKFAST -> Color(0xFFFFF8E1)
            MealType.LUNCH -> Color(0xFFF1F8E9)
            MealType.DINNER -> Color(0xFFE8EAF6)
            MealType.SNACK -> Color(0xFFFCE4EC)
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(bgColor)
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GlucoachColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(GlucoachSpacing.md))
        Column {
            Text(
                text = mealTypeDisplayName(meal.mealType),
                color = GlucoachColors.TextSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = meal.name,
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── 식사 상세 ──────────────────────────────────────────

@Composable
private fun MealDetailContent(
    meal: MealRecord,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(GlucoachColors.Background)
                .verticalScroll(rememberScrollState()),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(top = GlucoachSpacing.xxl, bottom = GlucoachSpacing.lg),
        ) {
            Row(
                modifier = Modifier.clickable(onClick = onBack),
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
                    text = "식사 캘린더",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "${meal.year}",
                        color = GlucoachColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = mealTypeDisplayName(meal.mealType),
                        color = GlucoachColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = GlucoachColors.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(GlucoachSpacing.xl))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (meal.imageResId != null) {
                    Image(
                        painter = painterResource(id = meal.imageResId),
                        contentDescription = meal.name,
                        modifier =
                            Modifier
                                .size(180.dp)
                                .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .size(180.dp)
                                .clip(CircleShape)
                                .background(GlucoachColors.Border.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Restaurant,
                            contentDescription = null,
                            tint = GlucoachColors.TextSecondary,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(GlucoachSpacing.xl))

            NutritionSummaryBar(
                calories = meal.calories,
                carbs = meal.carbs,
                protein = meal.protein,
                fat = meal.fat,
            )

            Spacer(Modifier.height(GlucoachSpacing.xxl))

            Text(
                text = "기록 상세",
                color = GlucoachColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(GlucoachSpacing.md))
            Text(
                text = meal.description,
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            )

            if (meal.ingredients.isNotEmpty()) {
                Spacer(Modifier.height(GlucoachSpacing.xxl))

                Text(
                    text = "Ingredients",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(GlucoachSpacing.md))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
                ) {
                    val ingredientColors =
                        listOf(
                            Color(0xFFFFF3E0),
                            Color(0xFFFFEBEE),
                            Color(0xFFE8F5E9),
                            Color(0xFFE3F2FD),
                            Color(0xFFF3E5F5),
                        )
                    meal.ingredients.take(4).forEachIndexed { idx, ingredient ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(ingredientColors[idx % ingredientColors.size])
                                        .border(1.dp, GlucoachColors.Border, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = ingredient.first().toString(),
                                    color = GlucoachColors.TextPrimary,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = ingredient,
                                color = GlucoachColors.TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    if (meal.ingredients.size > 4) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, GlucoachColors.Border, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "View\nAll",
                                    color = GlucoachColors.Primary,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── 영양정보 바 ──────────────────────────────────────────

@Composable
private fun NutritionSummaryBar(
    calories: Int,
    carbs: Float,
    protein: Float,
    fat: Float,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFFE0B2).copy(alpha = 0.5f))
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.md),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NutritionItem("열량", "${calories}kcal")
        NutritionItem("탄수화물", "${carbs}g")
        NutritionItem("단백질", "${protein}g")
        NutritionItem("지방", "${fat}g")
    }
}

@Composable
private fun NutritionItem(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            color = Color(0xFFE65100),
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = Color(0xFFE65100),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun guessMealType(isoDateTime: String): MealType =
    try {
        val hour = LocalDateTime.parse(isoDateTime).hour
        when {
            hour < 10 -> MealType.BREAKFAST
            hour < 14 -> MealType.LUNCH
            hour < 18 -> MealType.DINNER
            else -> MealType.SNACK
        }
    } catch (e: Exception) {
        MealType.LUNCH
    }
