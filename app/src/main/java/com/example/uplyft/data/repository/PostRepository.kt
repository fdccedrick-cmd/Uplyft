package com.example.uplyft.data.repository

import android.net.Uri
import com.example.uplyft.data.local.dao.PostDao
import com.example.uplyft.data.local.entity.PostEntity
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.data.local.entity.toEntity
import com.example.uplyft.data.remote.cloudinary.CloudinaryService
import com.example.uplyft.data.remote.firebase.PostFirebaseSource
import com.example.uplyft.domain.model.Post
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import com.example.uplyft.utils.PostUploadState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import kotlin.Exception
import kotlin.collections.mutableSetOf
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.joinToString


// data/repository/PostRepository.kt
class PostRepository(
    private val postDao          : PostDao,
    private val firebaseSource   : PostFirebaseSource,
    private val cloudinaryService: CloudinaryService
) {

    private val pendingLikeUpdates = mutableSetOf<String>()
    private val pendingCommentUpdates = mutableSetOf<String>()

    // ─────────────────────────────────────────────
    // OBSERVE
    // ─────────────────────────────────────────────

    fun observePosts(): Flow<List<Post>> {
        return postDao.observeAllPosts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeUserPosts(userId: String): Flow<List<Post>> {
        return postDao.observeUserPosts(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    // ─────────────────────────────────────────────
    // FETCH
    // ─────────────────────────────────────────────

    suspend fun refreshPosts(limit: Int = 10) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val remotePosts   = firebaseSource.fetchPosts(
                limit         = limit,
                currentUserId = currentUserId
            )
            remotePosts.forEach { post ->
                // Skip if post has pending like or comment updates
                if (!pendingLikeUpdates.contains(post.postId) &&
                    !pendingCommentUpdates.contains(post.postId)) {
                    postDao.insertPost(post.toEntity(isSynced = true))
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun loadMorePosts(limit: Int = 10) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val remotePosts   = firebaseSource.fetchMorePosts(
                limit         = limit,
                currentUserId = currentUserId
            )
            remotePosts.forEach { post ->
                // Skip if post has pending like or comment updates
                if (!pendingLikeUpdates.contains(post.postId) &&
                    !pendingCommentUpdates.contains(post.postId)) {
                    postDao.insertPost(post.toEntity(isSynced = true))
                }
            }
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────
    // LIKE
    // ─────────────────────────────────────────────

    suspend fun toggleLike(post: Post) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val newIsLiked = !post.isLiked
        val newCount   = if (newIsLiked) post.likesCount + 1 else post.likesCount - 1

        // instant UI update
        postDao.updateLikeState(post.postId, newIsLiked, newCount)

        CoroutineScope(Dispatchers.IO).launch {
            pendingLikeUpdates.add(post.postId)
            try {
                val likeRef = FirebaseFirestore.getInstance()
                    .collection("posts")
                    .document(post.postId)
                    .collection("likes")
                    .document(userId)
                val postRef = FirebaseFirestore.getInstance()
                    .collection("posts")
                    .document(post.postId)

                if (newIsLiked) {
                    likeRef.set(mapOf("likedAt" to System.currentTimeMillis())).await()
                    postRef.update("likesCount",
                        FieldValue.increment(1)).await()
                } else {
                    likeRef.delete().await()
                    postRef.update("likesCount",
                        FieldValue.increment(-1)).await()
                }
                pendingLikeUpdates.remove(post.postId)
                delay(500)
            } catch (_: Exception) {
                postDao.updateLikeState(post.postId, post.isLiked, post.likesCount)
                pendingLikeUpdates.remove(post.postId)
            }
        }
    }

    // ─────────────────────────────────────────────
    // COMMENTS
    // ─────────────────────────────────────────────

    suspend fun incrementCommentCount(postId: String) {
        try {
            val currentPost = postDao.getPostById(postId)
            if (currentPost != null) {
                postDao.updateCommentCount(postId, currentPost.commentsCount + 1)

                // Protect from being overwritten during refresh
                pendingCommentUpdates.add(postId)
                CoroutineScope(Dispatchers.IO).launch {
                    delay(3000) // 3 seconds protection window
                    pendingCommentUpdates.remove(postId)
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun decrementCommentCount(postId: String) {
        try {
            val currentPost = postDao.getPostById(postId)
            if (currentPost != null && currentPost.commentsCount > 0) {
                postDao.updateCommentCount(postId, currentPost.commentsCount - 1)

                // Protect from being overwritten during refresh
                pendingCommentUpdates.add(postId)
                CoroutineScope(Dispatchers.IO).launch {
                    delay(3000) // 3 seconds protection window
                    pendingCommentUpdates.remove(postId)
                }
            }
        } catch (_: Exception) {}
    }

    // ─────────────────────────────────────────────
    // CREATE POST — supports multiple images
    // ─────────────────────────────────────────────

    suspend fun createPost(
        imageUris : List<Uri>,   // ✅ List<Uri>
        caption   : String,
        userId    : String,
        onProgress: (PostUploadState) -> Unit
    ) {
        val tempPostId = UUID.randomUUID().toString()

        // fetch user info
        val userDoc      = FirebaseFirestore.getInstance()
            .collection(USERS_COLLECTION)
            .document(userId)
            .get().await()
        val fullName     = userDoc.getString("fullName") ?: ""
        val username     = userDoc.getString("username").let {
            if (it.isNullOrEmpty()) fullName else it
        }
        val userImageUrl = userDoc.getString("profileImageUrl") ?: ""

        // ✅ save locally with first URI as preview
        val localPost = PostEntity(
            postId       = tempPostId,
            userId       = userId,
            username     = username,
            userImageUrl = userImageUrl,
            imageUrl     = imageUris.first().toString(),
            imageUrls    = imageUris.joinToString(",") { it.toString() },
            caption      = caption,
            isSynced     = false
        )
        postDao.insertPost(localPost)
        onProgress(PostUploadState.Saving)

        try {
            onProgress(PostUploadState.Uploading)

            // ✅ upload ALL images to Cloudinary sequentially
            val cloudinaryUrls = imageUris.map { uri ->
                cloudinaryService.uploadImage(uri)
            }

            onProgress(PostUploadState.Syncing)

            val post = Post(
                postId       = tempPostId,
                userId       = userId,
                username     = username,
                userImageUrl = userImageUrl,
                imageUrl     = cloudinaryUrls.first(),
                imageUrls    = cloudinaryUrls,
                caption      = caption
            )
            firebaseSource.savePost(post)
            postDao.updatePostUrl(tempPostId, cloudinaryUrls.first())
            onProgress(PostUploadState.Done)

        } catch (e: Exception) {
            onProgress(PostUploadState.Error(e.message ?: "Upload failed"))
        }
    }
}