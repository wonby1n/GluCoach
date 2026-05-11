package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.FoodGradeInfo
import com.ssafy.s309.data.model.FoodGradeResponse
import com.ssafy.s309.data.model.FoodTrend
import com.ssafy.s309.data.model.GradeFoodItem
import com.ssafy.s309.data.model.MealRecordResponse
import com.ssafy.s309.data.repository.FoodReportMockData
import com.ssafy.s309.data.repository.FoodRepository
import com.ssafy.s309.data.repository.MOCK_FOOD_GRADES
import com.ssafy.s309.data.repository.USE_FOOD_REPORT_MOCK
import com.ssafy.s309.data.repository.mockFoodMealHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed class FoodReportUiState {
    object Loading : FoodReportUiState()

    data class Success(
        val gradeInfoList: List<FoodGradeInfo>,
        val gradeFoodItems: Map<String, List<GradeFoodItem>>,
        val topFoods: List<GradeFoodItem>,
    ) : FoodReportUiState()

    data class Error(val message: String) : FoodReportUiState()
}

data class FoodHistoryState(
    val food: GradeFoodItem? = null,
    val grade: String = "",
    val meals: List<MealRecordResponse> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class FoodReportViewModel
    @Inject
    constructor(
        private val foodRepository: FoodRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<FoodReportUiState>(FoodReportUiState.Loading)
        val uiState: StateFlow<FoodReportUiState> = _uiState.asStateFlow()

        private val _foodHistory = MutableStateFlow(FoodHistoryState())
        val foodHistory: StateFlow<FoodHistoryState> = _foodHistory.asStateFlow()

        init {
            loadFoodGrades()
        }

        fun loadFoodGrades() {
            viewModelScope.launch {
                _uiState.value = FoodReportUiState.Loading

                val result =
                    if (USE_FOOD_REPORT_MOCK) {
                        Result.success(MOCK_FOOD_GRADES)
                    } else {
                        foodRepository.getFoodGrades()
                    }

                result
                    .onSuccess { responses ->
                        val gradeFoodItems =
                            responses
                                .groupBy { it.grade }
                                .mapValues { (_, items) -> items.map { it.toGradeFoodItem() } }

                        val gradeInfoList =
                            FoodReportMockData.gradeInfoList.map { info ->
                                info.copy(count = gradeFoodItems[info.grade]?.size ?: 0)
                            }

                        val topFoods =
                            responses
                                .sortedByDescending { it.mealCount }
                                .take(3)
                                .map { it.toGradeFoodItem() }

                        _uiState.value =
                            FoodReportUiState.Success(
                                gradeInfoList = gradeInfoList,
                                gradeFoodItems = gradeFoodItems,
                                topFoods = topFoods,
                            )
                    }
                    .onFailure { e ->
                        _uiState.value = FoodReportUiState.Error(e.message ?: "데이터를 불러오지 못했어요")
                    }
            }
        }

        fun loadFoodHistory(
            food: GradeFoodItem,
            grade: String,
        ) {
            _foodHistory.value = FoodHistoryState(food = food, grade = grade, isLoading = true)
            viewModelScope.launch {
                val result =
                    if (USE_FOOD_REPORT_MOCK) {
                        Result.success(mockFoodMealHistory(food.foodId))
                    } else {
                        foodRepository.getFoodMealHistory(food.foodId)
                    }

                result
                    .onSuccess { meals ->
                        _foodHistory.value =
                            _foodHistory.value.copy(
                                meals = meals,
                                isLoading = false,
                            )
                    }
                    .onFailure {
                        _foodHistory.value =
                            _foodHistory.value.copy(
                                meals = emptyList(),
                                isLoading = false,
                            )
                    }
            }
        }

        fun clearFoodHistory() {
            _foodHistory.value = FoodHistoryState()
        }

        private fun FoodGradeResponse.toGradeFoodItem(): GradeFoodItem =
            GradeFoodItem(
                foodId = foodId,
                name = foodName,
                frequency = mealCount,
                lastEaten = formatLastEaten(lastEatenAt),
                glucoseRise = avgSlope.toInt(),
                trend = FoodTrend.STABLE,
                measureCount = mealCount,
            )

        private fun formatLastEaten(isoDate: String): String =
            runCatching {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date: Date = inputFormat.parse(isoDate) ?: return@runCatching isoDate
                val diffMs = System.currentTimeMillis() - date.time
                val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()
                when {
                    diffDays == 0 -> "오늘"
                    diffDays == 1 -> "어제"
                    diffDays < 7 -> "${diffDays}일 전"
                    diffDays < 14 -> "1주 전"
                    diffDays < 30 -> "${diffDays / 7}주 전"
                    else -> "${diffDays / 30}달 전"
                }
            }.getOrDefault(isoDate)
    }
