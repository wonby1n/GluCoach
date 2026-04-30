package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.UserSettings
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import com.ssafy.s309.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isLoading: Boolean = true,
    val settings: UserSettings? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(SettingsUiState())
        val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

        init {
            loadSettings()
        }

        fun loadSettings() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                userRepository.getSettings()
                    .onSuccess { settings ->
                        _uiState.update { it.copy(isLoading = false, settings = settings) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
            }
        }

        fun saveSettings(request: UserSettingsUpdateRequest) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, saveSuccess = false, error = null) }
                userRepository.updateSettings(request)
                    .onSuccess { updated ->
                        _uiState.update { it.copy(isSaving = false, saveSuccess = true, settings = updated) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isSaving = false, error = e.message) }
                    }
            }
        }

        fun clearSaveSuccess() {
            _uiState.update { it.copy(saveSuccess = false) }
        }
    }
