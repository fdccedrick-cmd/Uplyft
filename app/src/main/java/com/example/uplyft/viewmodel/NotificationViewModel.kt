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
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    fun loadNotifications(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list             = notifSource.getNotifications(userId)
                _notifications.value = list
                // ✅ sync unread count from loaded list
                _unreadCount.value   = list.count { !it.isRead }
            } catch (e: Exception) {
                Log.e("NotifVM", "Error: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ✅ real-time badge — updates instantly when new notif arrives
    fun startListeningUnreadCount(userId: String) {
        // remove existing listener first
        listenerRegistration?.remove()

        listenerRegistration = db.collection("notifications")
            .whereEqualTo("toUserId", userId)
            .whereEqualTo("isRead", false)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("NotifVM", "Listener error: ${error.message}")
                    return@addSnapshotListener
                }
                val count        = snapshot?.documents?.size ?: 0
                _unreadCount.value = count
                Log.d("NotifVM", "Unread count updated: $count")
            }
    }

    fun stopListening() {
        listenerRegistration?.remove()
        listenerRegistration = null
    }

    fun markAllRead(userId: String) {
        viewModelScope.launch {
            try {
                // ✅ update local state instantly — no waiting for Firestore
                _notifications.value = _notifications.value
                    .map { it.copy(isRead = true) }
                _unreadCount.value = 0

                // ✅ then sync to Firestore in background
                notifSource.markAllRead(userId)

            } catch (e: Exception) {
                Log.e("NotifVM", "markAllRead failed: ${e.message}")
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