package com.example.uplyft.domain.model

data class Post(
    val postId      : String = "",
    val userId      : String = "",
    val username    : String = "",       // ← add
    val userImageUrl: String = "",       // ← add
    val imageUrl    : String = "",
    val caption     : String = "",
    val likesCount  : Int    = 0,
    val isLiked     : Boolean = false,   // ← add
    val createdAt   : Long   = System.currentTimeMillis()
)