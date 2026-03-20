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

    // Instagram's approach: Track which comments are being synced to Firestore
    private val syncingLikes = mutableSetOf<String>()


    /* ================= LOAD ================= */

    fun loadComments(postId: String) {
        if (currentPostId != postId) {
            currentPostId = postId
            listenerJobActive = false
            _comments.value = emptyList()
            syncingLikes.clear()
        }

        if (listenerJobActive) return
        listenerJobActive = true

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        viewModelScope.launch {
            try {
                commentSource.observeComments(postId, currentUserId).collect { firestoreList ->
                    val pending = _comments.value.filter { it.isPending }
                    val current = _comments.value

                    // Instagram's approach: Only update comments that aren't being synced
                    val updated = firestoreList.map { firestore ->
                        if (syncingLikes.contains(firestore.commentId)) {
                            // Keep local optimistic state while syncing
                            current.find { it.commentId == firestore.commentId } ?: firestore
                        } else {
                            // Use Firestore data
                            firestore
                        }
                    }

                    _comments.value = (updated + pending)
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

        // Instagram's approach: Calculate new state based on CURRENT UI state
        val currentComment = _comments.value.find { it.commentId == comment.commentId } ?: comment
        val newIsLiked = !currentComment.isLiked
        val newCount = if (newIsLiked) currentComment.likesCount + 1 else currentComment.likesCount - 1

        // Mark as syncing
        syncingLikes.add(comment.commentId)

        // Update UI immediately
        _comments.value = _comments.value.map { c ->
            if (c.commentId == comment.commentId) {
                c.copy(isLiked = newIsLiked, likesCount = newCount)
            } else c
        }

        // Sync to Firestore in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val ref = FirebaseFirestore.getInstance()
                    .collection(POSTS_COLLECTION)
                    .document(comment.postId)
                    .collection("comments")
                    .document(comment.commentId)

                val likeRef = ref.collection("likes").document(uid)

                if (newIsLiked) {
                    likeRef.set(mapOf("likedAt" to System.currentTimeMillis())).await()
                    ref.update("likesCount", FieldValue.increment(1)).await()
                } else {
                    likeRef.delete().await()
                    ref.update("likesCount", FieldValue.increment(-1)).await()
                }

                // Only remove from syncing after successful sync
                kotlinx.coroutines.delay(500) // Small delay for Firestore propagation
                syncingLikes.remove(comment.commentId)

            } catch (_: Exception) {
                // On error, remove from syncing and revert
                syncingLikes.remove(comment.commentId)

                withContext(Dispatchers.Main) {
                    _comments.value = _comments.value.map { c ->
                        if (c.commentId == comment.commentId) {
                            c.copy(isLiked = currentComment.isLiked, likesCount = currentComment.likesCount)
                        } else c
                    }
                }
            }
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

