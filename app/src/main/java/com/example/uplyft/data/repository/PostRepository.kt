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
import java.util.UUID
import com.example.uplyft.utils.PostUploadState
import com.google.firebase.auth.FirebaseAuth


// data/repository/PostRepository.kt
class PostRepository(
    private val postDao: PostDao,
    private val firebaseSource: PostFirebaseSource,
    private val cloudinaryService: CloudinaryService
) {

    suspend fun refreshPosts(limit: Int = 10) {
        try {
            val remotePosts = firebaseSource.fetchPosts(limit = limit)
            remotePosts.forEach { post ->
                postDao.insertPost(post.toEntity(isSynced = true))
            }
        } catch (e: Exception) {
            // Silently fail — Room cache shows last known posts
        }
    }
    suspend fun loadMorePosts(limit: Int = 10) {
        try {
            val remotePosts = firebaseSource.fetchMorePosts(limit = limit)
            remotePosts.forEach { post ->
                postDao.insertPost(post.toEntity(isSynced = true))
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }
    suspend fun toggleLike(post: Post) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        try {
            val isLiked = firebaseSource.toggleLike(post.postId, userId)
            val newCount = if (isLiked) post.likesCount + 1 else post.likesCount - 1
            // Update Room instantly
            postDao.updateLikeCount(post.postId, newCount)
        } catch (e: Exception) {
            // Revert silently if it fails
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
        imageUri: Uri,
        caption: String,
        userId: String,
        onProgress: (PostUploadState) -> Unit
    ) {
        val tempPostId = UUID.randomUUID().toString()
        val localPost = PostEntity(
            postId   = tempPostId,
            userId   = userId,
            imageUrl = imageUri.toString(),
            caption  = caption,
            isSynced = false
        )
        postDao.insertPost(localPost)
        onProgress(PostUploadState.Saving)

        try {
            onProgress(PostUploadState.Uploading)
            val cloudinaryUrl = cloudinaryService.uploadImage(imageUri)

            onProgress(PostUploadState.Syncing)
            val post = Post(
                postId   = tempPostId,
                userId   = userId,
                imageUrl = cloudinaryUrl,
                caption  = caption
            )
            firebaseSource.savePost(post)
            postDao.updatePostUrl(tempPostId, cloudinaryUrl)
            onProgress(PostUploadState.Done)

        } catch (e: Exception) {
            onProgress(PostUploadState.Error(e.message ?: "Upload failed"))
        }
    }


}