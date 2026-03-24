package com.example.uplyft.data.repository

import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.local.entity.SavedPostEntity
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.domain.model.Post
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class SavedPostRepository(private val db: AppDatabase) {

    private val firestore = FirebaseFirestore.getInstance()
    private val savedPostDao = db.savedPostDao()
    private val postDao = db.postDao()

    // ─────────────────────────────────────────────
    // SAVE / UNSAVE POST
    // ─────────────────────────────────────────────

    suspend fun toggleSavePost(userId: String, postId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val isSaved = savedPostDao.isPostSaved(userId, postId)

            if (isSaved) {
                // Unsave: Delete from local cache immediately
                savedPostDao.deleteSavedPost(userId, postId)

                // Background sync to Firestore
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("savedPosts")
                        .document(postId)
                        .delete()
                        .await()
                } catch (e: Exception) {
                    // Silently fail, local cache already updated
                }
                false
            } else {
                // Save: Add to local cache immediately
                val savedPost = SavedPostEntity(
                    id = "${userId}_${postId}",
                    userId = userId,
                    postId = postId,
                    savedAt = System.currentTimeMillis()
                )
                savedPostDao.insertSavedPost(savedPost)

                // Background sync to Firestore
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("savedPosts")
                        .document(postId)
                        .set(mapOf(
                            "postId" to postId,
                            "savedAt" to System.currentTimeMillis()
                        ))
                        .await()
                } catch (e: Exception) {
                    // Silently fail, local cache already updated
                }
                true
            }
        }
    }

    // ─────────────────────────────────────────────
    // GET SAVED POSTS
    // ─────────────────────────────────────────────

    fun observeSavedPosts(userId: String): Flow<List<Post>> {
        // Combine saved post IDs with actual posts from cache
        return combine(
            savedPostDao.observeSavedPostIds(userId),
            postDao.observeAllPosts()
        ) { savedPostIds, allPosts ->
            // Filter posts that are in saved list, maintain saved order
            savedPostIds.mapNotNull { postId ->
                allPosts.find { it.postId == postId }?.toDomain()
            }
        }
    }

    suspend fun syncSavedPostsFromFirestore(userId: String) {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("savedPosts")
                    .get()
                    .await()

                // Clear local cache and insert from Firestore
                savedPostDao.clearAllSavedPosts(userId)

                snapshot.documents.forEach { doc ->
                    val postId = doc.getString("postId") ?: return@forEach
                    val savedAt = doc.getLong("savedAt") ?: System.currentTimeMillis()

                    val savedPost = SavedPostEntity(
                        id = "${userId}_${postId}",
                        userId = userId,
                        postId = postId,
                        savedAt = savedAt
                    )
                    savedPostDao.insertSavedPost(savedPost)
                }
            } catch (e: Exception) {
                // Silently fail, use cached data
            }
        }
    }

    suspend fun isPostSaved(userId: String, postId: String): Boolean {
        return withContext(Dispatchers.IO) {
            savedPostDao.isPostSaved(userId, postId)
        }
    }
}


