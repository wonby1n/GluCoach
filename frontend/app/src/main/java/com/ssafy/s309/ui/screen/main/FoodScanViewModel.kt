package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.model.MealCreateRequest
import com.ssafy.s309.data.repository.FoodRepository
import com.ssafy.s309.data.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class FoodScanUiState(
    val searchQuery: String = "",
    val searchResults: List<FoodSearchItem> = emptyList(),
    val selectedFood: FoodSearchItem? = null,
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class FoodScanViewModel
    @Inject
    constructor(
        private val foodRepository: FoodRepository,
        private val healthRepository: HealthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FoodScanUiState())
        val uiState: StateFlow<FoodScanUiState> = _uiState.asStateFlow()

        private var photoFile: File? = null
        private var searchJob: Job? = null

        fun setPhotoFile(file: File?) {
            photoFile = file
        }

        fun searchFoods(query: String) {
            _uiState.update { it.copy(searchQuery = query) }
            searchJob?.cancel()
            if (query.isBlank()) {
                _uiState.update { it.copy(searchResults = emptyList()) }
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(300)
                    _uiState.update { it.copy(isSearching = true) }
                    foodRepository.searchFoods(query)
                        .onSuccess { results ->
                            _uiState.update { it.copy(searchResults = results, isSearching = false) }
                        }
                        .onFailure { e ->
                            _uiState.update { it.copy(isSearching = false, error = e.message) }
                        }
                }
        }

        fun selectFood(food: FoodSearchItem) {
            _uiState.update { it.copy(selectedFood = food, searchResults = emptyList(), searchQuery = food.name) }
        }

        fun clearSelection() {
            _uiState.update { it.copy(selectedFood = null) }
        }

        fun recordMeal() {
            val food = _uiState.value.selectedFood ?: return
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                val request =
                    MealCreateRequest(
                        foodId = food.id,
                        recordedAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    )
                healthRepository.createMealRecord(request, photoFile)
                    .onSuccess {
                        _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isSaving = false, error = e.message ?: "식사 기록 실패") }
                    }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }

        /** 탄수화물 기반 혈당 상승 추정 (예측 API 없이 영양 정보 기반) */
        fun estimatePredictedRise(food: FoodSearchItem): Int {
            val carbs = food.carbsG?.toInt() ?: 30
            val sugar = food.sugarG?.toInt() ?: 0
            return ((carbs + sugar * 0.5) * 0.45).toInt().coerceIn(5, 60)
        }
    }
