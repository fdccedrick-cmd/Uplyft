package com.example.uplyft.data.remote.firebase

import com.example.uplyft.domain.model.Comment
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.example.uplyft.utils.Constants.POSTS_COLLECTION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

// data/remote/firebase/CommentFirebaseSource.kt
class CommentFirebaseSource {

    private val db = FirebaseFirestore.getInstance()

    suspend fun addComment(comment: Comment): Comment {
        val ref = db.collection(POSTS_COLLECTION)
            .document(comment.postId)
            .collection("comments")
            .document()

        val withId = comment.copy(commentId = ref.id)
        ref.set(withId).await()

        // Increment comment count on post
        db.collection(POSTS_COLLECTION)
            .document(comment.postId)
            .update("commentsCount", FieldValue.increment(1))
            .await()

        return withId
    }

    suspend fun getComments(postId: String): List<Comment> {
        return db.collection(POSTS_COLLECTION)
            .document(postId)
            .collection("comments")
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(Comment::class.java)?.copy(commentId = doc.id)
            }
            .sortedBy { it.createdAt }
    }

    suspend fun deleteComment(postId: String, commentId: String) {
        db.collection(POSTS_COLLECTION)
            .document(postId)
            .collection("comments")
            .document(commentId)
            .delete()
            .await()

        db.collection(POSTS_COLLECTION)
            .document(postId)
            .update("commentsCount", FieldValue.increment(-1))
            .await()
    }

    // ✅ real-time listener — updates instantly when anyone comments
    fun observeComments(postId: String, currentUserId: String?): Flow<List<Comment>> = callbackFlow {
        var initialLoadComplete = false

        val listener = db.collection(POSTS_COLLECTION)
            .document(postId)
            .collection("comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val comments = snapshot?.documents
                    ?.mapNotNull { doc ->
                        doc.toObject(Comment::class.java)?.copy(commentId = doc.id)
                    } ?: emptyList()

                // Only check like status on INITIAL load
                if (!initialLoadComplete && currentUserId != null && comments.isNotEmpty()) {
                    initialLoadComplete = true

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Check all likes in parallel ONCE
                            val commentsWithLikes = comments.map { comment ->
                                async {
                                    val isLiked = try {
                                        db.collection(POSTS_COLLECTION)
                                            .document(postId)
                                            .collection("comments")
                                            .document(comment.commentId)
                                            .collection("likes")
                                            .document(currentUserId)
                                            .get()
                                            .await()
                                            .exists()
                                    } catch (_: Exception) {
                                        false
                                    }
                                    comment.copy(isLiked = isLiked)
                                }
                            }.awaitAll()

                            trySend(commentsWithLikes)
                        } catch (_: Exception) {
                            trySend(comments)
                        }
                    }
                } else {
                    // Subsequent updates - just send comment data without checking likes
                    // ViewModel will preserve like states
                    trySend(comments)
                }
            }
        awaitClose { listener.remove() }
    }
}