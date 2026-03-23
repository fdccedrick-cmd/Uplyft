package com.example.uplyft.data.remote.firebase

import com.example.uplyft.domain.model.Post
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.example.uplyft.utils.Constants.POSTS_COLLECTION
import com.example.uplyft.utils.Constants.USERS_COLLECTION
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
    suspend fun fetchPosts(limit: Int = 10, currentUserId: String?): List<Post> {
        val snapshot = db.collection(POSTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .get()
            .await()

        lastVisible = snapshot.documents.lastOrNull()

        return snapshot.documents.mapNotNull { doc ->
            val post = doc.toObject(Post::class.java)?.copy(postId = doc.id) ?: return@mapNotNull null

            // Fetch user data for each post
            val userDoc = db.collection(USERS_COLLECTION)
                .document(post.userId)
                .get()
                .await()

            val fullName = userDoc.getString("fullName") ?: ""
            val username = userDoc.getString("username") ?: ""
            val profileImageUrl = userDoc.getString("profileImageUrl") ?: ""

            // Check if current user liked this post
            val isLiked = if (currentUserId != null) {
                db.collection(POSTS_COLLECTION)
                    .document(doc.id)
                    .collection("likes")
                    .document(currentUserId)
                    .get()
                    .await()
                    .exists()
            } else false

            // Get comment count from Firestore document (fallback to 0 if not present)
            val commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0

            post.copy(
                // fallback to fullName if username is empty
                username      = username.ifEmpty { fullName },
                userImageUrl  = profileImageUrl,
                isLiked       = isLiked,
                commentsCount = commentsCount
            )
        }
    }

    // ✅ Fix — fetchMorePosts uses lastVisible cursor
    suspend fun fetchMorePosts(limit: Int = 10, currentUserId: String?): List<Post> {
        val last = lastVisible ?: return emptyList()   // nothing to paginate

        val snapshot = db.collection(POSTS_COLLECTION)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(last)                          // Firestore cursor pagination
            .limit(limit.toLong())
            .get()
            .await()

        lastVisible = snapshot.documents.lastOrNull()

        return snapshot.documents.mapNotNull { doc ->
            val post = doc.toObject(Post::class.java)?.copy(postId = doc.id) ?: return@mapNotNull null

            // Check if current user liked this post
            val isLiked = if (currentUserId != null) {
                db.collection(POSTS_COLLECTION)
                    .document(doc.id)
                    .collection("likes")
                    .document(currentUserId)
                    .get()
                    .await()
                    .exists()
            } else false

            // Get comment count from Firestore document (fallback to 0 if not present)
            val commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0

            post.copy(
                isLiked = isLiked,
                commentsCount = commentsCount
            )
        }
    }

    suspend fun fetchUserPosts(userId: String, currentUserId: String?): List<Post> {
        val posts = db.collection(POSTS_COLLECTION)
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val post = doc.toObject(Post::class.java)?.copy(postId = doc.id) ?: return@mapNotNull null

                // Check if current user liked this post
                val isLiked = if (currentUserId != null) {
                    db.collection(POSTS_COLLECTION)
                        .document(doc.id)
                        .collection("likes")
                        .document(currentUserId)
                        .get()
                        .await()
                        .exists()
                } else false

                // Get comment count from Firestore document (fallback to 0 if not present)
                val commentsCount = doc.getLong("commentsCount")?.toInt() ?: 0

                post.copy(
                    isLiked = isLiked,
                    commentsCount = commentsCount
                )
            }

        return posts.sortedByDescending { it.createdAt }   // ← sort after fetch
    }
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