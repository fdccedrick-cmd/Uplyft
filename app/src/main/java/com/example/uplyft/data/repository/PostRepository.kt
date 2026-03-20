package com.example.uplyft.data.repository

import com.example.uplyft.data.local.dao.PostDao
import com.example.uplyft.data.remote.cloudinary.CloudinaryService
import com.example.uplyft.data.remote.firebase.PostFirebaseSource
import com.example.uplyft.domain.model.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import android.net.Uri
import com.example.uplyft.data.local.entity.PostEntity
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.data.local.entity.toEntity
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import java.util.UUID
import com.example.uplyft.utils.PostUploadState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


// data/repository/PostRepository.kt
class PostRepository(
    private val postDao: PostDao,
    private val firebaseSource: PostFirebaseSource,
    private val cloudinaryService: CloudinaryService
) {

    // Track posts with pending optimistic updates to prevent Firestore from overwriting them
    private val pendingLikeUpdates = mutableSetOf<String>()

    suspend fun refreshPosts(limit: Int = 10) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val remotePosts = firebaseSource.fetchPosts(limit = limit, currentUserId = currentUserId)
            remotePosts.forEach { post ->
                // Don't overwrite posts with pending optimistic updates
                if (!pendingLikeUpdates.contains(post.postId)) {
                    postDao.insertPost(post.toEntity(isSynced = true))
                }
            }
        } catch (_: Exception) {
            // Silently fail — Room cache shows last known posts
        }
    }

    suspend fun loadMorePosts(limit: Int = 10) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            val remotePosts = firebaseSource.fetchMorePosts(limit = limit, currentUserId = currentUserId)
            remotePosts.forEach { post ->
                // Don't overwrite posts with pending optimistic updates
                if (!pendingLikeUpdates.contains(post.postId)) {
                    postDao.insertPost(post.toEntity(isSynced = true))
                }
            }
        } catch (_: Exception) {
            // Silently fail
        }
    }

    suspend fun toggleLike(post: Post) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 1. INSTANT UI UPDATE (Instagram approach)
        val newIsLiked = !post.isLiked
        val newCount = if (newIsLiked) post.likesCount + 1 else post.likesCount - 1

        // Update Room cache immediately - UI reflects this instantly
        postDao.updateLikeState(post.postId, newIsLiked, newCount)

        // 2. BACKGROUND SYNC to Firebase (launch and forget - don't wait)
        CoroutineScope(Dispatchers.IO).launch {
            // Mark as pending just before sync
            pendingLikeUpdates.add(post.postId)

            try {
                // Use newIsLiked state directly instead of checking exists
                val likeRef = FirebaseFirestore.getInstance()
                    .collection("posts")
                    .document(post.postId)
                    .collection("likes")
                    .document(userId)

                val postRef = FirebaseFirestore.getInstance()
                    .collection("posts")
                    .document(post.postId)

                if (newIsLiked) {
                    // Liking - add like
                    likeRef.set(mapOf("likedAt" to System.currentTimeMillis())).await()
                    postRef.update("likesCount", com.google.firebase.firestore.FieldValue.increment(1)).await()
                } else {
                    // Unliking - remove like
                    likeRef.delete().await()
                    postRef.update("likesCount", com.google.firebase.firestore.FieldValue.increment(-1)).await()
                }

                // Remove from pending immediately after sync
                pendingLikeUpdates.remove(post.postId)

                // Wait for Firestore to propagate
                kotlinx.coroutines.delay(500)

            } catch (_: Exception) {
                // If Firebase fails, revert the optimistic update
                postDao.updateLikeState(post.postId, post.isLiked, post.likesCount)
                pendingLikeUpdates.remove(post.postId)
            }
        }
    }

    // Observe local Room cache — UI always reads from here
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

    suspend fun createPost(
        imageUri  : Uri,
        caption   : String,
        userId    : String,
        onProgress: (PostUploadState) -> Unit
    ) {
        val tempPostId = UUID.randomUUID().toString()
        val userDoc = FirebaseFirestore.getInstance()
            .collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .await()

        val fullName = userDoc.getString("fullName") ?: ""
        val username = userDoc.getString("username").let {
            if (it.isNullOrEmpty()) fullName else it
        }
        val userImageUrl = userDoc.getString("profileImageUrl") ?: ""

        val localPost = PostEntity(
            postId       = tempPostId,
            userId       = userId,
            username     = username,
            userImageUrl = userImageUrl,
            imageUrl     = imageUri.toString(),
            caption      = caption,
            isSynced     = false
        )
        postDao.insertPost(localPost)
        onProgress(PostUploadState.Saving)

        try {
            onProgress(PostUploadState.Uploading)
            val cloudinaryUrl = cloudinaryService.uploadImage(imageUri)

            onProgress(PostUploadState.Syncing)
            val post = Post(
                postId       = tempPostId,
                userId       = userId,
                username     = username,
                userImageUrl = userImageUrl,
                imageUrl     = cloudinaryUrl,
                caption      = caption
            )
            firebaseSource.savePost(post)
            postDao.updatePostUrl(tempPostId, cloudinaryUrl)
            onProgress(PostUploadState.Done)

        } catch (e: Exception) {
            onProgress(PostUploadState.Error(e.message ?: "Upload failed"))
        }
    }


}