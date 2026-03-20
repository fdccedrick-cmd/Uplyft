package com.example.uplyft.data.remote.firebase

import com.example.uplyft.domain.model.Comment
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.example.uplyft.utils.Constants.POSTS_COLLECTION

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
    fun observeComments(postId: String): Flow<List<Comment>> = callbackFlow {
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
                trySend(comments)
            }
        // ✅ remove listener when flow is cancelled
        awaitClose { listener.remove() }
    }
}