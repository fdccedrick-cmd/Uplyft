package com.example.uplyft.data.remote.firebase

import android.util.Log
import com.example.uplyft.domain.model.Notification
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class NotificationFirebaseSource {

    private val db = FirebaseFirestore.getInstance()

    suspend fun saveNotification(notification: Notification) {
        try {
            val data = hashMapOf(
                "type"         to notification.type,
                "fromUserId"   to notification.fromUserId,
                "fromUsername" to notification.fromUsername,
                "fromImage"    to notification.fromImage,
                "toUserId"     to notification.toUserId,
                "postId"       to notification.postId,
                "commentId"    to notification.commentId,
                "message"      to notification.message,
                "isRead"       to notification.isRead,
                "createdAt"    to notification.createdAt
            )
            db.collection("notifications").add(data).await()
        } catch (e: Exception) {
            Log.e("NotifSource", "Save failed: ${e.message}")
        }
    }

    suspend fun getNotifications(userId: String): List<Notification> {
        return try {
            val snapshot = db.collection("notifications")
                .whereEqualTo("toUserId", userId)
                .get()
                .await()

            Log.d("NotifSource", "Docs found: ${snapshot.documents.size}")

            // manual mapping — toObject() silently fails on camelCase fields
            snapshot.documents.mapNotNull { doc ->
                try {
                    Notification(
                        id           = doc.id,
                        type         = doc.getString("type")         ?: "",
                        fromUserId   = doc.getString("fromUserId")   ?: "",
                        fromUsername = doc.getString("fromUsername") ?: "",
                        fromImage    = doc.getString("fromImage")    ?: "",
                        toUserId     = doc.getString("toUserId")     ?: "",
                        postId       = doc.getString("postId")       ?: "",
                        commentId    = doc.getString("commentId")    ?: "",
                        message      = doc.getString("message")      ?: "",
                        isRead       = doc.getBoolean("isRead")      ?: false,
                        createdAt    = doc.getLong("createdAt")      ?: 0L
                    )
                } catch (e: Exception) {
                    Log.e("NotifSource", "Parse error on doc ${doc.id}: ${e.message}")
                    null
                }
            }.sortedByDescending { it.createdAt }

        } catch (e: Exception) {
            Log.e("NotifSource", "Fetch error: ${e.message}")
            emptyList()
        }
    }

    suspend fun markAllRead(userId: String) {
        try {
            val unreadDocs = db.collection("notifications")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
                .documents

            if (unreadDocs.isEmpty()) return
            unreadDocs.chunked(500).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { doc ->
                    batch.update(doc.reference, "isRead", true)
                }
                batch.commit().await()
            }

            Log.d("NotifSource", "Marked ${unreadDocs.size} as read")
        } catch (e: Exception) {
            Log.e("NotifSource", "markAllRead failed: ${e.message}")
        }
    }

    suspend fun getUnreadCount(userId: String): Int {
        return try {
            db.collection("notifications")
                .whereEqualTo("toUserId", userId)
                .whereEqualTo("isRead", false)
                .get()
                .await()
                .size()
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getFcmToken(userId: String): String? {
        return try {
            db.collection("users")
                .document(userId)
                .get()
                .await()
                .getString("fcmToken")
        } catch (e: Exception) {
            null
        }
    }
}