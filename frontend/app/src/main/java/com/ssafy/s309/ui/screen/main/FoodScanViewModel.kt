package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.model.GlucosePrediction
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

    data class Analyzing(val stage: Int) : FoodScanState() // 0=시작 1=AI인식·매칭 2=영양보강 3=완료

    data class Result(
        val candidates: List<FoodScanCandidate>,
        // 사진 통합 예측에서 받아오는 식전 혈당 곡선 (top-1 OK 시에만 채워짐).
        val prediction: GlucosePrediction? = null,
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

        private val _manualSearchResults = MutableStateFlow<List<FoodSearchItem>>(emptyList())
        val manualSearchResults: StateFlow<List<FoodSearchItem>> = _manualSearchResults.asStateFlow()

        private val _manualPrediction = MutableStateFlow<GlucosePrediction?>(null)
        val manualPrediction: StateFlow<GlucosePrediction?> = _manualPrediction.asStateFlow()

        fun analyze(photoFile: File) {
            if (_state.value !is FoodScanState.Idle) return
            viewModelScope.launch {
                _state.value = FoodScanState.Analyzing(0)

                // Stage 1: BE 사진 통합 예측 — CV 인식 + foods 매칭(top-1) + 예측 곡선
                _state.value = FoodScanState.Analyzing(1)
                val predictResult = foodRepository.predictFromImage(photoFile)
                if (predictResult.isFailure) {
                    _state.value =
                        FoodScanState.Error(
                            predictResult.exceptionOrNull()?.message ?: "음식 인식에 실패했습니다",
                        )
                    return@launch
                }
                val response = predictResult.getOrNull()!!

                if (response.status == "LOW_CONFIDENCE") {
                    _state.value = FoodScanState.Error("음식을 인식하지 못했어요")
                    return@launch
                }

                // Stage 2: 후보 영양정보 보강
                // OK / PENDING_NUTRITION 시 BE 가 foodId/foodName 채움 → top-1 보강.
                // BE 이상으로 foodId 가 null 이면 detected[] 전체에 대해 search 로 fallback 처리.
                // 알 수 없는 신규 status 도 동일 분기로 forward-compatible.
                _state.value = FoodScanState.Analyzing(2)
                val candidates = mutableListOf<FoodScanCandidate>()
                val topConfidence = response.detected.firstOrNull()?.confidence ?: 0f
                val topFoodId = response.foodId

                if (topFoodId != null) {
                    val topDisplayName = response.detected.firstOrNull()?.nameKo ?: response.foodName.orEmpty()
                    // searchFoods 결과를 한 번만 받아 재사용 — 중복 호출 회피.
                    val results = foodRepository.searchFoods(response.foodName ?: topDisplayName).getOrNull().orEmpty()
                    // BE 가 정확 일치 row 를 결정해 보낸 topFoodId 와 일치하는 것만 사용.
                    // firstOrNull() 로 폴백하면 검색 1위(인기순)가 다른 음식이라 가짜 매칭 발생.
                    val foodItem: FoodSearchItem? =
                        results.firstOrNull { it.id == topFoodId }
                    if (foodItem != null) {
                        candidates.add(
                            FoodScanCandidate(
                                rank = 1,
                                foodId = foodItem.id,
                                name = foodItem.displayName ?: topDisplayName,
                                kcal = foodItem.kcal,
                                carbsG = foodItem.carbsG,
                                proteinG = foodItem.proteinG,
                                fatG = foodItem.fatG,
                                confidence = topConfidence,
                            ),
                        )
                    } else {
                        // PENDING_NUTRITION — 영양정보 없는 customized row. foodId 만 살려 등록 가능하게.
                        candidates.add(
                            FoodScanCandidate(
                                rank = 1,
                                foodId = topFoodId,
                                name = topDisplayName,
                                kcal = null,
                                carbsG = null,
                                proteinG = null,
                                fatG = null,
                                confidence = topConfidence,
                            ),
                        )
                    }
                }

                // top-1 이 위에서 처리됐으면 detected[1..2], 아니면 detected[0..2] 까지 search 로 보강.
                // 서로 다른 AI detection 이 같은 foods row 로 매칭될 수 있어(prototype DB 동의어 키 등)
                // foodId 기준 cross-detection dedup. 중복이면 다음 detection 으로 자리 양보.
                val remainingStart = if (topFoodId != null) 1 else 0
                val seenFoodIds = candidates.map { it.foodId }.toMutableSet()
                for (detection in response.detected.drop(remainingStart)) {
                    if (candidates.size >= 3) break
                    val foodItem: FoodSearchItem =
                        foodRepository.searchFoods(detection.nameKo).getOrNull()?.firstOrNull()
                            ?: continue
                    if (!seenFoodIds.add(foodItem.id)) continue
                    candidates.add(
                        FoodScanCandidate(
                            rank = candidates.size + 1,
                            foodId = foodItem.id,
                            name = foodItem.displayName ?: detection.nameKo,
                            kcal = foodItem.kcal,
                            carbsG = foodItem.carbsG,
                            proteinG = foodItem.proteinG,
                            fatG = foodItem.fatG,
                            confidence = detection.confidence,
                        ),
                    )
                }

                // Stage 3: 완료
                _state.value = FoodScanState.Analyzing(3)
                delay(400)

                _state.value = FoodScanState.Result(candidates = candidates, prediction = response.prediction)
            }
        }

        fun saveMeal(
            candidate: FoodScanCandidate,
            photoFile: File,
            memo: String? = null,
            recordedAt: LocalDateTime = LocalDateTime.now(),
        ) {
            val current = _state.value
            viewModelScope.launch {
                if (current is FoodScanState.Result) {
                    _state.value = current.copy(isSaving = true)
                }
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
                    } else if (current is FoodScanState.Result) {
                        current.copy(isSaving = false)
                    } else {
                        current
                    }
            }
        }

        fun searchManualFood(query: String) {
            if (query.isBlank()) {
                _manualSearchResults.value = emptyList()
                return
            }
            viewModelScope.launch {
                _manualSearchResults.value = foodRepository.searchFoods(query).getOrNull().orEmpty()
            }
        }

        fun clearManualSearch() {
            _manualSearchResults.value = emptyList()
        }

        fun fetchManualPrediction(item: FoodSearchItem) {
            _manualPrediction.value = null
            viewModelScope.launch {
                _manualPrediction.value = foodRepository.predictByFood(item).getOrNull()
            }
        }

        fun clearManualPrediction() {
            _manualPrediction.value = null
        }

        fun resetError() {
            _state.value = FoodScanState.Idle
        }

        fun reset() {
            _state.value = FoodScanState.Idle
            _manualPrediction.value = null
        }

        suspend fun searchFoodsDirect(query: String): List<FoodSearchItem> = foodRepository.searchFoods(query).getOrNull().orEmpty()

        suspend fun predictByFoodDirect(item: FoodSearchItem): GlucosePrediction? = foodRepository.predictByFood(item).getOrNull()
    }
