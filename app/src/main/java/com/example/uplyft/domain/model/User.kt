package com.example.uplyft.domain.model

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis()
)