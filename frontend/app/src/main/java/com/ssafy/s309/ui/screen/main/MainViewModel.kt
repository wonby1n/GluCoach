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
                    _uiState.update { it.copy(isDeviceConnected = bleState is BleConnectionState.Connected) }
                }
            }
        }

        private fun observeGlucoseStream() {
            viewModelScope.launch {
                healthRepository.glucoseStream.collect { reading ->
                    _uiState.update { state ->
                        val newSeries = (state.glucoseSeries + reading).takeLast(100)
                        val diff =
                            if (newSeries.size >= 2) {
                                reading.valueMgDl - newSeries[newSeries.lastIndex - 1].valueMgDl
                            } else {
                                0
                            }
                        state.copy(
                            glucoseSeries = newSeries,
                            currentGlucoseMgDl = reading.valueMgDl,
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

        fun clearAllNotifications() {
            _uiState.update { it.copy(notifications = emptyList()) }
        }
    }
