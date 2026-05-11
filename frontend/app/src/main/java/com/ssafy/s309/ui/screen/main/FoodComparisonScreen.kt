package com.ssafy.s309.ui.screen.main

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.ssafy.s309.R
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.model.GlucoseCompareResponse
import com.ssafy.s309.data.model.GlucosePrediction
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.ui.component.FoodCategoryImageMapper
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import com.ssafy.s309.ui.viewmodel.FoodSearchViewModel
import kotlinx.coroutines.delay

// ── 데이터 ──────────────────────────────────────────────

internal data class FoodItem(
    val id: Long = 0,
    val name: String,
    val category: String = "",
    @DrawableRes val imageResId: Int = R.drawable.kiki_main,
    val isStable: Boolean = true,
    val maxGlucose: Int = 0,
    val recoveryTimeText: String = "",
    val calories: Int = 0,
    val gi: Int = 0,
    val carbs: Int = 0,
    val sugar: Int = 0,
    val protein: Int = 0,
    val fat: Int = 0,
    val fiber: Int = 0,
    val servingSize: Int = 0,
    val glucoseCurve: List<Float> = emptyList(),
)

private fun FoodSearchItem.toFoodItem() =
    FoodItem(
        id = id.toLong(),
        name = name,
        category = category.orEmpty(),
        imageResId = FoodCategoryImageMapper.getImageRes(category, name),
        calories = kcal?.toInt() ?: 0,
        carbs = carbsG?.toInt() ?: 0,
        sugar = sugarG?.toInt() ?: 0,
        protein = proteinG?.toInt() ?: 0,
        fat = fatG?.toInt() ?: 0,
        fiber = fiberG?.toInt() ?: 0,
        servingSize = servingSize?.toInt() ?: 0,
    )

private val allFoods =
    listOf(
        FoodItem(
            name = "짜장면",
            imageResId = R.drawable.jjajangmyeon,
            isStable = false,
            maxGlucose = 185,
            recoveryTimeText = "2시간 15분",
            calories = 700,
            gi = 80,
            carbs = 150,
            protein = 20,
            fat = 25,
            glucoseCurve = listOf(90f, 155f, 175f, 185f, 178f, 165f, 145f, 130f, 120f),
        ),
        FoodItem(
            name = "짬뽕",
            imageResId = R.drawable.jjambbong,
            isStable = true,
            maxGlucose = 140,
            recoveryTimeText = "1시간 10분",
            calories = 630,
            gi = 70,
            carbs = 130,
            protein = 30,
            fat = 20,
            glucoseCurve = listOf(90f, 125f, 140f, 138f, 128f, 115f, 105f, 98f, 95f),
        ),
        FoodItem(
            name = "연어샐러드",
            imageResId = R.drawable.jjajangmyeon,
            isStable = true,
            maxGlucose = 125,
            recoveryTimeText = "1시간",
            calories = 350,
            gi = 40,
            carbs = 20,
            protein = 35,
            fat = 15,
            glucoseCurve = listOf(90f, 110f, 125f, 120f, 112f, 105f, 98f, 93f, 90f),
        ),
        FoodItem(
            name = "연어(조리전)",
            imageResId = R.drawable.jjajangmyeon,
            isStable = true,
            maxGlucose = 110,
            recoveryTimeText = "50분",
            calories = 208,
            gi = 0,
            carbs = 0,
            protein = 40,
            fat = 6,
            glucoseCurve = listOf(90f, 100f, 110f, 108f, 102f, 97f, 93f, 91f, 90f),
        ),
        FoodItem(
            name = "연어회",
            imageResId = R.drawable.jjajangmyeon,
            isStable = true,
            maxGlucose = 105,
            recoveryTimeText = "45분",
            calories = 180,
            gi = 0,
            carbs = 2,
            protein = 38,
            fat = 5,
            glucoseCurve = listOf(90f, 98f, 105f, 103f, 99f, 95f, 92f, 91f, 90f),
        ),
        FoodItem(
            name = "연어구이",
            imageResId = R.drawable.jjajangmyeon,
            isStable = true,
            maxGlucose = 115,
            recoveryTimeText = "55분",
            calories = 250,
            gi = 5,
            carbs = 5,
            protein = 42,
            fat = 8,
            glucoseCurve = listOf(90f, 105f, 115f, 112f, 106f, 100f, 95f, 92f, 90f),
        ),
        FoodItem(
            name = "훈제연어",
            imageResId = R.drawable.jjajangmyeon,
            isStable = true,
            maxGlucose = 108,
            recoveryTimeText = "50분",
            calories = 190,
            gi = 0,
            carbs = 1,
            protein = 36,
            fat = 7,
            glucoseCurve = listOf(90f, 100f, 108f, 105f, 100f, 96f, 93f, 91f, 90f),
        ),
        FoodItem(
            name = "고등어구이",
            imageResId = R.drawable.jjambbong,
            isStable = true,
            maxGlucose = 118,
            recoveryTimeText = "55분",
            calories = 305,
            gi = 0,
            carbs = 0,
            protein = 38,
            fat = 18,
            glucoseCurve = listOf(90f, 106f, 118f, 115f, 108f, 101f, 96f, 92f, 90f),
        ),
    )

