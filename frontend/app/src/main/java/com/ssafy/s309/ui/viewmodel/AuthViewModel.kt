package com.ssafy.s309.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
        val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

        val isLoggedIn: Boolean get() = authRepository.isLoggedIn()

        val userEmail: String get() = authRepository.getUserEmail() ?: ""

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

        fun signup(
            email: String,
            password: String,
            name: String,
            phone: String,
        ) {
            viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                authRepository.signup(email, password, name, phone)
                    .onSuccess { _uiState.value = AuthUiState.LoginSuccess(isNewUser = true) }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "회원가입 실패") }
            }
        }

        fun logout() {
            viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                authRepository.logout()
                    .onSuccess { _uiState.value = AuthUiState.LogoutSuccess }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "로그아웃 실패") }
            }
        }

        fun withdraw(password: String) {
            viewModelScope.launch {
                _uiState.value = AuthUiState.Loading
                authRepository.withdraw(password)
                    .onSuccess { _uiState.value = AuthUiState.WithdrawSuccess }
                    .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "회원 탈퇴 실패") }
            }
        }

        fun resetState() {
            _uiState.value = AuthUiState.Idle
        }
    }
