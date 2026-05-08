package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.repository.FoodRepository
import com.ssafy.s309.data.repository.MealRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject

data class FoodScanCandidate(
    val rank: Int,
    val foodId: Int,
    val name: String,
    val kcal: Double?,
    val carbsG: Double?,
    val proteinG: Double?,
    val fatG: Double?,
    val confidence: Float,
)

sealed class FoodScanState {
    object Idle : FoodScanState()

    data class Analyzing(val stage: Int) : FoodScanState() // 0=시작 1=AI인식 2=영양검색 3=완료

    data class Result(
        val candidates: List<FoodScanCandidate>,
        val isSaving: Boolean = false,
    ) : FoodScanState()

    data class Error(val message: String) : FoodScanState()

    object Saved : FoodScanState()
}

@HiltViewModel
class FoodScanViewModel
    @Inject
    constructor(
        private val foodRepository: FoodRepository,
        private val mealRepository: MealRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<FoodScanState>(FoodScanState.Idle)
        val state: StateFlow<FoodScanState> = _state.asStateFlow()

        fun analyze(photoFile: File) {
            if (_state.value !is FoodScanState.Idle) return
            viewModelScope.launch {
                _state.value = FoodScanState.Analyzing(0)

                // Stage 1: AI 음식 탐지
                _state.value = FoodScanState.Analyzing(1)
                val detectResult = foodRepository.detectFood(photoFile)
                if (detectResult.isFailure) {
                    _state.value =
                        FoodScanState.Error(
                            detectResult.exceptionOrNull()?.message ?: "음식 인식에 실패했습니다",
                        )
                    return@launch
                }
                val detections = detectResult.getOrNull()?.detections.orEmpty()
                if (detections.isEmpty()) {
                    _state.value = FoodScanState.Error("음식을 인식하지 못했습니다.\n다시 촬영해 주세요.")
                    return@launch
                }

                // Stage 2: 식약처 DB에서 영양 정보 검색
                _state.value = FoodScanState.Analyzing(2)
                val candidates = mutableListOf<FoodScanCandidate>()
                detections.take(3).forEachIndexed { idx, detection ->
                    val searchResult = foodRepository.searchFoods(detection.nameKo)
                    val foodItem: FoodSearchItem? = searchResult.getOrNull()?.firstOrNull()
                    if (foodItem != null) {
                        candidates.add(
                            FoodScanCandidate(
                                rank = idx + 1,
                                foodId = foodItem.id,
                                name = foodItem.name,
                                kcal = foodItem.kcal,
                                carbsG = foodItem.carbsG,
                                proteinG = foodItem.proteinG,
                                fatG = foodItem.fatG,
                                confidence = detection.confidence,
                            ),
                        )
                    }
                }

                // Stage 3: 완료
                _state.value = FoodScanState.Analyzing(3)
                delay(400)

                _state.value =
                    if (candidates.isEmpty()) {
                        FoodScanState.Error("영양 정보를 찾을 수 없습니다.\n다시 촬영해 주세요.")
                    } else {
                        FoodScanState.Result(candidates)
                    }
            }
        }

        fun saveMeal(
            candidate: FoodScanCandidate,
            photoFile: File,
            memo: String? = null,
            recordedAt: LocalDateTime = LocalDateTime.now(),
        ) {
            val current = _state.value as? FoodScanState.Result ?: return
            viewModelScope.launch {
                _state.value = current.copy(isSaving = true)
                val result =
                    mealRepository.createMeal(
                        foodId = candidate.foodId,
                        recordedAt = recordedAt,
                        photoFile = photoFile,
                        memo = memo?.takeIf { it.isNotBlank() },
                    )
                _state.value =
                    if (result.isSuccess) {
                        FoodScanState.Saved
                    } else {
                        current.copy(isSaving = false)
                    }
            }
        }

        fun resetError() {
            _state.value = FoodScanState.Idle
        }
    }
