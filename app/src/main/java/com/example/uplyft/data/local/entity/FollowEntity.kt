package com.example.uplyft.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "follows")
data class FollowEntity(
    @PrimaryKey
    val id         : String,   // "{followerId}_{followingId}"
    val followerId : String,
    val followingId: String,
    val isFollowing: Boolean = true
)