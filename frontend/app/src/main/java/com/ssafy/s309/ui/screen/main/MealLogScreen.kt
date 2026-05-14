package com.ssafy.s309.ui.screen.main

import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.model.MealRecordResponse
import com.ssafy.s309.data.repository.FoodRepository
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.data.repository.MealRepository
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import com.ssafy.s309.ui.viewmodel.FoodSearchViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
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
    val imageUrl: String? = null,
    val calories: Int,
    val carbs: Float,
    val protein: Float,
    val fat: Float,
    val ingredients: List<String> = emptyList(),
    val recordedAt: String = "",
    val maxGlucose: Int? = null,
    val glucose1h: Int? = null,
    val glucose2h: Int? = null,
    val grade: String? = null,
)

enum class MealType(val label: String) {
    BREAKFAST("아침"),
    LUNCH("점심"),
    DINNER("저녁"),
    SNACK("간식"),
}

data class GlucoseDetail(
    val max: Int,
    val at1h: Int? = null,
    val at2h: Int? = null,
)

private fun mealTypeDisplayName(type: MealType): String =
    when (type) {
        MealType.SNACK -> type.label
        else -> "${type.label} 식사"
    }

// ── ViewModel ──────────────────────────────────────────

@HiltViewModel
class MealLogViewModel
    @Inject
    constructor(
        private val healthRepository: HealthRepository,
        private val foodRepository: FoodRepository,
        private val mealRepository: MealRepository,
    ) : ViewModel() {
        private val _beMeals = MutableStateFlow<List<MealRecordResponse>>(emptyList())
        val beMeals: StateFlow<List<MealRecordResponse>> = _beMeals.asStateFlow()

        private val _foodNutritionMap = MutableStateFlow<Map<Int, FoodSearchItem>>(emptyMap())
        val foodNutritionMap: StateFlow<Map<Int, FoodSearchItem>> = _foodNutritionMap.asStateFlow()

        private val _beDaysWithMeals = MutableStateFlow<Set<Int>>(emptySet())
        val beDaysWithMeals: StateFlow<Set<Int>> = _beDaysWithMeals.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _maxGlucoseMap = MutableStateFlow<Map<Int, GlucoseDetail>>(emptyMap())
        val maxGlucoseMap: StateFlow<Map<Int, GlucoseDetail>> = _maxGlucoseMap.asStateFlow()

        private val _foodGradeMap = MutableStateFlow<Map<Int, String>>(emptyMap())
        val foodGradeMap: StateFlow<Map<Int, String>> = _foodGradeMap.asStateFlow()

        private var loadedMonth: Pair<Int, Int>? = null

        fun invalidateMonthCache() {
            loadedMonth = null
        }

        private var loadMealsJob: Job? = null
        private var monthScanJob: Job? = null
        private var foodGradeJob: Job? = null

        init {
            loadFoodGrades()
        }

        fun loadMeals(date: String) {
            loadMealsJob?.cancel()
            loadMealsJob =
                viewModelScope.launch {
                    var result = healthRepository.getMealsByDate(date)
                    if (result.isFailure) {
                        delay(500)
                        result = healthRepository.getMealsByDate(date)
                    }
                    result
                        .onSuccess { meals ->
                            _beMeals.value = meals
                            _error.value = null
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
                            meals.forEach { meal ->
                                launch { lookupMaxGlucose(meal.mealId, meal.recordedAt) }
                            }
                            loadFoodGrades()
                        }
                        .onFailure {
                            Log.w("MealLogVM", "식사 조회 실패", it)
                            _beMeals.value = emptyList()
                            _error.value = "식사 기록을 불러올 수 없습니다"
                        }
                }
        }

        private suspend fun lookupMaxGlucose(
            mealId: Int,
            recordedAt: String,
        ) {
            if (mealId in _maxGlucoseMap.value) return
            runCatching {
                val mealTime = LocalDateTime.parse(recordedAt)
                val to = mealTime.plusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                val records = healthRepository.getGlucoseRecords(recordedAt, to)
                if (records.isNotEmpty()) {
                    val maxVal = records.maxOf { it.value.toInt() }
                    val target1h = mealTime.plusHours(1)
                    val target2h = mealTime.plusHours(2)
                    val at1h =
                        records.minByOrNull {
                            kotlin.math.abs(Duration.between(LocalDateTime.parse(it.measuredAt), target1h).toMinutes())
                        }?.value?.toInt()
                    val at2h =
                        records.minByOrNull {
                            kotlin.math.abs(Duration.between(LocalDateTime.parse(it.measuredAt), target2h).toMinutes())
                        }?.value?.toInt()
                    _maxGlucoseMap.value = _maxGlucoseMap.value + (mealId to GlucoseDetail(maxVal, at1h, at2h))
                }
            }.onFailure {
                Log.w("MealLogVM", "혈당 조회 실패 mealId=$mealId", it)
            }
        }

        private fun loadFoodGrades() {
            foodGradeJob?.cancel()
            foodGradeJob =
                viewModelScope.launch {
                    foodRepository.getFoodGrades()
                        .onSuccess { grades ->
                            _foodGradeMap.value = grades.associate { it.foodId to it.grade }
                        }
                }
        }

        fun clearError() {
            _error.value = null
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
            monthScanJob?.cancel()
            monthScanJob =
                viewModelScope.launch {
                    val yearMonth = YearMonth.of(year, month)
                    val days = yearMonth.lengthOfMonth()
                    val found = mutableSetOf<Int>()
                    for (chunk in (1..days).chunked(5)) {
                        chunk.map { day ->
                            async {
                                val date = yearMonth.atDay(day).format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val meals = runCatching { healthRepository.getMealsByDate(date).getOrNull() }.getOrNull()
                                if (!meals.isNullOrEmpty()) day else null
                            }
                        }.awaitAll().filterNotNull().let { found.addAll(it) }
                        _beDaysWithMeals.value = found.toSet()
                    }
                }
        }

        fun createMeal(
            foodId: Int,
            recordedAt: String,
            memo: String? = null,
            imageFile: java.io.File? = null,
            onSuccess: () -> Unit,
        ) {
            viewModelScope.launch {
                mealRepository.createMeal(
                    foodId = foodId,
                    recordedAt = LocalDateTime.parse(recordedAt),
                    photoFile = imageFile,
                    memo = memo,
                ).onSuccess {
                    _error.value = null
                    onSuccess()
                }.onFailure { e ->
                    val body = (e as? retrofit2.HttpException)?.response()?.errorBody()?.string()
                    Log.w("MealLogVM", "식사 기록 생성 실패 code=${(e as? retrofit2.HttpException)?.code()} body=$body", e)
                    _error.value = "식사 기록 저장에 실패했습니다"
                }
            }
        }
    }

