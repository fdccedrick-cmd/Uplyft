package com.example.uplyft.domain.model

data class Post(
    val postId      : String = "",
    val userId      : String = "",
    val username    : String = "",       // ← add
    val userImageUrl: String = "",       // ← add
    val imageUrl     : String       = "",   // first image (backward compat)
    val imageUrls    : List<String> = emptyList(), // ✅ all images
    val caption     : String = "",
    val commentsCount: Int    = 0,
    val likesCount  : Int    = 0,
    val isLiked     : Boolean = false,   // ← add
    val isSaved     : Boolean = false,   // ← add for saved posts
    val uploadStatus: String  = "synced", // "pending", "uploading", "failed", "synced"
    val createdAt   : Long   = System.currentTimeMillis()
)