// ── 진입점: 선택 ↔ 결과 상태 관리 ────────────────────────

@Composable
fun FoodComparisonContent(
    onMealSaved: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val foodSearchViewModel: FoodSearchViewModel = hiltViewModel()
    val viewModel: FoodComparisonViewModel = hiltViewModel()
    var foodA by remember { mutableStateOf<FoodItem?>(null) }
    var foodB by remember { mutableStateOf<FoodItem?>(null) }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.mealRecorded) {
        if (uiState.mealRecorded) {
            viewModel.resetResult()
            onMealSaved()
        }
    }

    if (uiState.result != null && foodA != null && foodB != null) {
        FoodComparisonResultContent(
            foodA = foodA!!,
            foodB = foodB!!,
            compareResult = uiState.result!!,
            glucoseRange = uiState.glucoseRange,
            onResetSelection = {
                foodA = null
                foodB = null
                viewModel.resetResult()
            },
            onFoodARemoved = {
                foodA = null
                viewModel.resetResult()
            },
            onFoodBRemoved = {
                foodB = null
                viewModel.resetResult()
            },
            onSelectMeal = { food, hour -> viewModel.selectMeal(food, hour) },
            modifier = modifier,
        )
    } else {
        FoodSelectionContent(
            foodA = foodA,
            foodB = foodB,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onFoodSelected = { food ->
                if (foodA == null) foodA = food else foodB = food
            },
            onCompareClick = {
                if (foodA != null && foodB != null) {
                    viewModel.compareGlucose(foodA!!, foodB!!)
                }
            },
            onErrorDismiss = { viewModel.clearError() },
            onFoodARemoved = { foodA = null },
            onFoodBRemoved = { foodB = null },
            foodSearchViewModel = foodSearchViewModel,
            modifier = modifier,
        )
    }
}

// ── 음식 선택 화면 (0~1개 선택 상태) ─────────────────────

