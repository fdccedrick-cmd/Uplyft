package com.example.uplyft.ui.main

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.uplyft.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class UplyftFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .update("fcmToken", token)
            .addOnFailureListener {
                Log.e("FCM", "Token update failed: ${it.message}")
            }
    }

    private fun showNotification(
        title    : String,
        body     : String,
        type     : String,
        postId   : String,
        fromUser : String,
        commentId: String
    ) {
        val channelId = "uplyft_notifications"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Uplyft Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Likes, comments, follows"
                enableLights(true)
                enableVibration(true)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        // ✅ pass all navigation data in intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("type",      type)
            putExtra("postId",    postId)
            putExtra("fromUserId", fromUser)
            putExtra("commentId", commentId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.app_logo)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title     = message.notification?.title
            ?: message.data["title"] ?: return
        val body      = message.notification?.body
            ?: message.data["body"]  ?: return
        val type      = message.data["type"]       ?: ""
        val postId    = message.data["postId"]     ?: ""
        val fromUser  = message.data["fromUserId"] ?: ""
        val commentId = message.data["commentId"]  ?: ""

        // ✅ pass fromUser to showNotification
        showNotification(title, body, type, postId, fromUser, commentId)
    }
}