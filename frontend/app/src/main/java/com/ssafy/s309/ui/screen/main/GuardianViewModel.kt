package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.GuardianCreateRequest
import com.ssafy.s309.data.model.GuardianItem
import com.ssafy.s309.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GuardianUiState(
    val isLoading: Boolean = true,
    val guardians: List<GuardianItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class GuardianViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(GuardianUiState())
        val uiState: StateFlow<GuardianUiState> = _uiState.asStateFlow()

        init {
            loadGuardians()
        }

        fun loadGuardians() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }
                userRepository.getGuardians()
                    .onSuccess { list ->
                        _uiState.update { it.copy(isLoading = false, guardians = list) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
            }
        }

        fun createGuardian(
            name: String,
            phone: String,
            relation: String?,
            isPrimary: Boolean,
        ) {
            viewModelScope.launch {
                userRepository.createGuardian(GuardianCreateRequest(name, phone, relation?.ifBlank { null }, isPrimary))
                    .onSuccess { loadGuardians() }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            }
        }

        fun updateGuardian(
            guardianId: String,
            name: String,
            phone: String,
            relation: String?,
            isPrimary: Boolean,
        ) {
            viewModelScope.launch {
                userRepository.updateGuardian(guardianId, GuardianCreateRequest(name, phone, relation?.ifBlank { null }, isPrimary))
                    .onSuccess { loadGuardians() }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            }
        }

        fun deleteGuardian(guardianId: String) {
            viewModelScope.launch {
                userRepository.deleteGuardian(guardianId)
                    .onSuccess { loadGuardians() }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
