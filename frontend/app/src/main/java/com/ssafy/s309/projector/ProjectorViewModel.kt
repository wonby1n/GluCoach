package com.ssafy.s309.projector

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectorUiState(
    val piIp: String = "192.168.0.100",
    val statusMessage: String = "연결 안 됨",
    val isConnected: Boolean = false,
)

@HiltViewModel
class ProjectorViewModel
    @Inject
    constructor(
        private val client: ProjectorSocketClient,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(ProjectorUiState(piIp = client.piIp))
        val uiState: StateFlow<ProjectorUiState> = _uiState.asStateFlow()

        fun updateIp(ip: String) {
            client.piIp = ip
            _uiState.update { it.copy(piIp = ip) }
        }

        fun connect() {
            viewModelScope.launch {
                _uiState.update { it.copy(statusMessage = "연결 중...") }
                val ok = client.connect()
                _uiState.update {
                    it.copy(
                        isConnected = ok,
                        statusMessage = if (ok) "연결됨: ${client.piIp}" else "연결 실패 — IP 확인",
                    )
                }
            }
        }

        fun show() = dispatch("SHOW") { client.show() }

        fun hide() = dispatch("HIDE") { client.hide() }

        fun simulateMorning() =
            dispatch("[시뮬] 아침 브리핑") {
                client.briefing(sleepScore = 78, glucose = 105)
            }

        fun simulateHighGlucose() =
            dispatch("[시뮬] 고혈당 알림") {
                client.alert()
            }

        private fun dispatch(
            label: String,
            action: suspend () -> Boolean,
        ) {
            viewModelScope.launch {
                val ok = action()
                _uiState.update {
                    it.copy(statusMessage = if (ok) "$label 전송됨" else "$label 전송 실패")
                }
            }
        }
    }
