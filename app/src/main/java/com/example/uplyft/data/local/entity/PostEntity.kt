package com.example.uplyft.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.uplyft.domain.model.Post
@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey
    val postId      : String,
    val userId      : String,
    val username    : String = "",
    val userImageUrl: String = "",
    val imageUrl    : String,
    val imageUrls  : String = "", // JSON string of all image URLs
    val caption     : String,
    val commentsCount: Int     = 0,
    val likesCount  : Int     = 0,
    val isLiked     : Boolean = false,
    val createdAt   : Long    = System.currentTimeMillis(),
    val isSynced    : Boolean = false
)

// PostEntity → Post (every field maps 1:1 now)
fun PostEntity.toDomain() = Post(
    postId       = postId,
    userId       = userId,
    username     = username,
    userImageUrl = userImageUrl,
    imageUrl     = imageUrl,
    imageUrls     = if (imageUrls.isBlank()) listOf(imageUrl)
    else imageUrls.split(",").filter { it.isNotBlank() },
    caption      = caption,
    commentsCount= commentsCount,
    likesCount   = likesCount,
    isLiked      = isLiked,
    createdAt    = createdAt
)

// Post → PostEntity
fun Post.toEntity(isSynced: Boolean = true) = PostEntity(
    postId       = postId,
    userId       = userId,
    username     = username,
    userImageUrl = userImageUrl,
    imageUrl     = imageUrl,
    imageUrls     = imageUrls.joinToString(","),
    caption      = caption,
    commentsCount= commentsCount,
    likesCount   = likesCount,
    isLiked      = isLiked,
    createdAt    = createdAt,
    isSynced     = isSynced
)