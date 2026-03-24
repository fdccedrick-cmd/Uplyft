package com.example.uplyft.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_posts")
data class SavedPostEntity(
    @PrimaryKey
    val id: String,  // userId_postId
    val userId: String,
    val postId: String,
    val savedAt: Long = System.currentTimeMillis()
)

