package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import com.ssafy.s309.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MyAccountUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val email: String = "",
    val name: String = "",
    val age: Int? = null,
    val phone: String = "",
    val guardian: String = "",
    val error: String? = null,
)

@HiltViewModel
class MyAccountViewModel
    @Inject
    constructor(
        private val userRepository: UserRepository,
        private val tokenManager: TokenManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(MyAccountUiState())
        val uiState: StateFlow<MyAccountUiState> = _uiState.asStateFlow()

        init {
            loadProfile()
        }

        fun loadProfile() {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true, error = null) }

                val localEmail = tokenManager.getEmail() ?: ""

                userRepository.getSettings()
                    .onSuccess { settings ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                email = localEmail,
                                name = settings.name ?: "",
                                age = settings.age,
                                phone = settings.phone ?: "",
                            )
                        }
                    }
                    .onFailure {
                        _uiState.update { it.copy(isLoading = false, email = localEmail) }
                    }

                userRepository.getGuardians()
                    .onSuccess { guardians ->
                        // priority=0이 주 보호자; name은 서버 응답에 없으므로 guardianId로 표시
                        val primary = guardians.minByOrNull { it.priority }
                        val label = primary?.name?.ifBlank { null } ?: primary?.let { "보호자 #${it.guardianId}" } ?: ""
                        _uiState.update { it.copy(guardian = label) }
                    }
            }
        }

        fun saveProfile(
            name: String,
            age: Int?,
            phone: String,
        ) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSaving = true, error = null) }
                userRepository.updateSettings(UserSettingsUpdateRequest(name = name, age = age, phone = phone))
                    .onSuccess { settings ->
                        _uiState.update {
                            it.copy(
                                isSaving = false,
                                saveSuccess = true,
                                name = settings.name ?: "",
                                age = settings.age,
                                phone = settings.phone ?: "",
                            )
                        }
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