@Composable
private fun FoodSelectionContent(
    foodA: FoodItem?,
    foodB: FoodItem?,
    isLoading: Boolean,
    error: String?,
    onFoodSelected: (FoodItem) -> Unit,
    onCompareClick: () -> Unit,
    onErrorDismiss: () -> Unit,
    onFoodARemoved: () -> Unit,
    onFoodBRemoved: () -> Unit,
    foodSearchViewModel: FoodSearchViewModel,
    modifier: Modifier = Modifier,
) {
    var showSearchDialog by remember { mutableStateOf(false) }
    var showNutritionDialog by remember { mutableStateOf(false) }
    var nutritionFood by remember { mutableStateOf<FoodItem?>(null) }
    val bothSelected = foodA != null && foodB != null

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            Text(
                text = "음식 비교 시뮬레이션",
                color = GlucoachColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

            Text(
                text = "메뉴 선택 시 혈당 때문에 고민이신가요?",
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
            )
            Text(
                text = "아래에 음식을 추가해서 예상 혈당 값을 비교해보세요.",
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            ) {
                if (foodA != null) {
                    SelectedFoodSlot(
                        food = foodA,
                        onInfoClick = {
                            nutritionFood = foodA
                            showNutritionDialog = true
                        },
                        onRemoveClick = onFoodARemoved,
                        showHint = foodB == null,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    EmptyFoodSlot(
                        onClick = { showSearchDialog = true },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (foodB != null) {
                    SelectedFoodSlot(
                        food = foodB,
                        onInfoClick = {
                            nutritionFood = foodB
                            showNutritionDialog = true
                        },
                        onRemoveClick = onFoodBRemoved,
                        showHint = foodA == null,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    EmptyFoodSlot(
                        onClick = { showSearchDialog = true },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            EmptyChartPlaceholder()

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            if (error != null) {
                Text(
                    text = error,
                    color = GlucoachColors.SpikeBadgeText,
                    fontSize = 13.sp,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlucoachColors.SpikeBadgeBg)
                            .padding(GlucoachSpacing.md)
                            .clickable(onClick = onErrorDismiss),
                )
                Spacer(modifier = Modifier.height(GlucoachSpacing.md))
            }

            Button(
                onClick = onCompareClick,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors =
                    if (bothSelected) {
                        ButtonDefaults.buttonColors(
                            containerColor = GlucoachColors.Primary,
                            contentColor = Color.White,
                        )
                    } else {
                        ButtonDefaults.buttonColors(
                            containerColor = GlucoachColors.Border,
                            contentColor = GlucoachColors.TextSecondary,
                        )
                    },
                enabled = bothSelected && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = if (bothSelected) "비교 시작하기" else "선택된 음식이 없어요",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }

        if (showSearchDialog) {
            FoodSearchDialog(
                foodSearchViewModel = foodSearchViewModel,
                onFoodSelected = { food ->
                    onFoodSelected(food.toFoodItem())
                    showSearchDialog = false
                },
                onDismiss = {
                    foodSearchViewModel.clearSearch()
                    showSearchDialog = false
                },
            )
        }

        if (showNutritionDialog && nutritionFood != null) {
            FoodNutritionDetailDialog(
                food = nutritionFood!!,
                onDismiss = { showNutritionDialog = false },
            )
        }
    }
}

@Composable
private fun EmptyFoodSlot(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(285.dp)
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .border(1.dp, GlucoachColors.Border, RoundedCornerShape(GlucoachCorner.card))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = "음식 추가",
            tint = GlucoachColors.Primary,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun SelectedFoodSlot(
    food: FoodItem,
    onInfoClick: () -> Unit,
    onRemoveClick: () -> Unit,
    showHint: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(285.dp)
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .border(1.dp, GlucoachColors.Border, RoundedCornerShape(GlucoachCorner.card))
                .padding(GlucoachSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    text = food.name,
                    color = GlucoachColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(GlucoachSpacing.xs))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "영양 정보",
                    tint = GlucoachColors.TextSecondary,
                    modifier =
                        Modifier
                            .size(18.dp)
                            .clickable(onClick = onInfoClick),
                )
            }
            Spacer(modifier = Modifier.width(GlucoachSpacing.xs))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "음식 삭제",
                tint = GlucoachColors.TextSecondary,
                modifier =
                    Modifier
                        .size(18.dp)
                        .clickable(onClick = onRemoveClick),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Image(
            painter = painterResource(id = food.imageResId),
            contentDescription = food.name,
            modifier = Modifier.size(100.dp).align(Alignment.CenterHorizontally),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        if (showHint) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlucoachColors.Border)
                        .padding(horizontal = GlucoachSpacing.md, vertical = GlucoachSpacing.xs),
            ) {
                Text(
                    text = "하나 더 선택해주세요",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.md))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, GlucoachColors.Border, RoundedCornerShape(8.dp)),
        ) {
            StatCell(
                label = "최고 혈당",
                value = "${food.maxGlucose}",
                unit = "mg/dL",
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(GlucoachColors.Border),
            )
            StatCell(
                label = "정상 복귀",
                value = food.recoveryTimeText,
                unit = "",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun EmptyChartPlaceholder() {
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
            text = "혈당 예측 곡선 비교",
            color = GlucoachColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        val chartHeight = 140.dp
        val gridColor = GlucoachColors.ChartGrid

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * 0.15f),
                    end = Offset(w, h * 0.15f),
                    strokeWidth = 1f,
                    pathEffect = dashEffect,
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * 0.85f),
                    end = Offset(w, h * 0.85f),
                    strokeWidth = 1f,
                    pathEffect = dashEffect,
                )
            }

            Text(
                text = "170",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart),
            )

            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(GlucoachColors.Background)
                        .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.sm),
            ) {
                Text(
                    text = "음식을 추가하면 혈당 그래프를 비교할 수 있어요.",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }

            Text(
                text = "90",
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
            listOf("식사 직후", "30분", "1시간", "2시간").forEach { label ->
                Text(
                    text = label,
                    color = GlucoachColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

// ── 검색 다이얼로그 ──────────────────────────────────────

@Composable
internal fun FoodSearchDialog(
    foodSearchViewModel: FoodSearchViewModel,
    onFoodSelected: (FoodSearchItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchResults by foodSearchViewModel.results.collectAsState()
    val isSearchLoading by foodSearchViewModel.isLoading.collectAsState()
    val searchError by foodSearchViewModel.error.collectAsState()
    val recentKeywords by foodSearchViewModel.recentKeywords.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(GlucoachCorner.card))
                    .background(GlucoachColors.Surface)
                    .padding(GlucoachSpacing.xl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchTab(
                    text = "검색하기",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                )
                SearchTab(
                    text = "최근 검색 목록",
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = GlucoachColors.TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            when (selectedTab) {
                0 -> {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            foodSearchViewModel.onQueryChanged(it.text)
                        },
                        singleLine = true,
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Search,
                            ),
                        textStyle =
                            TextStyle(
                                color = GlucoachColors.TextPrimary,
                                fontSize = 14.sp,
                            ),
                        placeholder = {
                            Text(
                                text = "음식명을 입력해주세요.",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 14.sp,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = GlucoachColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        trailingIcon =
                            if (searchQuery.text.isNotEmpty()) {
                                {
                                    IconButton(
                                        onClick = {
                                            searchQuery = TextFieldValue("")
                                            foodSearchViewModel.clearSearch()
                                        },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Clear,
                                            contentDescription = "지우기",
                                            tint = GlucoachColors.TextSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        shape = RoundedCornerShape(22.dp),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GlucoachColors.Primary,
                                unfocusedBorderColor = GlucoachColors.Border,
                                cursorColor = GlucoachColors.Primary,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )

                    LaunchedEffect(Unit) {
                        delay(200)
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }

                    Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

                    if (searchQuery.text.isBlank()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "검색된 내역이 없어요",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    } else if (isSearchLoading) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = GlucoachColors.Primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    } else if (searchError != null) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = searchError ?: "검색 실패",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    } else if (searchResults.isEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "검색 결과가 없어요",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            searchResults.forEach { item ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = GlucoachSpacing.md)
                                            .clickable {
                                                foodSearchViewModel.addRecentKeyword(item.name)
                                                onFoodSelected(item)
                                            },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            color = GlucoachColors.TextPrimary,
                                            fontSize = 15.sp,
                                        )
                                        if (!item.category.isNullOrBlank()) {
                                            Text(
                                                text = item.category,
                                                color = GlucoachColors.TextSecondary,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.kcal?.let { "${it.toInt()}kcal" } ?: "-",
                                        color = GlucoachColors.TextSecondary,
                                        fontSize = 13.sp,
                                    )
                                }
                                HorizontalDivider(color = GlucoachColors.Border.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                1 -> {
                    if (recentKeywords.isEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "최근 검색 내역이 없어요",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .heightIn(max = 300.dp)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            recentKeywords.toList().forEach { keyword ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = GlucoachSpacing.md)
                                            .clickable {
                                                selectedTab = 0
                                                searchQuery = TextFieldValue(keyword)
                                                foodSearchViewModel.onQueryChanged(keyword)
                                            },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = keyword,
                                        color = GlucoachColors.TextPrimary,
                                        fontSize = 15.sp,
                                    )
                                    IconButton(
                                        onClick = { foodSearchViewModel.removeRecentKeyword(keyword) },
                                        modifier = Modifier.size(24.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Clear,
                                            contentDescription = "삭제",
                                            tint = GlucoachColors.TextSecondary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                                HorizontalDivider(color = GlucoachColors.Border.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = if (isSelected) GlucoachColors.TextPrimary else GlucoachColors.TextSecondary,
        fontSize = 16.sp,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
        modifier =
            modifier
                .clickable(onClick = onClick)
                .padding(vertical = GlucoachSpacing.sm),
    )
}

// ── 비교 결과 화면 (2개 모두 선택) ───────────────────────

@Composable
private fun FoodComparisonResultContent(
    foodA: FoodItem,
    foodB: FoodItem,
    compareResult: GlucoseCompareResponse,
    glucoseRange: GlucoseRange,
    onResetSelection: () -> Unit,
    onFoodARemoved: () -> Unit,
    onFoodBRemoved: () -> Unit,
    onSelectMeal: (FoodItem, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFoodIndex by remember { mutableIntStateOf(1) }
    var showNutritionDialog by remember { mutableStateOf(false) }
    var nutritionDialogFoodIndex by remember { mutableIntStateOf(0) }
    var showMealTimeDialog by remember { mutableStateOf(false) }
    val foods = listOf(foodA, foodB)
    val predictions = listOf(compareResult.foodA, compareResult.foodB)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            Text(
                text = "음식 비교 시뮬레이션",
                color = GlucoachColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            ) {
                val removeCallbacks = listOf(onFoodARemoved, onFoodBRemoved)
                foods.forEachIndexed { index, food ->
                    FoodCard(
                        food = food,
                        prediction = predictions[index],
                        isSelected = index == selectedFoodIndex,
                        onSelect = { selectedFoodIndex = index },
                        onInfoClick = {
                            nutritionDialogFoodIndex = index
                            showNutritionDialog = true
                        },
                        onRemoveClick = removeCallbacks[index],
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            GlucoseComparisonChart(
                foodA = foodA,
                foodB = foodB,
                predictionA = compareResult.foodA,
                predictionB = compareResult.foodB,
                glucoseRange = glucoseRange,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            TipCard()

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            ) {
                OutlinedButton(
                    onClick = onResetSelection,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = GlucoachColors.Primary,
                        ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlucoachColors.Primary),
                ) {
                    Text(
                        text = "다른 음식 볼래요",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = { showMealTimeDialog = true },
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
                        text = "이 음식 선택할래요",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }

        if (showNutritionDialog) {
            FoodNutritionDetailDialog(
                food = foods[nutritionDialogFoodIndex],
                onDismiss = { showNutritionDialog = false },
            )
        }

        if (showMealTimeDialog) {
            MealTimePickerDialog(
                onTimeSelected = { hour ->
                    showMealTimeDialog = false
                    onSelectMeal(foods[selectedFoodIndex], hour)
                },
                onDismiss = { showMealTimeDialog = false },
            )
        }
    }
}

// ── 식사 시간 선택 다이얼로그 ──────────────────────────────

private data class MealTimeOption(
    val label: String,
    val subLabel: String,
    val hour: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun MealTimePickerDialog(
    onTimeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val options =
        listOf(
            MealTimeOption("아침", "06 - 10시", 8, Icons.Outlined.WbSunny),
            MealTimeOption("점심", "11 - 14시", 12, Icons.Outlined.LightMode),
            MealTimeOption("저녁", "17 - 21시", 19, Icons.Outlined.DarkMode),
            MealTimeOption("간식", "그 외", 15, Icons.Outlined.LocalCafe),
        )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlucoachColors.Surface)
                    .padding(GlucoachSpacing.xl),
        ) {
            Text(
                text = "언제 드셨나요?",
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(GlucoachSpacing.xs))
            Text(
                text = "식사 시간대를 선택해주세요",
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm),
            ) {
                options.forEach { option ->
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(GlucoachCorner.card))
                                .background(GlucoachColors.Primary.copy(alpha = 0.1f))
                                .clickable { onTimeSelected(option.hour) }
                                .padding(vertical = GlucoachSpacing.lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = option.label,
                            tint = GlucoachColors.PrimaryDark,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(GlucoachSpacing.sm))
                        Text(
                            text = option.label,
                            color = GlucoachColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = option.subLabel,
                            color = GlucoachColors.TextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(GlucoachSpacing.lg))

            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = "취소",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

// ── 공용 컴포넌트 ────────────────────────────────────────

@Composable
private fun FoodCard(
    food: FoodItem,
    prediction: GlucosePrediction,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInfoClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) GlucoachColors.Primary else GlucoachColors.Border
    val isStable = prediction.peakMgdl < 140f
    val peakReachText = formatMinutes(prediction.peakMinute)

    Column(
        modifier =
            modifier
                .height(285.dp)
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(GlucoachCorner.card),
                )
                .clickable(onClick = onSelect)
                .padding(GlucoachSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    text = food.name,
                    color = GlucoachColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(GlucoachSpacing.xs))
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "영양 정보",
                    tint = GlucoachColors.TextSecondary,
                    modifier =
                        Modifier
                            .size(18.dp)
                            .clickable(onClick = onInfoClick),
                )
            }
            Spacer(modifier = Modifier.width(GlucoachSpacing.xs))
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "음식 삭제",
                tint = GlucoachColors.TextSecondary,
                modifier =
                    Modifier
                        .size(18.dp)
                        .clickable(onClick = onRemoveClick),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Image(
            painter = painterResource(id = food.imageResId),
            contentDescription = food.name,
            modifier = Modifier.size(100.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        SpikeStatusBadge(isStable = isStable)

        Spacer(modifier = Modifier.height(GlucoachSpacing.md))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, GlucoachColors.Border, RoundedCornerShape(8.dp)),
        ) {
            StatCell(
                label = "최고 혈당",
                value = "${prediction.peakMgdl.toInt()}",
                unit = "mg/dL",
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(GlucoachColors.Border),
            )
            StatCell(
                label = "피크 도달",
                value = peakReachText,
                unit = "",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun formatMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}시간 ${mins}분"
        hours > 0 -> "${hours}시간"
        else -> "${mins}분"
    }
}

@Composable
private fun SpikeStatusBadge(isStable: Boolean) {
    val badgeColor = if (isStable) GlucoachColors.StableBadgeBg else GlucoachColors.SpikeBadgeBg
    val textColor = if (isStable) GlucoachColors.StableBadgeText else GlucoachColors.SpikeBadgeText
    val text = if (isStable) "안정적이에요" else "스파이크 높음"

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(badgeColor)
                .padding(horizontal = GlucoachSpacing.md, vertical = GlucoachSpacing.xs),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(vertical = GlucoachSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = GlucoachColors.TextSecondary,
            fontSize = 10.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                color = GlucoachColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = unit,
                    color = GlucoachColors.TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun GlucoseComparisonChart(
    foodA: FoodItem,
    foodB: FoodItem,
    predictionA: GlucosePrediction,
    predictionB: GlucosePrediction,
    glucoseRange: GlucoseRange,
) {
    val rangeHigh = glucoseRange.maxMgDl.toFloat()
    val rangeLow = glucoseRange.minMgDl.toFloat()
    val curveA = predictionA.curve.map { it.glucoseMgdl }
    val curveB = predictionB.curve.map { it.glucoseMgdl }
    val allValues = curveA + curveB + listOf(rangeHigh, rangeLow)
    val dataMax = allValues.maxOrNull() ?: 200f
    val dataMin = allValues.minOrNull() ?: 70f
    val padding = ((dataMax - dataMin) * 0.12f).coerceAtLeast(10f)
    val maxVal = dataMax + padding
    val minVal = dataMin - padding

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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "혈당 예측 곡선 비교",
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(horizontalAlignment = Alignment.End) {
                LegendDot(color = GlucoachColors.ChartLineInactive, label = foodA.name)
                Spacer(modifier = Modifier.height(4.dp))
                LegendDot(color = GlucoachColors.Primary, label = foodB.name)
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        val chartHeight = 160.dp
        val gridColor = GlucoachColors.ChartGrid

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val rangeVal = maxVal - minVal

                fun yFor(v: Float) = h - ((v - minVal) / rangeVal) * h

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

                drawLine(
                    color = gridColor,
                    start = Offset(0f, yFor(rangeHigh)),
                    end = Offset(w, yFor(rangeHigh)),
                    strokeWidth = 1f,
                    pathEffect = dashEffect,
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, yFor(rangeLow)),
                    end = Offset(w, yFor(rangeLow)),
                    strokeWidth = 1f,
                    pathEffect = dashEffect,
                )

                val shadeBrush =
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                GlucoachColors.Primary.copy(alpha = 0.08f),
                                GlucoachColors.Primary.copy(alpha = 0.02f),
                            ),
                        startY = yFor(rangeHigh),
                        endY = yFor(rangeLow),
                    )
                val shadePath =
                    Path().apply {
                        moveTo(0f, yFor(rangeHigh))
                        lineTo(w, yFor(rangeHigh))
                        lineTo(w, yFor(rangeLow))
                        lineTo(0f, yFor(rangeLow))
                        close()
                    }
                drawPath(shadePath, shadeBrush)

                fun drawCurve(
                    points: List<Float>,
                    color: Color,
                ) {
                    if (points.size < 2) return
                    val step = w / (points.size - 1)
                    val pts = points.mapIndexed { i, v -> Offset(i * step, yFor(v)) }
                    val path =
                        Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (i in 1 until pts.size) {
                                val p0 = pts[maxOf(i - 2, 0)]
                                val p1 = pts[i - 1]
                                val p2 = pts[i]
                                val p3 = pts[minOf(i + 1, pts.lastIndex)]
                                cubicTo(
                                    p1.x + (p2.x - p0.x) / 6f,
                                    p1.y + (p2.y - p0.y) / 6f,
                                    p2.x - (p3.x - p1.x) / 6f,
                                    p2.y - (p3.y - p1.y) / 6f,
                                    p2.x,
                                    p2.y,
                                )
                            }
                        }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 3f, cap = StrokeCap.Round),
                    )
                }

                drawCurve(curveA, GlucoachColors.ChartLineInactive)
                drawCurve(curveB, GlucoachColors.Primary)
            }

            val rangeTotal = maxVal - minVal
            val highOffsetY = (chartHeight.value * (maxVal - rangeHigh) / rangeTotal - 6f).dp
            val lowOffsetY = (chartHeight.value * (maxVal - rangeLow) / rangeTotal - 6f).dp
            Text(
                text = "${glucoseRange.maxMgDl}",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.offset(y = highOffsetY),
            )
            Text(
                text = "${glucoseRange.minMgDl}",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.offset(y = lowOffsetY),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        val maxMinute =
            maxOf(
                predictionA.curve.lastOrNull()?.minuteOffset ?: 120,
                predictionB.curve.lastOrNull()?.minuteOffset ?: 120,
            )
        val timeLabels = buildTimeLabels(maxMinute)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            timeLabels.forEach { label ->
                Text(
                    text = label,
                    color = GlucoachColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun buildTimeLabels(maxMinute: Int): List<String> {
    val labels = mutableListOf("식사 직후")
    val step =
        when {
            maxMinute <= 60 -> 15
            maxMinute <= 120 -> 30
            else -> 60
        }
    var m = step
    while (m <= maxMinute) {
        labels.add(
            when {
                m < 60 -> "${m}분"
                m % 60 == 0 -> "${m / 60}시간"
                else -> "${m / 60}시간 ${m % 60}분"
            },
        )
        m += step
    }
    return labels
}

@Composable
private fun LegendDot(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun TipCard() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.TipBg)
                .padding(GlucoachSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(GlucoachColors.TipAccent)
                    .padding(horizontal = GlucoachSpacing.sm, vertical = GlucoachSpacing.xs),
        ) {
            Text(
                text = "TIP",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.width(GlucoachSpacing.md))
        Text(
            text = "면을 절반 덜어내면 혈당 지수를 30% 낮출 수 있어요!",
            color = GlucoachColors.TextPrimary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun FoodNutritionDetailDialog(
    food: FoodItem,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .clip(RoundedCornerShape(GlucoachCorner.card))
                    .background(GlucoachColors.Surface)
                    .padding(GlucoachSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = GlucoachColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(GlucoachSpacing.xs))
                    Text(
                        text = food.name,
                        color = GlucoachColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = GlucoachColors.TextSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Image(
                painter = painterResource(id = food.imageResId),
                contentDescription = food.name,
                modifier = Modifier.size(150.dp),
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "영양성분",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${food.calories}kcal",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))
            HorizontalDivider(color = GlucoachColors.Border)

            NutritionRow("탄수화물", "${food.carbs}g")
            NutritionRow("단백질", "${food.protein}g")
            NutritionRow("지방", "${food.fat}g")
        }
    }
}

@Composable
private fun NutritionRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = GlucoachSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = GlucoachColors.TextSecondary,
            fontSize = 14.sp,
        )
        Text(
            text = value,
            color = GlucoachColors.TextPrimary,
            fontSize = 14.sp,
        )
    }
}
