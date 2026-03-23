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

    private val commentSource = CommentFirebaseSource(application.applicationContext)
    private val db = AppDatabase.getInstance(application)

    private val _comments = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> = _comments.asStateFlow()

    private val _addState = MutableStateFlow<Resource<Comment>?>(null)
    val addState: StateFlow<Resource<Comment>?> = _addState.asStateFlow()

    private var currentPostId: String? = null
    private var listenerJobActive = false

    // Instagram's REAL approach: Local cache of which comments are liked by current user
    // This is the source of truth for UI, not Firestore
    private val likedCommentIds = mutableSetOf<String>()
    private val syncingLikes = mutableSetOf<String>()


    /* ================= LOAD ================= */

    fun loadComments(postId: String) {
        if (currentPostId != postId) {
            currentPostId = postId
            listenerJobActive = false
            _comments.value = emptyList()
            likedCommentIds.clear()
            syncingLikes.clear()
        }

        if (listenerJobActive) return
        listenerJobActive = true

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

        viewModelScope.launch {
            try {
                var isFirstUpdate = true

                commentSource.observeComments(postId, currentUserId).collect { firestoreList ->
                    val pending = _comments.value.filter { it.isPending }

                    // On first update, populate likedCommentIds from ALL liked comments in Firestore
                    if (isFirstUpdate) {
                        isFirstUpdate = false
                        firestoreList.forEach { comment ->
                            if (comment.isLiked) {
                                likedCommentIds.add(comment.commentId)
                            }
                        }
                    }

                    // Apply local like state to ALL comments
                    val commentsWithLocalLikeState = firestoreList.map { firestore ->
                        val isLikedLocally = likedCommentIds.contains(firestore.commentId)

                        // Always use local like state for UI
                        firestore.copy(
                            isLiked = isLikedLocally,
                            likesCount = firestore.likesCount // Use Firestore count
                        )
                    }

                    _comments.value = (commentsWithLocalLikeState + pending)
                        .distinctBy { it.commentId }
                        .sortedBy { it.createdAt }
                }
            } catch (_: Exception) {
                listenerJobActive = false
            }
        }
    }

    /* ================= ADD ================= */

    fun addComment(postId: String, text: String) {
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

                // ✅ Update Room cache immediately with new comment count
                withContext(Dispatchers.IO) {
                    val currentPost = db.postDao().getPostById(postId)
                    if (currentPost != null) {
                        db.postDao().updateCommentCount(postId, currentPost.commentsCount + 1)
                    }
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

                // ✅ Update Room cache immediately with new comment count
                withContext(Dispatchers.IO) {
                    val currentPost = db.postDao().getPostById(postId)
                    if (currentPost != null) {
                        db.postDao().updateCommentCount(postId, currentPost.commentsCount + 1)
                    }
                }

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

        // Instagram's REAL approach: Use local cache as source of truth
        val isCurrentlyLiked = likedCommentIds.contains(comment.commentId)
        val newIsLiked = !isCurrentlyLiked

        // Update local cache immediately (source of truth)
        if (newIsLiked) {
            likedCommentIds.add(comment.commentId)
        } else {
            likedCommentIds.remove(comment.commentId)
        }

        val newCount = if (newIsLiked) comment.likesCount + 1 else comment.likesCount - 1

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

                // Remove from syncing after successful sync
                kotlinx.coroutines.delay(500)
                syncingLikes.remove(comment.commentId)

            } catch (_: Exception) {
                // On error, revert local cache
                if (newIsLiked) {
                    likedCommentIds.remove(comment.commentId)
                } else {
                    likedCommentIds.add(comment.commentId)
                }
                syncingLikes.remove(comment.commentId)

                // Revert UI
                withContext(Dispatchers.Main) {
                    _comments.value = _comments.value.map { c ->
                        if (c.commentId == comment.commentId) {
                            c.copy(isLiked = isCurrentlyLiked, likesCount = comment.likesCount)
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

                    // ✅ Update Room cache immediately with decremented comment count
                    val currentPost = db.postDao().getPostById(postId)
                    if (currentPost != null && currentPost.commentsCount > 0) {
                        db.postDao().updateCommentCount(postId, currentPost.commentsCount - 1)
                    }
                }
            } catch (_: Exception) {
            }
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

    /* ================= DEBUG/TESTING ================= */

    /**
     * Sync post comment count from Firestore to Room
     * Call this after manually deleting comments in Firestore Console
     */
    fun syncPostCommentCountFromFirestore(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestoreDb = FirebaseFirestore.getInstance()

                // Get actual comment count from Firestore
                val actualCommentCount = firestoreDb.collection(POSTS_COLLECTION)
                    .document(postId)
                    .collection("comments")
                    .get()
                    .await()
                    .size()

                // Update Room database
                db.postDao().updateCommentCount(postId, actualCommentCount)

                // Also update the Firestore post document
                firestoreDb.collection(POSTS_COLLECTION)
                    .document(postId)
                    .update("commentsCount", actualCommentCount)
                    .await()

                println("✅ Updated post $postId: commentsCount = $actualCommentCount")

            } catch (e: Exception) {
                println("❌ Error syncing comment count: ${e.message}")
            }
        }
    }

    /**
     * Fix ALL posts comment counts from Firestore
     */
    fun syncAllPostCommentCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val firestoreDb = FirebaseFirestore.getInstance()

                // Get all posts
                val posts = firestoreDb.collection(POSTS_COLLECTION).get().await()

                var fixed = 0

                for (postDoc in posts.documents) {
                    val postId = postDoc.id

                    // Get actual comment count
                    val actualCount = firestoreDb.collection(POSTS_COLLECTION)
                        .document(postId)
                        .collection("comments")
                        .get()
                        .await()
                        .size()

                    // Update Room
                    db.postDao().updateCommentCount(postId, actualCount)

                    // Update Firestore post document
                    postDoc.reference.update("commentsCount", actualCount).await()

                    fixed++
                }

                println("✅ Fixed $fixed posts' comment counts")

            } catch (e: Exception) {
                println("❌ Error: ${e.message}")
            }
        }
    }
}

