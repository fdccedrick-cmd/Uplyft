package com.example.uplyft.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uplyft.data.remote.firebase.NotificationFirebaseSource
import com.example.uplyft.domain.model.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await


// viewmodel/NotificationViewModel.kt
class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val notifSource = NotificationFirebaseSource()
    private val db          = FirebaseFirestore.getInstance()

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    private var unreadListener  : ListenerRegistration? = null
    private var notifListener   : ListenerRegistration? = null

    fun startListeningNotifications(userId: String) {
        if (notifListener != null) return

        _isLoading.value = true

        notifListener = db.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false

                if (error != null) {
                    Log.e("NotifVM", "Notif listener error: ${error.message}")
                    return@addSnapshotListener
                }

                val list = snapshot?.documents?.mapNotNull { doc ->
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
                    } catch (e: Exception) { null }
                }?.sortedByDescending { it.createdAt } ?: emptyList()

                _notifications.value = list
            }
    }
    fun startListeningUnreadCount(userId: String) {
        if (unreadListener != null) return

        unreadListener = db.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                _unreadCount.value = snapshot?.documents?.size ?: 0
            }
    }

    fun stopListening() {
        unreadListener?.remove()
        unreadListener = null
        notifListener?.remove()
        notifListener  = null
    }

    fun refreshNotifications(userId: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val notifications = notifSource.getNotifications(userId)
                _notifications.value = notifications
            } catch (e: Exception) {
                Log.e("NotifVM", "refreshNotifications failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAllRead(userId: String) {
        viewModelScope.launch {
            try {
                notifSource.markAllRead(userId)
            } catch (e: Exception) {
                Log.e("NotifVM", "markAllRead failed: ${e.message}")
            }
        }
    }
    fun markSingleRead(notifId: String, userId: String) {
        viewModelScope.launch {
            try {
                db.collection("notifications")
                    .document(notifId)
                    .update("isRead", true)
                    .await()
            } catch (e: Exception) {
                Log.e("NotifVM", "markSingleRead failed: ${e.message}")
            }
        }
    }
    fun refreshUnreadCount(userId: String) {
        viewModelScope.launch {
            try {
                _unreadCount.value = notifSource.getUnreadCount(userId)
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}