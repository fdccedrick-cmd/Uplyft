package com.example.uplyft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.data.remote.firebase.CommentFirebaseSource
import com.example.uplyft.domain.model.Comment
import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import com.example.uplyft.utils.Constants.POSTS_COLLECTION
import com.google.firebase.firestore.FieldValue

class CommentViewModel(application: Application) : AndroidViewModel(application) {

    private val commentSource = CommentFirebaseSource()
    private val db = AppDatabase.getInstance(application)

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _addState = MutableStateFlow<Resource<Comment>?>(null)
    val addState: StateFlow<Resource<Comment>?> = _addState.asStateFlow()

    private var currentPostId: String? = null
    private var listenerJobActive = false

    /* ================= LOAD ================= */

    fun loadComments(postId: String) {
        if (currentPostId != postId) {
            currentPostId = postId
            listenerJobActive = false
            _comments.value = emptyList()
        }

        if (listenerJobActive) return
        listenerJobActive = true

        viewModelScope.launch {
            try {
                commentSource.observeComments(postId).collect { firestoreList ->
                    val pending = _comments.value.filter { it.isPending }

                    _comments.value = (firestoreList + pending)
                        .distinctBy { it.commentId }
                        .sortedBy { it.createdAt }
                }
            } catch (_: Exception) {
                listenerJobActive = false
            }
        }
    }

    /* ================= ADD ================= */

    fun addComment(postId: String, text: String, parentId: String = "") {
        if (text.isBlank()) return

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModelScope.launch {
            _addState.value = Resource.Loading

            try {
                val user = getUser(uid)

                val tempId = "temp_${System.currentTimeMillis()}"

                val pending = Comment(
                    commentId = tempId,
                    postId = postId,
                    userId = uid,
                    username = user?.username?.ifEmpty { user.fullName } ?: "",
                    userImage = user?.profileImageUrl ?: "",
                    text = text.trim(),
                    parentId = parentId,
                    isPending = true,
                    createdAt = System.currentTimeMillis()
                )

                // ✅ optimistic update
                _comments.update { it + pending }

                val saved = withContext(Dispatchers.IO) {
                    commentSource.addComment(pending.copy(isPending = false))
                }

                // remove temp
                _comments.update { list ->
                    list.filterNot { it.commentId == tempId }
                }

                _addState.value = Resource.Success(saved)

            } catch (e: Exception) {
                _comments.update { list -> list.filterNot { it.isPending } }
                _addState.value = Resource.Error(e.message ?: "Failed to post")
            }
        }
    }

    /* ================= GIF ================= */

    fun addGifComment(postId: String, gifUrl: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModelScope.launch {
            _addState.value = Resource.Loading

            try {
                val user = getUser(uid)
                val tempId = "temp_${System.currentTimeMillis()}"

                val pending = Comment(
                    commentId = tempId,
                    postId = postId,
                    userId = uid,
                    username = user?.username ?: "",
                    userImage = user?.profileImageUrl ?: "",
                    gifUrl = gifUrl,
                    isGif = true,
                    isPending = true,
                    createdAt = System.currentTimeMillis()
                )

                _comments.update { it + pending }

                val saved = withContext(Dispatchers.IO) {
                    commentSource.addComment(pending.copy(isPending = false))
                }

                _comments.update { it.filterNot { c -> c.commentId == tempId } }

                _addState.value = Resource.Success(saved)

            } catch (e: Exception) {
                _comments.update { it.filterNot { c -> c.isPending } }
                _addState.value = Resource.Error("Failed GIF")
            }
        }
    }

    /* ================= LIKE ================= */

    fun toggleCommentLike(comment: Comment) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ref = FirebaseFirestore.getInstance()
                        .collection(POSTS_COLLECTION)
                        .document(comment.postId)
                        .collection("comments")
                        .document(comment.commentId)

                    val likeRef = ref.collection("likes").document(uid)
                    val exists = likeRef.get().await().exists()

                    if (exists) {
                        likeRef.delete().await()
                        ref.update("likesCount", FieldValue.increment(-1)).await()
                    } else {
                        likeRef.set(mapOf("likedAt" to System.currentTimeMillis())).await()
                        ref.update("likesCount", FieldValue.increment(1)).await()
                    }
                }
            } catch (_: Exception) {}
        }
    }

    /* ================= DELETE ================= */

    fun deleteComment(postId: String, commentId: String) {
        viewModelScope.launch {
            _comments.update { it.filterNot { c -> c.commentId == commentId } }

            try {
                withContext(Dispatchers.IO) {
                    commentSource.deleteComment(postId, commentId)
                }
            } catch (_: Exception) {}
        }
    }

    /* ================= HELPERS ================= */

    private suspend fun getUser(uid: String): User? {
        return withContext(Dispatchers.IO) {
            db.userDao().getUserById(uid)?.toDomain()
                ?: FirebaseFirestore.getInstance()
                    .collection(USERS_COLLECTION)
                    .document(uid)
                    .get()
                    .await()
                    .toObject(User::class.java)
                    ?.copy(uid = uid)
        }
    }
}