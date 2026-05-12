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

            /** 날짜 목록 화면 — 처음 진입 시 표시 */
            data class DateList(
                val reports: List<WeeklyReportResponse>,
                val gradeMap: Map<Int, String>,
                val isGenerating: Boolean = false,
            ) : UiState()

            /** 특정 주 리포트 상세 화면 */
            data class Detail(
                val reports: List<WeeklyReportResponse>,
                val gradeMap: Map<Int, String>,
                val selectedIndex: Int,
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

        /** 최초 진입 or 재시도 — 기존 리포트 목록을 불러와 DateList 상태로 전환 */
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

                val gradeMap =
                    gradesDeferred.await()
                        .getOrDefault(emptyList())
                        .associate { it.foodId to it.grade }

                _uiState.value = UiState.DateList(reports = reports, gradeMap = gradeMap)
            }
        }

        /** 새로 생성하기 버튼 — POST로 새 리포트 생성 후 목록을 갱신 */
        fun generateReport() {
            val current = _uiState.value as? UiState.DateList ?: return
            viewModelScope.launch {
                _uiState.value = current.copy(isGenerating = true)

                repository.generateReport().getOrElse {
                    _uiState.value = current.copy(isGenerating = false)
                    return@launch
                }

                val reportsDeferred = async { repository.getWeeklyReports() }
                val gradesDeferred = async { foodRepository.getFoodGrades() }

                val reports =
                    reportsDeferred.await().getOrElse {
                        _uiState.value = current.copy(isGenerating = false)
                        return@launch
                    }
                val gradeMap =
                    gradesDeferred.await()
                        .getOrDefault(emptyList())
                        .associate { it.foodId to it.grade }

                _uiState.value = UiState.DateList(reports = reports, gradeMap = gradeMap)
            }
        }

        /** 날짜 목록에서 특정 주 선택 → 상세 화면으로 전환 */
        fun selectReport(index: Int) {
            val current = _uiState.value as? UiState.DateList ?: return
            if (index !in current.reports.indices) return
            _uiState.value = UiState.Detail(current.reports, current.gradeMap, index)
        }

        /** 상세 화면에서 목록으로 돌아가기 */
        fun backToList() {
            val current = _uiState.value as? UiState.Detail ?: return
            _uiState.value = UiState.DateList(current.reports, current.gradeMap)
        }

        fun showPrevious() {
            val s = _uiState.value as? UiState.Detail ?: return
            if (s.hasPrevious) _uiState.value = s.copy(selectedIndex = s.selectedIndex + 1)
        }

        fun showNext() {
            val s = _uiState.value as? UiState.Detail ?: return
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
