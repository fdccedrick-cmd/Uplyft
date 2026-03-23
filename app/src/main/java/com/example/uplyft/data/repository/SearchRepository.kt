package com.example.uplyft.data.repository

import com.example.uplyft.data.local.dao.SearchHistoryDao
import com.example.uplyft.data.local.entity.SearchHistoryEntity
import com.example.uplyft.data.remote.firebase.UserFirebaseSource
import com.example.uplyft.data.remote.firebase.AuthFirebaseSource
import com.example.uplyft.domain.model.User
import kotlinx.coroutines.flow.Flow

class SearchRepository(
    private val userFirebaseSource: UserFirebaseSource,
    private val authFirebaseSource: AuthFirebaseSource,
    private val searchHistoryDao: SearchHistoryDao
) {

    suspend fun searchUsers(query: String): List<User> {
        return try {
            val currentUserId = authFirebaseSource.getCurrentUserId()
            userFirebaseSource.searchUsers(query, currentUserId)
        } catch (e: Exception) {
            // Return empty list on error
            emptyList()
        }
    }

    suspend fun addToSearchHistory(user: User) {
        val searchHistory = SearchHistoryEntity(
            userId = user.uid,
            username = user.username,
            fullName = user.fullName,
            profileImageUrl = user.profileImageUrl
        )
        searchHistoryDao.insertSearchHistory(searchHistory)
    }

    fun getSearchHistory(): Flow<List<SearchHistoryEntity>> {
        return searchHistoryDao.getAllSearchHistory()
    }

    suspend fun removeFromSearchHistory(userId: String) {
        searchHistoryDao.deleteSearchHistory(userId)
    }

    suspend fun clearAllSearchHistory() {
        searchHistoryDao.clearAllSearchHistory()
    }
}