// ── 진입점 ──────────────────────────────────────────────

@Composable
fun MealLogContent(
    onBackToHome: () -> Unit = {},
    onNavigateToFoodReport: () -> Unit = {},
    initialDate: String? = null,
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
            initialDate = initialDate,
        )

        AnimatedVisibility(
            visible = selectedMeal != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { selectedMeal = null },
            )
        }

        AnimatedVisibility(
            visible = selectedMeal != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
        ) {
            displayedMeal?.let { meal ->
                MealDetailContent(
                    meal = meal,
                    onBack = { selectedMeal = null },
                    onNavigateToFoodReport = {
                        selectedMeal = null
                        onNavigateToFoodReport()
                    },
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
    initialDate: String? = null,
    modifier: Modifier = Modifier,
) {
    val parsedInit =
        remember(initialDate) {
            initialDate?.let {
                runCatching { java.time.LocalDate.parse(it) }.getOrNull()
            }
        }

    val today =
        remember {
            val cal = Calendar.getInstance()
            Triple(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
        }

    var displayYear by remember { mutableIntStateOf(parsedInit?.year ?: today.first) }
    var displayMonth by remember { mutableIntStateOf(parsedInit?.monthValue ?: today.second) }
    var selectedYear by remember { mutableIntStateOf(parsedInit?.year ?: today.first) }
    var selectedMonth by remember { mutableIntStateOf(parsedInit?.monthValue ?: today.second) }
    var selectedDay by remember { mutableIntStateOf(parsedInit?.dayOfMonth ?: today.third) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showMemoDialog by remember { mutableStateOf(false) }
    var pendingFood by remember { mutableStateOf<FoodSearchItem?>(null) }
    var memoInput by remember { mutableStateOf("") }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri -> if (uri != null) selectedPhotoUri = uri }
    val beMeals by mealLogViewModel.beMeals.collectAsState()
    val beDaysWithMeals by mealLogViewModel.beDaysWithMeals.collectAsState()
    val nutritionMap by mealLogViewModel.foodNutritionMap.collectAsState()
    val errorMessage by mealLogViewModel.error.collectAsState()

    LaunchedEffect(displayYear, displayMonth) {
        mealLogViewModel.onMonthChanged(displayYear, displayMonth)
    }

    LaunchedEffect(selectedYear, selectedMonth, selectedDay) {
        if (selectedDay > 0) {
            val date =
                LocalDate.of(selectedYear, selectedMonth, selectedDay)
                    .format(DateTimeFormatter.ISO_LOCAL_DATE)
            mealLogViewModel.loadMeals(date)
        }
    }

    LaunchedEffect(initialDate) {
        if (initialDate != null) {
            val parsed = runCatching { LocalDate.parse(initialDate) }.getOrNull() ?: return@LaunchedEffect
            mealLogViewModel.invalidateMonthCache()
            mealLogViewModel.onMonthChanged(parsed.year, parsed.monthValue)
            mealLogViewModel.loadMeals(initialDate)
        }
    }

    val glucoseMap by mealLogViewModel.maxGlucoseMap.collectAsState()
    val foodGradeMap by mealLogViewModel.foodGradeMap.collectAsState()

    val selectedDateMeals =
        remember(beMeals, selectedYear, selectedMonth, selectedDay, nutritionMap, glucoseMap, foodGradeMap) {
            beMeals.map { m ->
                val food = m.foodId?.let { nutritionMap[it] }
                MealRecord(
                    id = m.mealId,
                    year = selectedYear,
                    month = selectedMonth,
                    day = selectedDay,
                    mealType = guessMealType(m.recordedAt),
                    name = m.foodDisplayName ?: m.foodName ?: "식사 기록",
                    description = m.memo ?: "",
                    calories = food?.kcal?.toInt() ?: 0,
                    carbs = food?.carbsG?.toFloat() ?: 0f,
                    protein = food?.proteinG?.toFloat() ?: 0f,
                    fat = food?.fatG?.toFloat() ?: 0f,
                    imageUrl = m.imageUrl,
                    recordedAt = m.recordedAt,
                    maxGlucose = glucoseMap[m.mealId]?.max,
                    glucose1h = glucoseMap[m.mealId]?.at1h,
                    glucose2h = glucoseMap[m.mealId]?.at2h,
                    grade = m.foodId?.let { foodGradeMap[it] },
                )
            }
        }

    val daysWithMeals = beDaysWithMeals

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
                onFoodSelected = { food ->
                    showSearchDialog = false
                    pendingFood = food
                    memoInput = ""
                    selectedPhotoUri = null
                    showMemoDialog = true
                    foodSearchViewModel.clearSearch()
                },
                onDismiss = {
                    foodSearchViewModel.clearSearch()
                    showSearchDialog = false
                },
            )
        }

        if (showMemoDialog && pendingFood != null) {
            val initialDay = if (selectedDay > 0) selectedDay else today.third
            val initialYear = if (selectedDay > 0) selectedYear else today.first
            val initialMonth = if (selectedDay > 0) selectedMonth else today.second
            MemoInputDialog(
                memo = memoInput,
                onMemoChange = { memoInput = it },
                photoUri = selectedPhotoUri,
                onPickPhoto = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onRemovePhoto = { selectedPhotoUri = null },
                initialDateTime =
                    LocalDateTime.of(
                        initialYear,
                        initialMonth,
                        initialDay,
                        LocalDateTime.now().hour,
                        LocalDateTime.now().minute,
                    ),
                onConfirm = { dateTime ->
                    showMemoDialog = false
                    val food = pendingFood ?: return@MemoInputDialog
                    if (selectedDay <= 0) selectedDay = today.third
                    val recordedAt = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    val imageFile =
                        selectedPhotoUri?.let { uri ->
                            runCatching {
                                val file = java.io.File(context.cacheDir, "meal_${System.currentTimeMillis()}.jpg")
                                val input = context.contentResolver.openInputStream(uri) ?: return@runCatching null
                                val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                                input.close()
                                if (bitmap == null) return@runCatching null
                                val maxDim = 1024
                                val scaled =
                                    if (bitmap.width > maxDim || bitmap.height > maxDim) {
                                        val s = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
                                        android.graphics.Bitmap.createScaledBitmap(
                                            bitmap,
                                            (bitmap.width * s).toInt(),
                                            (bitmap.height * s).toInt(),
                                            true,
                                        )
                                    } else {
                                        bitmap
                                    }
                                file.outputStream().use { out ->
                                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                                }
                                if (scaled !== bitmap) scaled.recycle()
                                bitmap.recycle()
                                file.takeIf { it.exists() && it.length() > 0 }
                            }.getOrNull()
                        }
                    mealLogViewModel.createMeal(
                        foodId = food.id,
                        recordedAt = recordedAt,
                        memo = memoInput,
                        imageFile = imageFile,
                        onSuccess = {
                            val date = dateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                            mealLogViewModel.loadMeals(date)
                        },
                    )
                    pendingFood = null
                    selectedPhotoUri = null
                },
                onDismiss = {
                    showMemoDialog = false
                    selectedPhotoUri = null
                    pendingFood = null
                },
            )
        }

        MonthCalendar(
            year = displayYear,
            month = displayMonth,
            selectedDay = if (displayYear == selectedYear && displayMonth == selectedMonth) selectedDay else 0,
            todayYear = today.first,
            todayMonth = today.second,
            todayDay = today.third,
            daysWithMeals = daysWithMeals,
            onDayClick = {
                selectedYear = displayYear
                selectedMonth = displayMonth
                selectedDay = it
            },
            onPrevMonth = {
                if (displayMonth == 1) {
                    displayMonth = 12
                    displayYear--
                } else {
                    displayMonth--
                }
            },
            onNextMonth = {
                if (displayMonth == 12) {
                    displayMonth = 1
                    displayYear++
                } else {
                    displayMonth++
                }
            },
        )

        Spacer(Modifier.height(GlucoachSpacing.lg))

        if (errorMessage != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(GlucoachCorner.card))
                        .background(Color(0xFFFFEBEE))
                        .clickable { mealLogViewModel.clearError() }
                        .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = Color(0xFFD32F2F),
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(GlucoachSpacing.sm))
        }

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
                Text(
                    text = if (selectedDay > 0) "이 날의 식사 기록이 없습니다" else "날짜를 선택해주세요",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
        } else {
            MealListSection(
                year = selectedYear,
                month = selectedMonth,
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
    val context = LocalContext.current
    LaunchedEffect(meal.imageUrl) {
        meal.imageUrl?.let { url ->
            SingletonImageLoader.get(context).enqueue(
                ImageRequest.Builder(context).data(url).build(),
            )
        }
    }

    val icon =
        when (meal.mealType) {
            MealType.BREAKFAST -> Icons.Outlined.WbSunny
            MealType.LUNCH -> Icons.Outlined.LightMode
            MealType.DINNER -> Icons.Outlined.DarkMode
            MealType.SNACK -> Icons.Outlined.LocalCafe
        }
    val bgColor =
        when (meal.mealType) {
            MealType.BREAKFAST -> Color(0xFFF0FAFB)
            MealType.LUNCH -> GlucoachColors.PrimaryLight
            MealType.DINNER -> Color(0xFFD4EEF5)
            MealType.SNACK -> Color(0xFFE8F7FA)
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
    onNavigateToFoodReport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPhotoViewer by remember { mutableStateOf(false) }
    val tabs = listOf("메모", "혈당 기록", "영양정보")
    val hasImage = meal.imageUrl != null || meal.imageResId != null
    val cardFraction = 0.50f

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Transparent),
    ) {
        if (meal.imageUrl != null) {
            AsyncImage(
                model = meal.imageUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(1f - cardFraction + 0.05f)
                        .align(Alignment.TopCenter)
                        .clickable { showPhotoViewer = true },
                contentScale = ContentScale.Crop,
            )
        } else if (meal.imageResId != null) {
            Image(
                painter = painterResource(id = meal.imageResId),
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(1f - cardFraction + 0.05f)
                        .align(Alignment.TopCenter)
                        .clickable { showPhotoViewer = true },
                contentScale = ContentScale.Crop,
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(cardFraction)
                    .align(Alignment.BottomCenter)
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

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatMealDateTimeLabel(meal),
                        color = GlucoachColors.TextSecondary,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = meal.name,
                            modifier = Modifier.weight(1f, fill = false),
                            color = GlucoachColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        FoodGradeBadge(grade = meal.grade)
                    }
                }
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = GlucoachColors.TextSecondary,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, title ->
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clickable { selectedTab = index }
                                .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = title,
                            color = if (selectedTab == index) GlucoachColors.TextPrimary else GlucoachColors.TextSecondary,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier =
                                Modifier
                                    .width(if (selectedTab == index) 48.dp else 0.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(if (selectedTab == index) GlucoachColors.Primary else Color.Transparent),
                        )
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(GlucoachColors.Border.copy(alpha = 0.3f)),
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
            ) {
                when (selectedTab) {
                    0 -> MemoTabContent(meal.description)
                    1 -> GlucoseTabContent(meal)
                    2 -> NutritionTabContent(meal)
                }
            }

            val isPending =
                meal.grade == null &&
                    runCatching {
                        val elapsed =
                            java.time.Duration.between(
                                LocalDateTime.parse(meal.recordedAt),
                                LocalDateTime.now(),
                            )
                        elapsed.toHours() < 2
                    }.getOrDefault(false)

            if (isPending) {
                androidx.compose.material3.Button(
                    onClick = {},
                    enabled = false,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            disabledContainerColor = GlucoachColors.Border,
                            disabledContentColor = Color.White,
                        ),
                ) {
                    Text("지금은 성적표 생성 중이에요", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                androidx.compose.material3.Button(
                    onClick = onNavigateToFoodReport,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = GlucoachColors.Primary,
                            contentColor = Color.White,
                        ),
                ) {
                    Text("음식 성적표로 가기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        AnimatedVisibility(
            visible = showPhotoViewer && hasImage,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { showPhotoViewer = false },
            ) {
                if (meal.imageUrl != null) {
                    AsyncImage(
                        model = meal.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else if (meal.imageResId != null) {
                    Image(
                        painter = painterResource(id = meal.imageResId),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                IconButton(
                    onClick = { showPhotoViewer = false },
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 40.dp, end = 16.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ── 메모 입력 다이얼로그 ──────────────────────────────────

@Composable
private fun MemoInputDialog(
    memo: String,
    onMemoChange: (String) -> Unit,
    photoUri: Uri? = null,
    onPickPhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
    initialDateTime: LocalDateTime = LocalDateTime.now(),
    onConfirm: (LocalDateTime) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedDateTime by remember { mutableStateOf(initialDateTime) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(GlucoachCorner.card))
                    .background(GlucoachColors.Surface)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "식사 기록",
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(GlucoachSpacing.md))

            Text(
                text = "사진",
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(GlucoachSpacing.sm))
            if (photoUri != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(GlucoachCorner.card)),
                ) {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    IconButton(
                        onClick = onRemovePhoto,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "삭제",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(GlucoachCorner.card))
                            .border(1.dp, GlucoachColors.Border, RoundedCornerShape(GlucoachCorner.card))
                            .clickable(onClick = onPickPhoto),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CameraAlt,
                            contentDescription = null,
                            tint = GlucoachColors.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "사진 추가 (선택)",
                            color = GlucoachColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(GlucoachSpacing.lg))

            Text(
                text = "식사 시간",
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(GlucoachSpacing.sm))
            com.ssafy.s309.ui.component.MealDateTimePicker(
                initialDateTime = initialDateTime,
                onDateTimeChanged = { selectedDateTime = it },
            )
            Spacer(Modifier.height(GlucoachSpacing.lg))
            Text(
                text = "메모",
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(GlucoachSpacing.sm))
            androidx.compose.material3.OutlinedTextField(
                value = memo,
                onValueChange = onMemoChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "메모를 입력하세요 (선택)",
                        color = GlucoachColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                },
                shape = RoundedCornerShape(GlucoachCorner.card),
                colors =
                    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlucoachColors.Primary,
                        unfocusedBorderColor = GlucoachColors.Border,
                    ),
                minLines = 2,
                maxLines = 4,
            )
            Spacer(Modifier.height(GlucoachSpacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Text("취소", fontSize = 14.sp)
                }
                androidx.compose.material3.Button(
                    onClick = { onConfirm(selectedDateTime) },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    colors =
                        androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = GlucoachColors.Primary,
                            contentColor = Color.White,
                        ),
                ) {
                    Text("확인", fontSize = 14.sp)
                }
            }
        }
    }
}

// ── 상세 탭 컨텐츠 ──────────────────────────────────────

@Composable
private fun MemoTabContent(description: String) {
    if (description.isNotEmpty()) {
        Text(
            text = description,
            color = GlucoachColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
    } else {
        Text(
            text = "메모가 없습니다",
            color = GlucoachColors.TextSecondary.copy(alpha = 0.5f),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun GlucoseTabContent(meal: MealRecord) {
    if (meal.maxGlucose == null && meal.glucose1h == null && meal.glucose2h == null) {
        Text(
            text = "혈당 기록이 없습니다",
            color = GlucoachColors.TextSecondary.copy(alpha = 0.5f),
            fontSize = 14.sp,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        meal.maxGlucose?.let { GlucoseRow("최고 혈당", "${it}mg/dL") }
        meal.glucose1h?.let { GlucoseRow("식후 1시간", "${it}mg/dL") }
        meal.glucose2h?.let { GlucoseRow("식후 2시간", "${it}mg/dL") }
    }
}

@Composable
private fun GlucoseRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, color = GlucoachColors.TextSecondary, fontSize = 14.sp)
        Text(
            text = value,
            color = GlucoachColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NutritionTabContent(meal: MealRecord) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        NutritionTabItem("열량", "${meal.calories}kcal", Color(0xFFE65100))
        NutritionTabItem("탄수화물", String.format("%.2fg", meal.carbs), GlucoachColors.Primary)
        NutritionTabItem("단백질", String.format("%.2fg", meal.protein), GlucoachColors.Primary)
        NutritionTabItem("지방", String.format("%.1fg", meal.fat), GlucoachColors.Primary)
    }
}

@Composable
private fun NutritionTabItem(
    label: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = GlucoachColors.TextSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(text = value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatMealDateTimeLabel(meal: MealRecord): String =
    try {
        val dt = LocalDateTime.parse(meal.recordedAt)
        val amPm = if (dt.hour < 12) "오전" else "오후"
        val hour12 = if (dt.hour % 12 == 0) 12 else dt.hour % 12
        "${dt.year}년 ${dt.monthValue}월 ${dt.dayOfMonth}일 ${mealTypeDisplayName(meal.mealType)}  $amPm ${hour12}시 ${dt.minute}분"
    } catch (_: Exception) {
        "${meal.year}년 ${meal.month}월 ${meal.day}일 ${mealTypeDisplayName(meal.mealType)}"
    }

@Composable
private fun FoodGradeBadge(grade: String?) {
    val isPending = grade == null
    val bgColor = if (isPending) Color(0xFFA9A9A9) else gradeColor(grade)
    val textColor =
        if (isPending) {
            Color.White
        } else {
            when (grade) {
                "B", "C" -> GlucoachColors.TextPrimary
                else -> Color.White
            }
        }

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isPending) "측정 중" else grade!!.uppercase(),
            color = textColor,
            fontSize = if (isPending) 11.sp else 13.sp,
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
