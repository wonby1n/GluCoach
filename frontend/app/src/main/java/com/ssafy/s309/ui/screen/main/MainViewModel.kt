package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.repository.HealthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 메인 화면 ViewModel.
 *
 * 현재는 [HealthRepository] 가 mock 데이터를 반환한다. BE 연동 시 Repository 내부만 교체되며
 * 이 ViewModel 은 변경이 불필요하다.
 */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val healthRepository: HealthRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        init {
            loadDashboard()
        }

        /** 메인 화면 초기/갱신 로드. 풀-투-리프레시 등 재사용 가능. */
        fun loadDashboard() {
            viewModelScope.launch {
                val series = healthRepository.getRecentGlucose()
                val range = healthRepository.getGlucoseTargetRange()
                val meals = healthRepository.getTodayMeals()
                val summary = healthRepository.getTodaySummary()
                val notifications = healthRepository.getNotifications()

                val current = series.lastOrNull()?.valueMgDl
                val diff =
                    if (series.size >= 2 && current != null) {
                        current - series[series.lastIndex - 1].valueMgDl
                    } else {
                        0
                    }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        currentGlucoseMgDl = current,
                        diffFromPrevious = diff,
                        glucoseSeries = series,
                        glucoseRange = range,
                        meals = meals,
                        summary = summary,
                        notifications = notifications,
                    )
                }
            }
        }

        fun openNotificationPanel() {
            _uiState.update { it.copy(isNotificationPanelOpen = true) }
        }

        fun closeNotificationPanel() {
            _uiState.update { it.copy(isNotificationPanelOpen = false) }
        }

        fun selectNotification(item: com.ssafy.s309.data.model.NotificationItem) {
            _uiState.update { it.copy(selectedNotification = item) }
        }

        fun dismissNotificationDetail() {
            val selected =
                _uiState.value.selectedNotification ?: run {
                    _uiState.update { it.copy(selectedNotification = null) }
                    return
                }
            _uiState.update { state ->
                state.copy(
                    selectedNotification = null,
                    notifications =
                        state.notifications.map {
                            if (it.id == selected.id) it.copy(isUnread = false) else it
                        },
                )
            }
        }

        fun clearAllNotifications() {
            _uiState.update { it.copy(notifications = emptyList()) }
        }
    }
