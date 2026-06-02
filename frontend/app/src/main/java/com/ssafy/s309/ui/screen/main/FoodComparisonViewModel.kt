package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.CompareExplainRequest
import com.ssafy.s309.data.model.FoodCompareItem
import com.ssafy.s309.data.model.GlucoseCompareRequest
import com.ssafy.s309.data.model.GlucoseCompareResponse
import com.ssafy.s309.data.model.GlucosePrediction
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.MealCreateRequest
import com.ssafy.s309.data.repository.FoodRepository
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.data.repository.PredictRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class FoodComparisonUiState(
    val isLoading: Boolean = false,
    val result: GlucoseCompareResponse? = null,
    val error: String? = null,
    val mealRecorded: Boolean = false,
    val mealError: String? = null,
    val glucoseRange: GlucoseRange = GlucoseRange(minMgDl = 90, maxMgDl = 170),
    val autoFoodA: FoodItem? = null,
    val autoFoodB: FoodItem? = null,
    val explainMessage: String? = null,
    val isExplainLoading: Boolean = false,
)

@HiltViewModel
class FoodComparisonViewModel
    @Inject
    constructor(
        private val predictRepository: PredictRepository,
        private val healthRepository: HealthRepository,
        private val foodRepository: FoodRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FoodComparisonUiState())
        val uiState: StateFlow<FoodComparisonUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                val range = healthRepository.getGlucoseTargetRange()
                _uiState.update { it.copy(glucoseRange = range) }
            }
        }

        internal fun compareGlucose(
            foodA: FoodItem,
            foodB: FoodItem,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val request =
                    GlucoseCompareRequest(
                        foodA = foodA.toCompareItem(),
                        foodB = foodB.toCompareItem(),
                    )
                predictRepository.compareGlucose(request)
                    .onSuccess { response ->
                        _uiState.update {
                            it.copy(result = response, isExplainLoading = true)
                        }
                        fetchExplain(foodA, foodB, response)
                    }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(isLoading = false, error = e.message ?: "혈당 예측 실패")
                        }
                    }
            }
        }

        internal fun selectMeal(
            food: FoodItem,
            dateTime: LocalDateTime,
            memo: String,
            imageFile: java.io.File? = null,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, mealError = null) }
                val recordedAt = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                healthRepository.createMealRecord(
                    request =
                        MealCreateRequest(
                            foodId = food.id.toInt(),
                            memo = memo.ifBlank { null },
                            recordedAt = recordedAt,
                        ),
                    imageFile = imageFile,
                )
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false, mealRecorded = true) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, mealError = e.message ?: "식사 기록 실패") }
                    }
            }
        }

        fun autoSearchAndCompare(
            aName: String,
            bName: String,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val itemA = foodRepository.searchFoods(aName).getOrNull()?.firstOrNull()
                val itemB = foodRepository.searchFoods(bName).getOrNull()?.firstOrNull()
                if (itemA == null || itemB == null) {
                    _uiState.update { it.copy(isLoading = false, error = "음식을 찾을 수 없어요") }
                    return@launch
                }
                val foodA = itemA.toFoodItem()
                val foodB = itemB.toFoodItem()
                _uiState.update { it.copy(autoFoodA = foodA, autoFoodB = foodB) }
                compareGlucose(foodA, foodB)
            }
        }

        private fun fetchExplain(
            foodA: FoodItem,
            foodB: FoodItem,
            result: GlucoseCompareResponse,
        ) {
            viewModelScope.launch {
                val request =
                    CompareExplainRequest(
                        foodAName = "짬뽕",
                        foodBName = if (foodA.name == "짬뽕") foodB.name else foodA.name,
                        foodAPeakMgdl = result.foodA.peakMgdl,
                        foodAPeakMinute = result.foodA.peakMinute,
                        foodASlope = slopeOf(result.foodA),
                        foodBPeakMgdl = result.foodB.peakMgdl,
                        foodBPeakMinute = result.foodB.peakMinute,
                        foodBSlope = slopeOf(result.foodB),
                    )
                predictRepository
                    .explainCompare(request)
                    .onSuccess { res ->
                        val message = res.message.replace("짜장면", "짬뽕")
                        _uiState.update { it.copy(isLoading = false, isExplainLoading = false, explainMessage = message) }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isLoading = false, isExplainLoading = false) }
                    }
            }
        }

        private fun slopeOf(pred: GlucosePrediction): Float {
            val baseline = pred.curve.firstOrNull()?.glucoseMgdl ?: pred.peakMgdl
            val minutes = pred.peakMinute.takeIf { it > 0 } ?: 1
            return (pred.peakMgdl - baseline) / minutes
        }

        fun resetResult() {
            _uiState.update { FoodComparisonUiState() }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        fun clearMealError() {
            _uiState.update { it.copy(mealError = null) }
        }

        private fun FoodItem.toCompareItem() =
            FoodCompareItem(
                foodId = id,
                foodName = name,
                carbsG = carbs,
                proteinG = protein,
                fatG = fat,
                kcal = calories,
                sugarG = sugar,
                giScore = gi.takeIf { it > 0 },
            )
    }
