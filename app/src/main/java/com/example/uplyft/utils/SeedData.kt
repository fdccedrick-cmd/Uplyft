package com.example.uplyft.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.uplyft.utils.Constants.POSTS_COLLECTION
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import kotlinx.coroutines.tasks.await
import java.util.UUID

object SeedData {

    // Free placeholder images from Unsplash (no API key needed)
    private val imageUrls = listOf(
        "https://images.unsplash.com/photo-1506748686214-e9df14d4d9d0",
        "https://images.unsplash.com/photo-1469474968028-56623f02e42e",
        "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05",
        "https://images.unsplash.com/photo-1441974231531-c6227db76b6e",
        "https://images.unsplash.com/photo-1472214103451-9374bd1c798e",
        "https://images.unsplash.com/photo-1426604966848-d7adac402bff",
        "https://images.unsplash.com/photo-1501785888041-af3ef285b470",
        "https://images.unsplash.com/photo-1518173946687-a4c8892bbd9f",
        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e",
        "https://images.unsplash.com/photo-1478827217976-e88f77c52183",
        "https://images.unsplash.com/photo-1490730141103-6cac27aaab94",
        "https://images.unsplash.com/photo-1486870591958-9b9d0d1dda99",
        "https://images.unsplash.com/photo-1475924156734-496f6cac6ec1",
        "https://images.unsplash.com/photo-1504893524553-b855bce32c67",
        "https://images.unsplash.com/photo-1519120944692-1a8d8cfc107f",
        "https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1",
        "https://images.unsplash.com/photo-1502082553048-f009c37129b9",
        "https://images.unsplash.com/photo-1511593358241-7eea1f3c84e5",
        "https://images.unsplash.com/photo-1465146633011-14f8e0781093",
        "https://images.unsplash.com/photo-1484591974057-265bb767ef71",
        "https://images.unsplash.com/photo-1523712999610-f77fbcfc3843",
        "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d",
        "https://images.unsplash.com/photo-1497436072909-60f360e1d4b1",
        "https://images.unsplash.com/photo-1494256997604-768d1f608cac",
        "https://images.unsplash.com/photo-1541963463532-d68292c34b19",
        "https://images.unsplash.com/photo-1517649763962-0c623066013b",
        "https://images.unsplash.com/photo-1500648767791-00dcc994a43e",
        "https://images.unsplash.com/photo-1506794778202-cad84cf45f1d",
        "https://images.unsplash.com/photo-1519085360753-af0119f7cbe7",
        "https://images.unsplash.com/photo-1524504388940-b1c1722653e1",
        "https://images.unsplash.com/photo-1534528741775-53994a69daeb",
        "https://images.unsplash.com/photo-1517841905240-472988babdf9",
        "https://images.unsplash.com/photo-1529626455594-4ff0802cfb7e",
        "https://images.unsplash.com/photo-1544005313-94ddf0286df2",
        "https://images.unsplash.com/photo-1499952127939-9bbf5af6c51c",
        "https://images.unsplash.com/photo-1488161628813-04466f872be2",
        "https://images.unsplash.com/photo-1552374196-c4e7ffc6e126",
        "https://images.unsplash.com/photo-1542909168-82c3e7fdca5c",
        "https://images.unsplash.com/photo-1531746020798-e6953c6e8e04",
        "https://images.unsplash.com/photo-1539571696357-5a69c17a67c6",
        "https://images.unsplash.com/photo-1521119989659-a83eee488004",
        "https://images.unsplash.com/photo-1509967419530-da38b4704bc6",
        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde",
        "https://images.unsplash.com/photo-1524638431109-93d95c968f03",
        "https://images.unsplash.com/photo-1543610892-0b1f7e6d8ac1",
        "https://images.unsplash.com/photo-1502823403499-6ccfcf4fb453",
        "https://images.unsplash.com/photo-1522075469751-3a6694fb2f61",
        "https://images.unsplash.com/photo-1504257432389-52343af06ae3",
        "https://images.unsplash.com/photo-1492562080023-ab3db95bfbce",
        "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d"
    )

