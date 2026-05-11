package com.ssafy.s309.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.local.TokenManager
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import com.ssafy.s309.data.repository.AuthRepository
import com.ssafy.s309.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()

    object Loading : AuthUiState()

    data class LoginSuccess(val isNewUser: Boolean = false) : AuthUiState()

    object LogoutSuccess : AuthUiState()

    object WithdrawSuccess : AuthUiState()

    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val userRepository: UserRepository,
        private val tokenManager: TokenManager,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        init {
            viewModelScope.launch {
                tokenManager.sessionExpiredFlow.collect {
                    _uiState.value = AuthUiState.LogoutSuccess
                }
            }
        }

        private val _autoLoginResult = MutableStateFlow<Boolean?>(null)
        val autoLoginResult: StateFlow<Boolean?> = _autoLoginResult.asStateFlow()

        val isLoggedIn: Boolean get() = authRepository.isLoggedIn()

        val userEmail: String get() = authRepository.getUserEmail() ?: ""

        fun tryAutoLogin() {
            viewModelScope.launch {
                val refreshResult = async { authRepository.refresh() }
                delay(1500L)
                _autoLoginResult.value = refreshResult.await().isSuccess
            }
        }

        fun login(
            email: String,
            password: String,
        ) {
            viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                authRepository.login(email, password)
                    .onSuccess { _uiState.value = AuthUiState.LoginSuccess() }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "로그인 실패") }
            }
        }

        var pendingEmail = ""
            private set
        var pendingPassword = ""
            private set
        var pendingName = ""
            private set
        var pendingPhone = ""
            private set
        var pendingAge = ""
            private set
        var pendingHeight = ""
            private set
        var pendingWeight = ""
            private set

        fun saveSignupData(
            email: String,
            password: String,
            name: String,
            phone: String,
        ) {
            pendingEmail = email
            pendingPassword = password
            pendingName = name
            pendingPhone = phone
        }

        fun saveHealthData(
            age: String,
            height: String,
            weight: String,
        ) {
            pendingAge = age
            pendingHeight = height
            pendingWeight = weight
        }

        fun clearPendingData() {
            pendingEmail = ""
            pendingPassword = ""
            pendingName = ""
            pendingPhone = ""
            pendingAge = ""
            pendingHeight = ""
            pendingWeight = ""
        }

        fun performPendingSignup() {
            viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                authRepository.signup(pendingEmail, pendingPassword, pendingName, pendingPhone)
                    .onSuccess {
                        val settingsRequest =
                            UserSettingsUpdateRequest(
                                name = pendingName.ifBlank { null },
                                age = pendingAge.toIntOrNull(),
                                phone = pendingPhone.ifBlank { null },
                                height = pendingHeight.toFloatOrNull(),
                                weight = pendingWeight.toFloatOrNull(),
                            )
                        userRepository.updateSettings(settingsRequest)
                        clearPendingData()
                        _uiState.value = AuthUiState.LoginSuccess(isNewUser = true)
                    }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "회원가입 실패") }
            }
        }

        fun logout() {
            viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                authRepository.logout()
                    .onSuccess {
                        clearPendingData()
                        _uiState.value = AuthUiState.LogoutSuccess
                    }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "로그아웃 실패") }
            }
        }

        fun withdraw(password: String) {
            viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                android.util.Log.d("AuthVM", "withdraw called")
                authRepository.withdraw(password)
                    .onSuccess {
                        android.util.Log.d("AuthVM", "withdraw success")
                        clearPendingData()
                        _uiState.value = AuthUiState.WithdrawSuccess
                    }
                    .onFailure {
                        android.util.Log.e("AuthVM", "withdraw failed: ${it.message}", it)
                        _uiState.value = AuthUiState.Error(it.message ?: "회원 탈퇴 실패")
                    }
            }
        }

        fun resetState() {
            _uiState.value = AuthUiState.Idle
        }
    }
