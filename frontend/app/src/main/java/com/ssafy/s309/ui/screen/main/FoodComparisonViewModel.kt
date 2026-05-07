package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.FoodCompareItem
import com.ssafy.s309.data.model.GlucoseCompareRequest
import com.ssafy.s309.data.model.GlucoseCompareResponse
import com.ssafy.s309.data.model.MealCreateRequest
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
)

@HiltViewModel
class FoodComparisonViewModel
    @Inject
    constructor(
        private val predictRepository: PredictRepository,
        private val healthRepository: HealthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FoodComparisonUiState())
        val uiState: StateFlow<FoodComparisonUiState> = _uiState.asStateFlow()

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
                            it.copy(isLoading = false, result = response)
                        }
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
            mealHour: Int,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, mealError = null) }
                val recordedAt =
                    LocalDateTime.now()
                        .withHour(mealHour).withMinute(0).withSecond(0).withNano(0)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                healthRepository.createMealRecord(MealCreateRequest(foodId = food.id.toInt(), recordedAt = recordedAt))
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false, mealRecorded = true) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, mealError = e.message ?: "식사 기록 실패") }
                    }
            }
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
