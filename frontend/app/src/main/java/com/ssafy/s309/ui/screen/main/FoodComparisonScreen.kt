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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ssafy.s309.R
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

// ── 데이터 ──────────────────────────────────────────────

internal data class FoodItem(
    val name: String,
    @DrawableRes val imageResId: Int,
    val isStable: Boolean,
    val maxGlucose: Int,
    val recoveryTimeText: String,
    val calories: Int,
    val gi: Int,
    val carbs: Int,
    val protein: Int,
    val fat: Int,
    val glucoseCurve: List<Float>,
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
            imageResId = R.drawable.salmon_salad,
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
            imageResId = R.drawable.salmon_salad,
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
            imageResId = R.drawable.salmon_salad,
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
            imageResId = R.drawable.salmon_salad,
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
            imageResId = R.drawable.salmon_salad,
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
            imageResId = R.drawable.grilled_mackerel,
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
fun FoodComparisonContent(modifier: Modifier = Modifier) {
    var foodA by remember { mutableStateOf<FoodItem?>(null) }
    var foodB by remember { mutableStateOf<FoodItem?>(null) }

    if (foodA != null && foodB != null) {
        FoodComparisonResultContent(
            foodA = foodA!!,
            foodB = foodB!!,
            onResetSelection = {
                foodA = null
                foodB = null
            },
            modifier = modifier,
        )
    } else {
        FoodSelectionContent(
            foodA = foodA,
            foodB = foodB,
            onFoodSelected = { food ->
                if (foodA == null) foodA = food else foodB = food
            },
            modifier = modifier,
        )
    }
}

// ── 음식 선택 화면 (0~1개 선택 상태) ─────────────────────

@Composable
private fun FoodSelectionContent(
    foodA: FoodItem?,
    foodB: FoodItem?,
    onFoodSelected: (FoodItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSearchDialog by remember { mutableStateOf(false) }
    var showNutritionDialog by remember { mutableStateOf(false) }
    var nutritionFood by remember { mutableStateOf<FoodItem?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
        ) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

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

            Button(
                onClick = { },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = GlucoachColors.Border,
                        contentColor = GlucoachColors.TextSecondary,
                    ),
                enabled = false,
            ) {
                Text(
                    text = "선택된 음식이 없어요",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }

        if (showSearchDialog) {
            FoodSearchDialog(
                onFoodSelected = { food ->
                    onFoodSelected(food)
                    showSearchDialog = false
                },
                onDismiss = { showSearchDialog = false },
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
                .height(200.dp)
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .border(1.dp, GlucoachColors.Border, RoundedCornerShape(GlucoachCorner.card))
                .padding(GlucoachSpacing.lg),
    ) {
        Text(
            text = food.name,
            color = GlucoachColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Box {
            Image(
                painter = painterResource(id = food.imageResId),
                contentDescription = food.name,
                modifier = Modifier.size(100.dp).align(Alignment.Center),
                contentScale = ContentScale.Fit,
            )
            IconButton(
                onClick = onInfoClick,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(GlucoachColors.TextSecondary.copy(alpha = 0.7f)),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "영양 정보",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
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
                        .height(48.dp)
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
private fun FoodSearchDialog(
    onFoodSelected: (FoodItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    val recentKeywords = remember { mutableStateListOf("연어(조리전)", "연어구이", "연어회", "훈제연어") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val filteredFoods =
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            allFoods.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 80.dp)
                    .clip(RoundedCornerShape(GlucoachCorner.card))
                    .background(GlucoachColors.Surface)
                    .padding(GlucoachSpacing.xl),
        ) {
            // 탭 헤더
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

                if (selectedTab == 1) {
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
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            when (selectedTab) {
                0 -> {
                    // 검색 입력 필드
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .border(
                                        width = 1.5.dp,
                                        color =
                                            if (isSearchFocused) {
                                                GlucoachColors.Primary
                                            } else {
                                                GlucoachColors.Border
                                            },
                                        shape = RoundedCornerShape(22.dp),
                                    )
                                    .padding(horizontal = GlucoachSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                                tint = GlucoachColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(GlucoachSpacing.sm))
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle =
                                    TextStyle(
                                        color = GlucoachColors.TextPrimary,
                                        fontSize = 14.sp,
                                    ),
                                cursorBrush = SolidColor(GlucoachColors.Primary),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                        .onFocusChanged { isSearchFocused = it.isFocused },
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = "음식명을 입력해주세요.",
                                                color = GlucoachColors.TextSecondary,
                                                fontSize = 14.sp,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "지우기",
                                        tint = GlucoachColors.TextSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }

                        if (searchQuery.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(GlucoachSpacing.sm))
                            Text(
                                text = "취소",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 14.sp,
                                modifier =
                                    Modifier.clickable {
                                        searchQuery = ""
                                        focusManager.clearFocus()
                                    },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

                    if (searchQuery.isBlank()) {
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
                    } else if (filteredFoods.isEmpty()) {
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
                        Column {
                            filteredFoods.forEach { food ->
                                Text(
                                    text = food.name,
                                    color = GlucoachColors.TextPrimary,
                                    fontSize = 15.sp,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (!recentKeywords.contains(food.name)) {
                                                    recentKeywords.add(0, food.name)
                                                }
                                                onFoodSelected(food)
                                            }
                                            .padding(vertical = GlucoachSpacing.md),
                                )
                                HorizontalDivider(color = GlucoachColors.Border.copy(alpha = 0.5f))
                            }
                        }
                    }
                }

                1 -> {
                    // 최근 검색 목록
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
                        Column {
                            recentKeywords.toList().forEach { keyword ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val food = allFoods.find { it.name == keyword }
                                                if (food != null) {
                                                    onFoodSelected(food)
                                                } else {
                                                    selectedTab = 0
                                                    searchQuery = keyword
                                                }
                                            }
                                            .padding(vertical = GlucoachSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = keyword,
                                        color = GlucoachColors.TextPrimary,
                                        fontSize = 15.sp,
                                    )
                                    IconButton(
                                        onClick = { recentKeywords.remove(keyword) },
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

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun SearchTab(
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
    onResetSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedFoodIndex by remember { mutableIntStateOf(1) }
    var showNutritionDialog by remember { mutableStateOf(false) }
    var nutritionDialogFoodIndex by remember { mutableIntStateOf(0) }
    val foods = listOf(foodA, foodB)

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
        ) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

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
                foods.forEachIndexed { index, food ->
                    FoodCard(
                        food = food,
                        isSelected = index == selectedFoodIndex,
                        onSelect = { selectedFoodIndex = index },
                        onInfoClick = {
                            nutritionDialogFoodIndex = index
                            showNutritionDialog = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            GlucoseComparisonChart(foodA = foodA, foodB = foodB)

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
                    onClick = { },
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
    }
}

// ── 공용 컴포넌트 ────────────────────────────────────────

@Composable
private fun FoodCard(
    food: FoodItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) GlucoachColors.Primary else GlucoachColors.Border

    Box(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
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
            Text(
                text = food.name,
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start),
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

            Image(
                painter = painterResource(id = food.imageResId),
                contentDescription = food.name,
                modifier = Modifier.size(100.dp),
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

            SpikeStatusBadge(
                isStable = food.isStable,
                onInfoClick = onInfoClick,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
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
                            .height(48.dp)
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

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "선택됨",
                tint = GlucoachColors.Primary,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(GlucoachSpacing.sm)
                        .size(24.dp),
            )
        }
    }
}

@Composable
private fun SpikeStatusBadge(
    isStable: Boolean,
    onInfoClick: () -> Unit,
) {
    val badgeColor = if (isStable) GlucoachColors.StableBadgeBg else GlucoachColors.SpikeBadgeBg
    val textColor = if (isStable) GlucoachColors.StableBadgeText else GlucoachColors.SpikeBadgeText
    val text = if (isStable) "안정적이에요" else "스파이크 높음"

    Row(
        verticalAlignment = Alignment.CenterVertically,
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
        IconButton(
            onClick = onInfoClick,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "영양 정보",
                tint = textColor,
                modifier = Modifier.size(16.dp),
            )
        }
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
) {
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
                val minVal = 70f
                val maxVal = 200f
                val rangeVal = maxVal - minVal

                fun yFor(v: Float) = h - ((v - minVal) / rangeVal) * h

                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))

                drawLine(
                    color = gridColor,
                    start = Offset(0f, yFor(170f)),
                    end = Offset(w, yFor(170f)),
                    strokeWidth = 1f,
                    pathEffect = dashEffect,
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, yFor(90f)),
                    end = Offset(w, yFor(90f)),
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
                        startY = yFor(170f),
                        endY = yFor(90f),
                    )
                val shadePath =
                    Path().apply {
                        moveTo(0f, yFor(170f))
                        lineTo(w, yFor(170f))
                        lineTo(w, yFor(90f))
                        lineTo(0f, yFor(90f))
                        close()
                    }
                drawPath(shadePath, shadeBrush)

                fun drawCurve(
                    points: List<Float>,
                    color: Color,
                ) {
                    if (points.size < 2) return
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
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 3f, cap = StrokeCap.Round),
                    )
                }

                drawCurve(foodA.glucoseCurve, GlucoachColors.ChartLineInactive)
                drawCurve(foodB.glucoseCurve, GlucoachColors.Primary)
            }

            Text(
                text = "170",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Text(
                text = "90",
                color = GlucoachColors.TextSecondary,
                fontSize = 10.sp,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 20.dp),
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

            NutritionRow("혈당지수(GI)", "${food.gi}g")
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