    private val captions = listOf(
        "Beautiful sunset 🌅",
        "Nature at its finest 🌿",
        "Adventure awaits! ⛰️",
        "Making memories 📸",
        "Living my best life ✨",
        "Wanderlust vibes 🌍",
        "Blessed and grateful 🙏",
        "Good vibes only ☀️",
        "Chasing dreams 💫",
        "Simply amazing! 😍",
        "Peace and tranquility 🕊️",
        "Life is beautiful 🌸",
        "Exploring the world 🗺️",
        "Sunshine state of mind ☀️",
        "Dream big 💭",
        "Stay wild 🌿",
        "Ocean breeze 🌊",
        "Mountain therapy 🏔️",
        "Golden hour magic ✨",
        "Weekend vibes 🎉",
        "Happiness is here 😊",
        "Fresh perspectives 👀",
        "Living the dream 💯",
        "Just breathe 🌬️",
        "Positivity only ✌️",
        "Grateful heart ❤️",
        "New adventures 🚀",
        "Finding joy 🌈",
        "Simple pleasures 🍃",
        "Making moments count ⏰",
        "Endless summer ☀️",
        "Wild and free 🦋",
        "Serenity now 🧘",
        "Pure bliss 😌",
        "Life's a journey 🛤️",
        "Smile always 😄",
        "Natural beauty 🌺",
        "Sky above, earth below ☁️",
        "Inhale confidence 💪",
        "Radiate positivity ✨",
        "Create your sunshine 🌞",
        "Be yourself 🌟",
        "Live fully 🎯",
        "Embrace the chaos 🌪️",
        "Find your peace ☮️",
        "Love this view 👁️",
        "Feeling alive 💚",
        "Stay curious 🔍",
        "Born to explore 🧭",
        "Collect moments 📷"
    )

    /**
     * Seeds 50 posts to Firestore for pagination testing
     * Call this from MainActivity or a debug menu
     */
    suspend fun seedPosts() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid
        val firestore = FirebaseFirestore.getInstance()

        // Get current user data
        val userDoc = firestore.collection(USERS_COLLECTION)
            .document(userId)
            .get()
            .await()

        val fullName = userDoc.getString("fullName") ?: "Test User"
        val username = userDoc.getString("username") ?: fullName
        val profileImageUrl = userDoc.getString("profileImageUrl") ?: ""

        // Create 50 posts
        for (i in 0 until 50) {
            val postId = UUID.randomUUID().toString()
            val imageUrl = imageUrls[i % imageUrls.size]
            val caption = captions[i % captions.size]

            // Add variation to timestamps (spread over last 30 days)
            val timestamp = System.currentTimeMillis() - (i * 3600000L) // 1 hour apart

            val post = hashMapOf(
                "postId" to postId,
                "userId" to userId,
                "username" to username,
                "userImageUrl" to profileImageUrl,
                "imageUrl" to imageUrl,
                "imageUrls" to listOf(imageUrl),
                "caption" to caption,
                "likesCount" to (0..50).random(),
                "commentsCount" to (0..20).random(),
                "createdAt" to timestamp
            )

            try {
                firestore.collection(POSTS_COLLECTION)
                    .document(postId)
                    .set(post)
                    .await()

                println("✅ Seeded post $i: $caption")
            } catch (e: Exception) {
                println("❌ Failed to seed post $i: ${e.message}")
            }
        }

        println("🎉 Successfully seeded 50 posts!")
    }

    /**
     * Deletes all posts created by current user
     * Use this to clean up test data
     */
    suspend fun deleteAllMyPosts() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val userId = currentUser.uid
        val firestore = FirebaseFirestore.getInstance()

        try {
            val snapshot = firestore.collection(POSTS_COLLECTION)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            snapshot.documents.forEach { doc ->
                doc.reference.delete().await()
            }

            println("🗑️ Deleted all posts for user: $userId")
        } catch (e: Exception) {
            println("❌ Failed to delete posts: ${e.message}")
        }
    }
}


