package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.WeeklyReportResponse
import com.ssafy.s309.data.repository.FoodRepository
import com.ssafy.s309.data.repository.WeeklyReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AIReportViewModel
    @Inject
    constructor(
        private val repository: WeeklyReportRepository,
        private val foodRepository: FoodRepository,
    ) : ViewModel() {
        sealed class UiState {
            data object Loading : UiState()

            data class Success(
                val reports: List<WeeklyReportResponse>,
                val gradeMap: Map<Int, String>,
                val selectedIndex: Int = 0,
            ) : UiState() {
                val current: WeeklyReportResponse get() = reports[selectedIndex]
                val hasPrevious: Boolean get() = selectedIndex < reports.lastIndex
                val hasNext: Boolean get() = selectedIndex > 0
            }

            data class Error(val message: String) : UiState()
        }

        private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        init {
            load()
        }

        fun load() {
            viewModelScope.launch {
                _uiState.value = UiState.Loading
                val reportsDeferred = async { repository.getWeeklyReports() }
                val gradesDeferred = async { foodRepository.getFoodGrades() }

                val reports =
                    reportsDeferred.await().getOrElse {
                        _uiState.value = UiState.Error(it.message ?: "보고서를 불러올 수 없어요")
                        return@launch
                    }

                if (reports.isEmpty()) {
                    _uiState.value = UiState.Error("아직 생성된 주간 보고서가 없어요")
                    return@launch
                }

                val gradeMap =
                    gradesDeferred.await()
                        .getOrDefault(emptyList())
                        .associate { it.foodId to it.grade }

                _uiState.value = UiState.Success(reports, gradeMap)
            }
        }

        fun showPrevious() {
            val s = _uiState.value as? UiState.Success ?: return
            if (s.hasPrevious) _uiState.value = s.copy(selectedIndex = s.selectedIndex + 1)
        }

        fun showNext() {
            val s = _uiState.value as? UiState.Success ?: return
            if (s.hasNext) _uiState.value = s.copy(selectedIndex = s.selectedIndex - 1)
        }

        fun getPdfUrl(
            id: Int,
            onSuccess: (String) -> Unit,
            onError: (String) -> Unit,
        ) {
            viewModelScope.launch {
                repository.getPdfUrl(id)
                    .onSuccess(onSuccess)
                    .onFailure { onError(it.message ?: "PDF를 가져올 수 없어요") }
            }
        }
    }
