package com.example.uplyft.data.remote.firebase

import com.example.uplyft.domain.model.Follow
import com.example.uplyft.utils.Constants.FOLLOWS_COLLECTION
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FollowFirebaseSource {

    private val db = FirebaseFirestore.getInstance()

    // Follow a user
    suspend fun followUser(followerId: String, followingId: String) {
        val docId = "${followerId}_${followingId}"
        val follow = Follow(
            followerId  = followerId,
            followingId = followingId
        )
        db.collection(FOLLOWS_COLLECTION).document(docId).set(follow).await()
    }

    // Unfollow a user
    suspend fun unfollowUser(followerId: String, followingId: String) {
        val docId = "${followerId}_${followingId}"
        db.collection(FOLLOWS_COLLECTION).document(docId).delete().await()
    }

    // Check if current user follows another user
    suspend fun isFollowing(followerId: String, followingId: String): Boolean {
        val docId = "${followerId}_${followingId}"
        return db.collection(FOLLOWS_COLLECTION)
            .document(docId)
            .get()
            .await()
            .exists()
    }

    // Get followers count of a user
    suspend fun getFollowersCount(userId: String): Int {
        return db.collection(FOLLOWS_COLLECTION)
            .whereEqualTo("followingId", userId)
            .get()
            .await()
            .size()
    }

    suspend fun getFollowingCount(userId: String): Int {
        return db.collection(FOLLOWS_COLLECTION)
            .whereEqualTo("followerId", userId)
            .get()
            .await()
            .size()
    }
}