package com.ssafy.s309.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.data.repository.FoodRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FoodSearchViewModel
    @Inject
    constructor(
        private val foodRepository: FoodRepository,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        private val _results = MutableStateFlow<List<FoodSearchItem>>(emptyList())
        val results: StateFlow<List<FoodSearchItem>> = _results.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private var searchJob: Job? = null

        fun onQueryChanged(newQuery: String) {
            if (_query.value == newQuery) return
            _query.value = newQuery
            _error.value = null

            searchJob?.cancel()
            if (newQuery.isBlank()) {
                _results.value = emptyList()
                _isLoading.value = false
                return
            }

            searchJob =
                viewModelScope.launch {
                    delay(300)
                    _isLoading.value = true
                    foodRepository.searchFoods(newQuery)
                        .onSuccess { raw ->
                            _results.value =
                                raw.sortedWith(
                                    compareBy {
                                        when {
                                            it.name.equals(newQuery, ignoreCase = true) -> 0
                                            it.name.startsWith(newQuery, ignoreCase = true) -> 1
                                            else -> 2
                                        }
                                    },
                                )
                            _error.value = null
                        }
                        .onFailure {
                            _results.value = emptyList()
                            _error.value = it.message ?: "검색 실패"
                        }
                    _isLoading.value = false
                }
        }

        fun clearSearch() {
            searchJob?.cancel()
            _query.value = ""
            _results.value = emptyList()
            _isLoading.value = false
            _error.value = null
        }
    }
