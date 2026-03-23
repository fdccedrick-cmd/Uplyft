package com.example.uplyft.utils

import android.content.Context
import android.util.Log
import com.example.uplyft.data.remote.fcm.FCMApiService
import com.example.uplyft.data.remote.firebase.NotificationFirebaseSource
import com.example.uplyft.domain.model.Notification
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await


class NotificationSender(private val context: Context) {

    private val fcmApi      = FCMApiService(context)
    private val notifSource = NotificationFirebaseSource()
    private val db          = FirebaseFirestore.getInstance()

    suspend fun sendFollowNotification(
        fromUserId  : String,
        fromUsername: String,
        fromImage   : String,
        toUserId    : String
    ) {
        if (fromUserId == toUserId) return
        val message = "@$fromUsername started following you"
        save(
            Notification(
                type         = NotificationTypes.FOLLOW,
                fromUserId   = fromUserId,
                fromUsername = fromUsername,
                fromImage    = fromImage,
                toUserId     = toUserId,
                message      = message
            )
        )
        push(toUserId, fromUsername, "started following you",
            mapOf("type" to NotificationTypes.FOLLOW,
                "fromUserId" to fromUserId))
    }



    suspend fun sendLikePostNotification(
        fromUserId  : String,
        fromUsername: String,
        fromImage   : String,
        toUserId    : String,
        postId      : String
    ) {
        if (fromUserId == toUserId) return
        save(
            Notification(
                type         = NotificationTypes.LIKE_POST,
                fromUserId   = fromUserId,
                fromUsername = fromUsername,
                fromImage    = fromImage,
                toUserId     = toUserId,
                postId       = postId,
                message      = "@$fromUsername liked your post"
            )
        )
        push(toUserId, fromUsername, "liked your post",
            mapOf("type"   to NotificationTypes.LIKE_POST,
                "postId" to postId))
    }

    suspend fun sendCommentNotification(
        fromUserId  : String,
        fromUsername: String,
        fromImage   : String,
        toUserId    : String,
        postId      : String,
        commentText : String
    ) {
        if (fromUserId == toUserId) return
        val preview = if (commentText.length > 50)
            commentText.take(50) + "..." else commentText
        save(
            Notification(
                type         = NotificationTypes.COMMENT,
                fromUserId   = fromUserId,
                fromUsername = fromUsername,
                fromImage    = fromImage,
                toUserId     = toUserId,
                postId       = postId,
                message      = "@$fromUsername commented: $preview"
            )
        )
        push(toUserId, fromUsername, "commented: $preview",
            mapOf("type"   to NotificationTypes.COMMENT,
                "postId" to postId,
                "fromUserId" to fromUserId ))
    }



    suspend fun sendLikeCommentNotification(
        fromUserId  : String,
        fromUsername: String,
        fromImage   : String,
        toUserId    : String,
        postId      : String,
        commentId   : String
    ) {
        if (fromUserId == toUserId) return
        save(
            Notification(
                type         = NotificationTypes.LIKE_COMMENT,
                fromUserId   = fromUserId,
                fromUsername = fromUsername,
                fromImage    = fromImage,
                toUserId     = toUserId,
                postId       = postId,
                commentId    = commentId,
                message      = "@$fromUsername liked your comment"
            )
        )
        push(toUserId, fromUsername, "liked your comment",
            mapOf("type"      to NotificationTypes.LIKE_COMMENT,
                "postId"    to postId,
                "commentId" to commentId,
                "fromUserId" to fromUserId ))
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private suspend fun save(notification: Notification) {
        notifSource.saveNotification(notification)
    }

    private suspend fun push(
        toUserId: String,
        title   : String,
        body    : String,
        data    : Map<String, String>
    ) {
        try {
            val token = notifSource.getFcmToken(toUserId) ?: return
            fcmApi.sendNotification(
                toToken = token,
                title   = title,
                body    = body,
                data    = data
            )
        } catch (e: Exception) {
            Log.e("NotifSender", "Push failed: ${e.message}")
        }
    }
}