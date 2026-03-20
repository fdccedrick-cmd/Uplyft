package com.example.uplyft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.domain.model.MentionUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.example.uplyft.utils.Constants.FOLLOWS_COLLECTION
import com.example.uplyft.utils.Constants.USERS_COLLECTION



// viewmodel/MentionViewModel.kt
class MentionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val firestore = FirebaseFirestore.getInstance()

    private val _suggestions = MutableStateFlow<List<MentionUser>>(emptyList())
    val suggestions: StateFlow<List<MentionUser>> = _suggestions

    fun searchMentions(query: String, currentUid: String) {
        if (query.isEmpty()) {
            _suggestions.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                val results = withContext(Dispatchers.IO) {
                    fetchMentionSuggestions(query, currentUid)
                }
                _suggestions.value = results
            } catch (e: Exception) {
                _suggestions.value = emptyList()
            }
        }
    }

    private suspend fun fetchMentionSuggestions(
        query     : String,
        currentUid: String
    ): List<MentionUser> {

        val followingSnap = firestore.collection(FOLLOWS_COLLECTION)
            .whereEqualTo("followerId", currentUid)
            .get().await()
        val followingIds = followingSnap.documents
            .mapNotNull { it.getString("followingId") }
            .filter { it.isNotEmpty() }

        // ✅ return empty if nobody followed — whereIn crashes on empty list
        if (followingIds.isEmpty()) return emptyList()

        val followersSnap = firestore.collection(FOLLOWS_COLLECTION)
            .whereEqualTo("followingId", currentUid)
            .get().await()
        val followerIds = followersSnap.documents
            .mapNotNull { it.getString("followerId") }
            .filter { it.isNotEmpty() }

        val mutualIds = followingIds.intersect(followerIds.toSet())

        val chunks     = followingIds.chunked(10)
        val userDocs   = mutableListOf<MentionUser>()

        chunks.forEach { chunk ->
            // ✅ skip empty chunks
            if (chunk.isEmpty()) return@forEach

            val snap = firestore.collection(USERS_COLLECTION)
                .whereIn("uid", chunk)
                .get().await()

            snap.documents.forEach { doc ->
                val username = doc.getString("username") ?: ""
                val fullName = doc.getString("fullName") ?: ""

                // ✅ filter by query — show all if query empty
                val matches = query.isEmpty() ||
                        username.startsWith(query, ignoreCase = true) ||
                        fullName.contains(query, ignoreCase = true)

                if (matches) {
                    userDocs.add(
                        MentionUser(
                            uid          = doc.id,
                            username     = username,
                            fullName     = fullName,
                            profileImage = doc.getString("profileImageUrl") ?: "",
                            isMutual     = doc.id in mutualIds
                        )
                    )
                }
            }
        }

        return userDocs.sortedWith(
            compareByDescending<MentionUser> { it.isMutual }
                .thenBy { it.username }
        )
    }
    fun clearSuggestions() {
        _suggestions.value = emptyList()
    }
}