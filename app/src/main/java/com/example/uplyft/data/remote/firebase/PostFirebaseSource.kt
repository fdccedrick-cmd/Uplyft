package com.example.uplyft.data.remote.firebase

import com.example.uplyft.domain.model.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.uplyft.utils.Constants.POSTS_COLLECTION
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue

// data/remote/firebase/PostFirebaseSource.kt
class PostFirebaseSource {

    private val db = FirebaseFirestore.getInstance()

    // Keeps track of last document for pagination
    private var lastVisible: DocumentSnapshot? = null

    suspend fun savePost(post: Post) {
        db.collection(POSTS_COLLECTION)
            .document(post.postId)
            .set(post)
            .await()
    }

    // ✅ Fix — limit param added, saves lastVisible for pagination
    suspend fun fetchPosts(limit: Int = 10): List<Post> {
        val snapshot = db.collection(POSTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()

        // Save last doc for next page
        lastVisible = snapshot.documents.lastOrNull()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Post::class.java)?.copy(postId = doc.id)
        }
    }

    // ✅ Fix — fetchMorePosts uses lastVisible cursor
    suspend fun fetchMorePosts(limit: Int = 10): List<Post> {
        val last = lastVisible ?: return emptyList()   // nothing to paginate

        val snapshot = db.collection(POSTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(last)                          // Firestore cursor pagination
            .limit(limit.toLong())
            .get()
            .await()

        lastVisible = snapshot.documents.lastOrNull()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Post::class.java)?.copy(postId = doc.id)
        }
    }

    // ✅ Fix — toggleLike uses Firestore transaction
    suspend fun toggleLike(postId: String, userId: String): Boolean {
        val likeRef = db.collection(POSTS_COLLECTION)
            .document(postId)
            .collection("likes")
            .document(userId)

        val postRef = db.collection(POSTS_COLLECTION).document(postId)

        return db.runTransaction { transaction ->
            val likeDoc = transaction.get(likeRef)
            if (likeDoc.exists()) {
                // Already liked — unlike
                transaction.delete(likeRef)
                transaction.update(postRef, "likesCount", FieldValue.increment(-1))
                false   // not liked anymore
            } else {
                // Not liked — like it
                transaction.set(likeRef, hashMapOf("likedAt" to System.currentTimeMillis()))
                transaction.update(postRef, "likesCount", FieldValue.increment(1))
                true    // now liked
            }
        }.await()
    }

    suspend fun deletePost(postId: String) {
        db.collection(POSTS_COLLECTION).document(postId).delete().await()
    }
}