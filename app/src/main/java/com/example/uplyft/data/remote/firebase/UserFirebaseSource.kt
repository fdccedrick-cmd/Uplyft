package com.example.uplyft.data.remote.firebase

import android.util.Log
import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import com.example.uplyft.utils.Constants.FOLLOWS_COLLECTION
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserFirebaseSource {
    private val db = FirebaseFirestore.getInstance()

    suspend fun searchUsers(query: String, currentUserId: String?): List<User> {
        if (query.isBlank()) return emptyList()

        val queryLower = query.lowercase().trim()

        return try {
            // Fetch all users (or limit to reasonable number)
            val allUsers = db.collection(USERS_COLLECTION)
                .limit(500) // Reasonable limit for search
                .get()
                .await()
                .mapNotNull { doc ->
                    try {
                        // Log the raw document data to see field names
                        Log.d("UserFirebaseSource", "RAW Document ${doc.id}: ${doc.data}")
                        Log.d("UserFirebaseSource", "  imageUrl = '${doc.getString("imageUrl")}'")
                        Log.d("UserFirebaseSource", "  profileImageUrl = '${doc.getString("profileImageUrl")}'")

                        // Try BOTH field names
                        val imageUrl = doc.getString("imageUrl") ?: doc.getString("profileImageUrl") ?: ""

                        val user = User(
                            uid = doc.id,
                            fullName = doc.getString("fullName") ?: "",
                            username = doc.getString("username") ?: "",
                            email = doc.getString("email") ?: "",
                            profileImageUrl = imageUrl,
                            bio = doc.getString("bio") ?: "",
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                        )

                        Log.d("UserFirebaseSource", "Created User ${user.username}: profileImageUrl='${user.profileImageUrl}'")
                        user
                    } catch (e: Exception) {
                        Log.e("UserFirebaseSource", "Error parsing user ${doc.id}: ${e.message}")
                        null
                    }
                }
                .filter { it.uid != currentUserId } // Exclude current user

            // Filter users based on query (case-insensitive)
            val matchingUsers = allUsers.filter { user ->
                user.username.lowercase().contains(queryLower) ||
                user.fullName.lowercase().contains(queryLower)
            }

            Log.d("UserFirebaseSource", "Query: '$query', Found: ${matchingUsers.size} users")
            matchingUsers.forEach { user ->
                Log.d("UserFirebaseSource", "  - ${user.username} (${user.fullName}): profileImageUrl='${user.profileImageUrl}'")
            }

            // If current user is logged in, prioritize followed users
            if (currentUserId != null && matchingUsers.isNotEmpty()) {
                val followingIds = getFollowingIds(currentUserId)
                val mutualFollowerIds = getMutualFollowerIds(currentUserId, followingIds)

                Log.d("UserFirebaseSource", "Following: ${followingIds.size}, Mutual: ${mutualFollowerIds.size}")

                // Sort: mutual followers first, then following, then others by best match
                return matchingUsers.sortedWith(
                    compareByDescending<User> { mutualFollowerIds.contains(it.uid) }
                        .thenByDescending { followingIds.contains(it.uid) }
                        .thenBy {
                            // Prioritize exact username start match
                            if (it.username.lowercase().startsWith(queryLower)) 0 else 1
                        }
                        .thenBy { it.username.lowercase() }
                ).take(20)
            }

            // Return sorted by best match
            matchingUsers.sortedWith(
                compareBy<User> {
                    // Exact username start match first
                    if (it.username.lowercase().startsWith(queryLower)) 0 else 1
                }
                .thenBy {
                    // Full name start match second
                    if (it.fullName.lowercase().startsWith(queryLower)) 0 else 1
                }
                .thenBy { it.username.lowercase() }
            ).take(20)

        } catch (e: Exception) {
            Log.e("UserFirebaseSource", "Search error: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun getFollowingIds(userId: String): Set<String> {
        return try {
            db.collection(FOLLOWS_COLLECTION)
                .whereEqualTo("followerId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("followingId") }
                .toSet()
        } catch (e: Exception) {
            Log.e("UserFirebaseSource", "Error getting following: ${e.message}")
            emptySet()
        }
    }

    private suspend fun getMutualFollowerIds(userId: String, followingIds: Set<String>): Set<String> {
        return try {
            if (followingIds.isEmpty()) return emptySet()

            db.collection(FOLLOWS_COLLECTION)
                .whereEqualTo("followingId", userId)
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("followerId") }
                .filter { followingIds.contains(it) } // Mutual if they follow back
                .toSet()
        } catch (e: Exception) {
            Log.e("UserFirebaseSource", "Error getting mutual followers: ${e.message}")
            emptySet()
        }
    }

    suspend fun getUserById(userId: String): User? {
        return try {
            val doc = db.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            if (doc.exists()) {
                // Firestore stores 'imageUrl' but User model expects 'profileImageUrl'
                User(
                    uid = doc.id,
                    fullName = doc.getString("fullName") ?: "",
                    username = doc.getString("username") ?: "",
                    email = doc.getString("email") ?: "",
                    profileImageUrl = doc.getString("imageUrl") ?: "", // Map imageUrl -> profileImageUrl
                    bio = doc.getString("bio") ?: "",
                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                )
            } else {
                null
            }
        } catch (e: Exception) {
            // Return null if user not found or error occurred
            null
        }
    }
}


