package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val healthRepository: HealthRepository,
        private val userRepository: UserRepository,
        private val tokenManager: TokenManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        init {
            loadDashboard()
            observeBleConnection()
            observeGlucoseStream()
            observeGlucoseAlerts()
            registerPendingFcmToken()
        }

        fun loadDashboard() {
            viewModelScope.launch {
                val range = healthRepository.getGlucoseTargetRange()
                val summary = healthRepository.getTodaySummary()
                val notifications = healthRepository.getNotifications()

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        glucoseRange = range,
                        summary = summary,
                        notifications = notifications,
                    )
                }

                userRepository.getSettings()
                    .onSuccess { settings ->
                        val diabetesType = settings.diabetesType ?: "NONE"
                        _uiState.update { s ->
                            s.copy(diabetesType = diabetesType)
                        }
                        val low = settings.targetLow ?: return@onSuccess
                        val high = settings.targetHigh ?: return@onSuccess
                        healthRepository.updateAlertThresholds(low, high)
                    }
            }
        }

        private fun observeGlucoseAlerts() {
            viewModelScope.launch {
                healthRepository.glucoseAlertStream.collect { alert ->
                    _uiState.update { state ->
                        state.copy(notifications = listOf(alert) + state.notifications)
                    }
                }
            }
        }

        private fun registerPendingFcmToken() {
            val token = tokenManager.getFcmToken() ?: return
            viewModelScope.launch {
                userRepository.registerFcmToken(token)
            }
        }

        private fun observeBleConnection() {
            viewModelScope.launch {
                healthRepository.bleConnectionState.collect { bleState ->
                    val connected = bleState is BleConnectionState.Connected
                    _uiState.update { state ->
                        if (connected) {
                            simBuffer.clear()
                            state.copy(isDeviceConnected = true)
                        } else {
                            state.copy(
                                isDeviceConnected = false,
                                currentGlucoseMgDl = null,
                                diffFromPrevious = 0,
                            )
                        }
                    }
                }
            }
        }

        private fun observeGlucoseStream() {
            viewModelScope.launch {
                healthRepository.glucoseHistory.collect { history ->
                    val current = history.lastOrNull() ?: return@collect
                    val prev = if (history.size >= 2) history[history.lastIndex - 1] else null
                    val diff = prev?.let { current.valueMgDl - it.valueMgDl } ?: 0
                    val rate =
                        prev?.let {
                            val minutes = (current.timestampMillis - it.timestampMillis) / 60_000f
                            if (minutes > 0f) (current.valueMgDl - it.valueMgDl) / minutes else 0f
                        } ?: 0f
                    _uiState.update { state ->
                        state.copy(
                            glucoseSeries = history,
                            currentGlucoseMgDl = current.valueMgDl,
                            diffFromPrevious = diff,
                            trendRateMgDlPerMin = rate,
                        )
                    }
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
            viewModelScope.launch {
                healthRepository.markAlertRead(selected.id.toInt())
            }
        }

        fun clearAllNotifications() {
            _uiState.update { it.copy(notifications = emptyList()) }
        }

        override fun onCleared() {
            super.onCleared()
        }

        // [DEBUG_KIKI_TEST] 배포 전 삭제
        fun debugSetGlucose(
            mgDl: Int,
            rateMgDlPerMin: Float,
        ) {
            _uiState.update {
                it.copy(
                    currentGlucoseMgDl = mgDl,
                    trendRateMgDlPerMin = rateMgDlPerMin,
                    diffFromPrevious = (rateMgDlPerMin * 5).toInt(),
                )
            }
        }
        // [/DEBUG_KIKI_TEST]
    }
