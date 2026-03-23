package com.example.uplyft.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uplyft.data.local.entity.SearchHistoryEntity
import com.example.uplyft.data.repository.SearchRepository
import com.example.uplyft.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<SearchHistoryEntity>>(emptyList())
    val searchHistory: StateFlow<List<SearchHistoryEntity>> = _searchHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadSearchHistory()
    }

    private fun loadSearchHistory() {
        viewModelScope.launch {
            searchRepository.getSearchHistory().collect { history ->
                _searchHistory.value = history
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query

        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isLoading.value = false
            return
        }

        viewModelScope.launch {
            _isLoading.value = true

            // Shorter debounce for faster search (150ms instead of 300ms)
            kotlinx.coroutines.delay(150)

            if (_searchQuery.value == query) {
                android.util.Log.d("SearchViewModel", "Searching for: '$query'")
                val results = searchRepository.searchUsers(query)
                android.util.Log.d("SearchViewModel", "Found ${results.size} results")
                _searchResults.value = results
                _isLoading.value = false
            }
        }
    }

    fun onUserClicked(user: User) {
        viewModelScope.launch {
            searchRepository.addToSearchHistory(user)
        }
    }

    fun onRemoveFromHistory(userId: String) {
        viewModelScope.launch {
            searchRepository.removeFromSearchHistory(userId)
        }
    }

    fun onClearAllHistory() {
        viewModelScope.launch {
            searchRepository.clearAllSearchHistory()
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isLoading.value = false
    }
}


