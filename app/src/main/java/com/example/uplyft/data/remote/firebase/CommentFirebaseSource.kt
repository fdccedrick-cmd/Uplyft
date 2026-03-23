package com.example.uplyft.data.remote.firebase

import android.content.Context
import android.util.Log
import com.example.uplyft.domain.model.Comment
import com.example.uplyft.utils.NotificationSender
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.uplyft.utils.Constants.POSTS_COLLECTION
import com.example.uplyft.utils.Constants.USERS_COLLECTION

class CommentFirebaseSource(
    private val context: Context
) {

    private val db = FirebaseFirestore.getInstance()

    suspend fun addComment(comment: Comment): Comment {
        val ref = db.collection(POSTS_COLLECTION)
            .document(comment.postId)
            .collection("comments")
            .document()

        val withId = comment.copy(commentId = ref.id)
        ref.set(withId).await()

        db.collection(POSTS_COLLECTION)
            .document(comment.postId)
            .update("commentsCount", FieldValue.increment(1))
            .await()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fromUser = db.collection(USERS_COLLECTION)
                    .document(comment.userId).get().await()
                val username = fromUser.getString("username") ?: ""
                val image    = fromUser.getString("profileImageUrl") ?: ""

                val postDoc     = db.collection(POSTS_COLLECTION)
                    .document(comment.postId).get().await()
                val postOwnerId = postDoc.getString("userId") ?: ""

                NotificationSender(context).sendCommentNotification(
                    fromUserId   = comment.userId,
                    fromUsername = username,
                    fromImage    = image,
                    toUserId     = postOwnerId,
                    postId       = comment.postId,
                    commentText  = comment.text
                )
            } catch (e: Exception) {
                Log.e("CommentNotif", "Failed: ${e.message}")
            }
        }

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

    fun observeComments(
        postId       : String,
        currentUserId: String?
    ): Flow<List<Comment>> = callbackFlow {

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
                        doc.toObject(Comment::class.java)
                            ?.copy(commentId = doc.id)
                    } ?: emptyList()

                if (!initialLoadComplete &&
                    currentUserId != null &&
                    comments.isNotEmpty()) {

                    initialLoadComplete = true

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
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
                                    } catch (_: Exception) { false }
                                    comment.copy(isLiked = isLiked)
                                }
                            }.awaitAll()
                            trySend(commentsWithLikes)
                        } catch (_: Exception) {
                            trySend(comments)
                        }
                    }
                } else {
                    trySend(comments)
                }
            }

        awaitClose { listener.remove() }
    }
}