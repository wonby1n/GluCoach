package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.FoodGradeInfo
import com.ssafy.s309.data.model.FoodGradeResponse
import com.ssafy.s309.data.model.FoodTrend
import com.ssafy.s309.data.model.GradeFoodItem
import com.ssafy.s309.data.repository.FoodReportMockData
import com.ssafy.s309.data.repository.FoodRepository
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
    ) : FoodReportUiState()

    data class Error(val message: String) : FoodReportUiState()
}

@HiltViewModel
class FoodReportViewModel
    @Inject
    constructor(
        private val foodRepository: FoodRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<FoodReportUiState>(FoodReportUiState.Loading)
        val uiState: StateFlow<FoodReportUiState> = _uiState.asStateFlow()

        init {
            loadFoodGrades()
        }

        fun loadFoodGrades() {
            viewModelScope.launch {
                _uiState.value = FoodReportUiState.Loading
                foodRepository.getFoodGrades()
                    .onSuccess { responses ->
                        val gradeFoodItems =
                            responses
                                .groupBy { it.grade }
                                .mapValues { (_, items) -> items.map { it.toGradeFoodItem() } }

                        val gradeInfoList =
                            FoodReportMockData.gradeInfoList.map { info ->
                                info.copy(count = gradeFoodItems[info.grade]?.size ?: 0)
                            }

                        _uiState.value =
                            FoodReportUiState.Success(
                                gradeInfoList = gradeInfoList,
                                gradeFoodItems = gradeFoodItems,
                            )
                    }
                    .onFailure { e ->
                        _uiState.value = FoodReportUiState.Error(e.message ?: "데이터를 불러오지 못했어요")
                    }
            }
        }

        private fun FoodGradeResponse.toGradeFoodItem(): GradeFoodItem =
            GradeFoodItem(
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
