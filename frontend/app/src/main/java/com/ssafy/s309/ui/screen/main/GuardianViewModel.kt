package com.ssafy.s309.ui.screen.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.GuardianCreateRequest
import com.ssafy.s309.data.model.GuardianItem
import com.ssafy.s309.data.model.UserSearchResponse
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
    // 보호자 추가 — 1단계 검색 상태
    val isSearching: Boolean = false,
    val searchResult: UserSearchResponse? = null,
    val searchError: String? = null,
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
                    .onSuccess { list -> _uiState.update { it.copy(isLoading = false, guardians = list) } }
                    .onFailure { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
            }
        }

        /** 보호자 추가 1단계: 전화번호로 가입 사용자 검색 */
        fun searchUserByPhone(phone: String) {
            viewModelScope.launch {
                _uiState.update { it.copy(isSearching = true, searchResult = null, searchError = null) }
                userRepository.searchUser(phone)
                    .onSuccess { result -> _uiState.update { it.copy(isSearching = false, searchResult = result) } }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(isSearching = false, searchError = e.message ?: "사용자를 찾을 수 없습니다")
                        }
                    }
            }
        }

        /** 보호자 추가 2단계: 검색된 사용자를 보호자로 등록 */
        fun createGuardian(relation: String?) {
            val result = _uiState.value.searchResult ?: return
            viewModelScope.launch {
                userRepository.createGuardian(GuardianCreateRequest(result.userId, relation?.ifBlank { null }))
                    .onSuccess {
                        _uiState.update { it.copy(searchResult = null, searchError = null) }
                        loadGuardians()
                    }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            }
        }

        /** 보호자 관계(relation) 수정 */
        fun updateGuardian(
            item: GuardianItem,
            relation: String?,
        ) {
            viewModelScope.launch {
                // 백엔드 PUT /api/user/guardians/{id} 는 wardGuardianId(=item.id)와 guardianId(필수) 를 받음
                userRepository.updateGuardian(item.id, GuardianCreateRequest(item.guardianId, relation?.ifBlank { null }))
                    .onSuccess { loadGuardians() }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            }
        }

        fun deleteGuardian(item: GuardianItem) {
            viewModelScope.launch {
                userRepository.deleteGuardian(item.id)
                    .onSuccess { loadGuardians() }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
            }
        }

        fun clearSearchState() {
            _uiState.update { it.copy(searchResult = null, searchError = null, isSearching = false) }
        }

        fun clearError() {
            _uiState.update { it.copy(error = null) }
        }
    }
