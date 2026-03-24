package com.example.uplyft.data.repository

import android.net.Uri
import android.util.Log
import android.content.Context
import com.example.uplyft.data.local.dao.PostDao
import com.example.uplyft.data.local.dao.SavedPostDao
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.local.entity.PostEntity
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.data.local.entity.toEntity
import com.example.uplyft.data.remote.cloudinary.CloudinaryService
import com.example.uplyft.data.remote.firebase.PostFirebaseSource
import com.example.uplyft.domain.model.Post
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import com.example.uplyft.utils.NotificationSender
import com.example.uplyft.utils.PostUploadState
import com.example.uplyft.utils.NetworkUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.UUID

class PostRepository(
    private val context          : Context,
    private val postDao          : PostDao,
    private val firebaseSource   : PostFirebaseSource,
    private val cloudinaryService: CloudinaryService
) {

    private val db = AppDatabase.getInstance(context)
    private val savedPostDao: SavedPostDao = db.savedPostDao()
    private val pendingLikeUpdates = mutableSetOf<String>()
    private val pendingCommentUpdates = mutableSetOf<String>()


    fun observePosts(): Flow<List<Post>> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        return combine(
            postDao.observeAllPosts(),
            savedPostDao.observeSavedPostIds(currentUserId)
        ) { postEntities, savedPostIds ->
            postEntities.map { entity ->
                entity.toDomain().copy(isSaved = savedPostIds.contains(entity.postId))
            }
        }
    }

    fun observeUserPosts(userId: String): Flow<List<Post>> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        return combine(
            postDao.observeUserPosts(userId),
            savedPostDao.observeSavedPostIds(currentUserId)
        ) { postEntities, savedPostIds ->
            postEntities.map { entity ->
                entity.toDomain().copy(isSaved = savedPostIds.contains(entity.postId))
            }
        }
    }

    suspend fun refreshPosts(limit: Int = 5) {
        try {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

            // Fetch new posts from Firestore
            val remotePosts = firebaseSource.fetchPosts(
                limit         = limit,
                currentUserId = currentUserId
            )

            // Only clear cache if we got new posts successfully
            if (remotePosts.isNotEmpty()) {
                // Keep unsynced posts (pending uploads)
                val unsyncedPosts = postDao.getUnsyncedPosts()
                val unsyncedPostIds = unsyncedPosts.map { it.postId }

                // Clear all synced posts from Room
                val allPosts = postDao.getAllPostsSync()
                allPosts.forEach { post ->
                    if (!unsyncedPostIds.contains(post.postId)) {
                        postDao.deletePost(post.postId)
                    }
                }

                // Insert new posts
                remotePosts.forEach { post ->
                    if (!pendingLikeUpdates.contains(post.postId) &&
                        !pendingCommentUpdates.contains(post.postId)) {
                        postDao.insertPost(post.toEntity(isSynced = true))
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

    suspend fun loadMorePosts(limit: Int = 5) {
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

    suspend fun refreshUserPosts(userId: String, currentUserId: String?) {
        try {
            val remotePosts = firebaseSource.fetchUserPosts(userId, currentUserId)

            // Insert/update user's posts in Room (don't delete others)
            remotePosts.forEach { post ->
                postDao.insertPost(post.toEntity(isSynced = true))
            }
        } catch (_: Exception) {
            // Keep existing cache on error
        }
    }

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
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val fromUser = FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(userId).get().await()
                            NotificationSender(context).sendLikePostNotification(
                                fromUserId   = userId,
                                fromUsername = fromUser.getString("username") ?: "",
                                fromImage    = fromUser.getString("profileImageUrl") ?: "",
                                toUserId     = post.userId,
                                postId       = post.postId
                            )
                        } catch (e: Exception) {
                            Log.e("Like", "Notif failed: ${e.message}")
                        }
                    }
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

    suspend fun createPost(
        imageUris : List<Uri>,
        caption   : String,
        userId    : String,
        onProgress: (PostUploadState) -> Unit
    ) {
        val tempPostId = UUID.randomUUID().toString()

        // Check network first - if offline, save as failed immediately
        if (!NetworkUtils.isNetworkAvailable(context)) {
            // No network - get cached user info if available
            val cachedUser = db.userDao().getUserById(userId)?.toDomain()

            val username = cachedUser?.username ?: cachedUser?.fullName ?: "You"
            val userImageUrl = cachedUser?.profileImageUrl ?: ""

            val localPost = PostEntity(
                postId       = tempPostId,
                userId       = userId,
                username     = username,
                userImageUrl = userImageUrl,
                imageUrl     = imageUris.first().toString(),
                imageUrls    = imageUris.joinToString(",") { it.toString() },
                caption      = caption,
                isSynced     = false,
                uploadStatus = "failed"
            )
            postDao.insertPost(localPost)
            onProgress(PostUploadState.Error("No internet connection - Tap to retry"))
            return
        }

        // fetch user info (only when online)
        val userDoc = try {
            withTimeout(10000) {
                FirebaseFirestore.getInstance()
                    .collection(USERS_COLLECTION)
                    .document(userId)
                    .get().await()
            }
        } catch (e: Exception) {
            // Failed to fetch user info - use cached
            val cachedUser = db.userDao().getUserById(userId)?.toDomain()

            val username = cachedUser?.username ?: cachedUser?.fullName ?: "You"
            val userImageUrl = cachedUser?.profileImageUrl ?: ""

            val localPost = PostEntity(
                postId       = tempPostId,
                userId       = userId,
                username     = username,
                userImageUrl = userImageUrl,
                imageUrl     = imageUris.first().toString(),
                imageUrls    = imageUris.joinToString(",") { it.toString() },
                caption      = caption,
                isSynced     = false,
                uploadStatus = "failed"
            )
            postDao.insertPost(localPost)
            onProgress(PostUploadState.Error("Failed to fetch user info - Check connection"))
            return
        }

        val fullName     = userDoc.getString("fullName") ?: ""
        val username     = userDoc.getString("username").let {
            if (it.isNullOrEmpty()) fullName else it
        }
        val userImageUrl = userDoc.getString("profileImageUrl") ?: ""

        //save locally with first URI as preview - status "pending"
        val localPost = PostEntity(
            postId       = tempPostId,
            userId       = userId,
            username     = username,
            userImageUrl = userImageUrl,
            imageUrl     = imageUris.first().toString(),
            imageUrls    = imageUris.joinToString(",") { it.toString() },
            caption      = caption,
            isSynced     = false,
            uploadStatus = "pending"
        )
        postDao.insertPost(localPost)
        onProgress(PostUploadState.Saving)

        try {
            // Update status to "uploading"
            postDao.updateUploadStatus(tempPostId, "uploading")
            onProgress(PostUploadState.Uploading)

            // upload ALL images to Cloudinary sequentially with timeout
            val cloudinaryUrls = withTimeout(60000) {
                imageUris.map { uri ->
                    cloudinaryService.uploadImage(uri)
                }
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

            withTimeout(10000) {
                firebaseSource.savePost(post)
            }

            //Update Room with cloud URLs and mark as synced
            postDao.updatePostUrlsAndSync(
                tempPostId,
                cloudinaryUrls.first(),
                cloudinaryUrls.joinToString(","),
                true
            )
            postDao.updateUploadStatus(tempPostId, "synced")
            onProgress(PostUploadState.Done)

        } catch (e: Exception) {
            // Mark as failed, keep in Room for retry
            postDao.updateUploadStatus(tempPostId, "failed")
            val errorMsg = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> "Upload timeout - Check your connection"
                e.message?.contains("network", ignoreCase = true) == true -> "Network error"
                else -> e.message ?: "Upload failed"
            }
            onProgress(PostUploadState.Error(errorMsg))
        }
    }

    suspend fun retryUpload(post: Post, onProgress: (PostUploadState) -> Unit) {
        // Check network first
        if (!NetworkUtils.isNetworkAvailable(context)) {
            postDao.updateUploadStatus(post.postId, "failed")
            onProgress(PostUploadState.Error("No internet connection"))
            return
        }

        Log.d("PostRepository", "retryUpload: Network available, starting upload")

        try {
            // Update status to "uploading"
            postDao.updateUploadStatus(post.postId, "uploading")
            onProgress(PostUploadState.Uploading)

            // Parse local URIs from post.imageUrls
            val localUris = post.imageUrls.map { Uri.parse(it) }

            // Upload to Cloudinary with timeout
            val cloudinaryUrls = withTimeout(60000) {
                localUris.map { uri ->
                    cloudinaryService.uploadImage(uri)
                }
            }

            onProgress(PostUploadState.Syncing)

            // Save to Firestore with timeout
            Log.d("PostRepository", "retryUpload: Starting Firestore sync")
            val updatedPost = post.copy(
                imageUrl  = cloudinaryUrls.first(),
                imageUrls = cloudinaryUrls
            )
            withTimeout(10000) {
                firebaseSource.savePost(updatedPost)
            }
            Log.d("PostRepository", "retryUpload: Firestore sync complete")

            // Update Room with cloud URLs
            postDao.updatePostUrlsAndSync(
                post.postId,
                cloudinaryUrls.first(),
                cloudinaryUrls.joinToString(","),
                true
            )
            postDao.updateUploadStatus(post.postId, "synced")
            Log.d("PostRepository", "retryUpload: Upload SUCCESS, status set to synced")
            onProgress(PostUploadState.Done)

        } catch (e: Exception) {
            Log.e("PostRepository", "retryUpload: Upload FAILED - ${e.message}", e)
            postDao.updateUploadStatus(post.postId, "failed")
            val errorMsg = when {
                e.message?.contains("timeout", ignoreCase = true) == true -> "Upload timeout - Check your connection"
                e.message?.contains("network", ignoreCase = true) == true -> "Network error"
                else -> e.message ?: "Upload failed"
            }
            onProgress(PostUploadState.Error(errorMsg))
        }
    }
}