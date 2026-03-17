package com.example.uplyft.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.uplyft.domain.model.User
@Entity (tableName = "users")
data class UserEntity(
    @PrimaryKey
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val profileImageUrl: String = "",
    val bio: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
// Mapper extensions
fun UserEntity.toDomain() = User(uid, fullName, email, profileImageUrl, bio, createdAt)
fun User.toEntity() = UserEntity(uid, fullName, email, profileImageUrl, bio, createdAt)