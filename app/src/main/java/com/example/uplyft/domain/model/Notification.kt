package com.example.uplyft.domain.model

data class Notification(
    val id          : String  = "",
    val type        : String  = "",
    val fromUserId  : String  = "",
    val fromUsername: String  = "",
    val fromImage   : String  = "",
    val toUserId    : String  = "",
    val postId      : String  = "",
    val commentId   : String  = "",
    val message     : String  = "",
    val isRead      : Boolean = false,
    val createdAt   : Long    = System.currentTimeMillis()
)