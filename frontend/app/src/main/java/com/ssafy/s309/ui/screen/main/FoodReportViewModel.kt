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
import java.util.concurrent.TimeUnit
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
                foodRepository
                    .getFoodGrades()
                    .onSuccess { responses ->
                        val byGrade = responses.groupBy { it.grade }
                        val gradeFoodItems =
                            byGrade.mapValues { (_, list) ->
                                list.map { it.toGradeFoodItem() }
                            }
                        val gradeInfoList =
                            FoodReportMockData.gradeInfoList.map { info ->
                                info.copy(count = byGrade[info.grade]?.size ?: 0)
                            }
                        _uiState.value = FoodReportUiState.Success(gradeInfoList, gradeFoodItems)
                    }.onFailure { e ->
                        _uiState.value = FoodReportUiState.Error(e.message ?: "데이터를 불러올 수 없어요")
                    }
            }
        }

        private fun FoodGradeResponse.toGradeFoodItem() =
            GradeFoodItem(
                name = foodName,
                frequency = mealCount,
                lastEaten = formatLastEaten(lastEatenAt),
                glucoseRise = avgSlope.toInt(),
                trend = FoodTrend.STABLE,
                measureCount = mealCount,
            )

        private fun formatLastEaten(isoDateTime: String): String =
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                val date: Date = sdf.parse(isoDateTime) ?: return "-"
                val diffMs = System.currentTimeMillis() - date.time
                val days = TimeUnit.MILLISECONDS.toDays(diffMs)
                when {
                    days == 0L -> "오늘"
                    days == 1L -> "어제"
                    days < 7L -> "${days}일 전"
                    days < 30L -> "${days / 7}주 전"
                    else -> "${days / 30}달 전"
                }
            } catch (e: Exception) {
                "-"
            }
    }
