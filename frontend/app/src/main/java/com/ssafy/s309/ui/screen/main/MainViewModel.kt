package com.ssafy.s309.ui.screen.main

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.repository.HealthRepository
import com.ssafy.s309.data.repository.UserRepository
import com.ssafy.s309.feature.glucofit.glucose.GlucoseSimulator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        private val simBuffer = ArrayDeque<GlucoseReading>()

        init {
            loadDashboard()
            observeBleConnection()
            observeGlucoseStream()
            observeGlucoseAlerts()
            registerPendingFcmToken()
            GlucoseSimulator.start(context)
            observeSimulatorStream()
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
                        GlucoseSimulator.stop()
                        GlucoseSimulator.start(context, diabetesType)
                        val low = settings.targetLow ?: return@onSuccess
                        val high = settings.targetHigh ?: return@onSuccess
                        healthRepository.updateAlertThresholds(low, high)
                    }
            }
        }

        private fun observeGlucoseAlerts() {
            viewModelScope.launch {
                healthRepository.glucoseAlertStream.collect { alert ->
                    // 즉시 반영 (팝업 없이 배너에만 쌓이도록 selectedNotification 세팅 안 함)
                    _uiState.update { state ->
                        state.copy(
                            notifications = listOf(alert) + state.notifications,
                        )
                    }
                    // BE에서 재조회 → displayTrace + 정확한 alertType(messageType) 획득
                    val refreshed = healthRepository.getNotifications()
                    // 로컬에서 이미 읽음 처리한 알림은 서버 응답으로 덮어쓰지 않음 (race condition 방지)
                    val locallyReadIds =
                        _uiState.value.notifications
                            .filter { !it.isUnread }.map { it.id }.toSet()
                    _uiState.update {
                        it.copy(
                            notifications =
                                refreshed.map { n ->
                                    if (n.id in locallyReadIds) n.copy(isUnread = false) else n
                                },
                        )
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

        private fun observeSimulatorStream() {
            viewModelScope.launch {
                GlucoseSimulator.glucoseState.collect { value ->
                    value ?: return@collect
                    if (_uiState.value.isDeviceConnected) return@collect

                    val reading =
                        GlucoseReading(
                            timestampMillis = System.currentTimeMillis(),
                            valueMgDl = value.toInt(),
                        )
                    simBuffer.addLast(reading)
                    if (simBuffer.size > 50) simBuffer.removeFirst()

                    val series = simBuffer.toList()
                    val current = series.lastOrNull() ?: return@collect
                    val prev = if (series.size >= 2) series[series.lastIndex - 1] else null
                    val diff = prev?.let { current.valueMgDl - it.valueMgDl } ?: 0
                    val rate =
                        prev?.let {
                            val minutes = (current.timestampMillis - it.timestampMillis) / 60_000f
                            if (minutes > 0f) (current.valueMgDl - it.valueMgDl) / minutes else 0f
                        } ?: 0f

                    _uiState.update { state ->
                        state.copy(
                            glucoseSeries = series,
                            currentGlucoseMgDl = current.valueMgDl,
                            diffFromPrevious = diff,
                            trendRateMgDlPerMin = rate,
                        )
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
            // FCM 미수신 단말도 패널 열 때 항상 최신 알림 보이도록 강제 재조회
            viewModelScope.launch {
                val refreshed = healthRepository.getNotifications()
                val locallyReadIds =
                    _uiState.value.notifications
                        .filter { !it.isUnread }.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        notifications =
                            refreshed.map { n ->
                                if (n.id in locallyReadIds) n.copy(isUnread = false) else n
                            },
                    )
                }
            }
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
                healthRepository.markAlertRead(selected.id)
            }
        }

        fun clearAllNotifications() {
            _uiState.update { it.copy(notifications = emptyList()) }
        }

        fun markAllNotificationsRead() {
            _uiState.update { it.copy(notifications = it.notifications.map { n -> n.copy(isUnread = false) }) }
            NotificationManagerCompat.from(context).cancelAll()
            viewModelScope.launch {
                healthRepository.markAllAlertsRead()
            }
        }

        fun sendMealReply(
            userReply: String,
            displayLabel: String,
        ) {
            viewModelScope.launch {
                healthRepository.sendPostMealReply(userReply, displayLabel)
            }
        }

        override fun onCleared() {
            super.onCleared()
            GlucoseSimulator.stop()
        }
    }
