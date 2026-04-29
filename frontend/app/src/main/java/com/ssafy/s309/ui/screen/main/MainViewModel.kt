package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.data.repository.HealthRepository
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        init {
            loadDashboard()
            observeBleConnection()
            observeGlucoseStream()
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
            }
        }

        private fun observeBleConnection() {
            viewModelScope.launch {
                healthRepository.bleConnectionState.collect { bleState ->
                    val connected = bleState is BleConnectionState.Connected
                    _uiState.update { state ->
                        if (connected) {
                            state.copy(isDeviceConnected = true)
                        } else {
                            state.copy(
                                isDeviceConnected = false,
                                glucoseSeries = emptyList(),
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
                    val diff =
                        if (history.size >= 2) {
                            current.valueMgDl - history[history.lastIndex - 1].valueMgDl
                        } else {
                            0
                        }
                    _uiState.update { state ->
                        state.copy(
                            glucoseSeries = history,
                            currentGlucoseMgDl = current.valueMgDl,
                            diffFromPrevious = diff,
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
        }

        fun clearAllNotifications() {
            _uiState.update { it.copy(notifications = emptyList()) }
        }
    }
