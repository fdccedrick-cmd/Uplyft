package com.example.uplyft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.remote.cloudinary.CloudinaryService
import com.example.uplyft.data.remote.firebase.PostFirebaseSource
import com.example.uplyft.data.repository.PostRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import com.example.uplyft.domain.model.Post
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.example.uplyft.utils.PostUploadState
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class PostViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = AppDatabase.getInstance(application)
    private val cloudinary = CloudinaryService(application.applicationContext)
    private val firebaseSource = PostFirebaseSource()
    private val repository     = PostRepository(db.postDao(), firebaseSource, cloudinary)

    // Feed from Room — auto-updates
    val posts: StateFlow<List<Post>> = repository
        .observePosts()
        .stateIn(
            scope        = viewModelScope,
            started      = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uploadState = MutableStateFlow<PostUploadState?>(null)
    val uploadState: StateFlow<PostUploadState?> = _uploadState

    private val _feedState = MutableStateFlow<FeedState>(FeedState.Loading)
    val feedState: StateFlow<FeedState> = _feedState

    // Pagination
    private var lastDocument: DocumentSnapshot? = null
    private val pageSize = 10
    private var isLoadingMore = false

    init {
        viewModelScope.launch {
            loadFeed()
        }
    }

    fun loadFeed() {
        viewModelScope.launch {
            _feedState.value = FeedState.Loading
            try {
                repository.refreshPosts(limit = pageSize)
                _feedState.value = FeedState.Success
            } catch (e: Exception) {
                _feedState.value = FeedState.Error(e.message ?: "Failed to load feed")
            }
        }
    }

    fun loadMorePosts() {
        if (isLoadingMore) return
        viewModelScope.launch {
            isLoadingMore = true
            try {
                repository.loadMorePosts(limit = pageSize)
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun toggleLike(post: Post) {
        viewModelScope.launch {
            repository.toggleLike(post)
        }
    }

    fun createPost(imageUri: Uri, caption: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            repository.createPost(
                imageUri   = imageUri,
                caption    = caption,
                userId     = userId,
                onProgress = { _uploadState.value = it }
            )
        }
    }
    fun getUserPosts(userId: String): Flow<List<Post>> {
        return repository.observeUserPosts(userId)
    }
}

// Feed state
sealed class FeedState {
    object Loading : FeedState()
    object Success : FeedState()
    data class Error(val message: String) : FeedState()
}
