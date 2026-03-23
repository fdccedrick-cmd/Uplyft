package com.example.uplyft.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey
    val userId: String,
    val username: String,
    val fullName: String,
    val profileImageUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)

