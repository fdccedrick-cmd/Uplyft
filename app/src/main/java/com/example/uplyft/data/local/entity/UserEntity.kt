package com.example.uplyft.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.uplyft.domain.model.User
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val uid            : String,
    val fullName       : String,
    val username       : String = "",
    val email          : String,
    val profileImageUrl: String = "",
    val bio            : String = "",
    val createdAt      : Long   = System.currentTimeMillis()
)

fun UserEntity.toDomain() = User(
    uid             = uid,
    fullName        = fullName,
    username        = username,
    email           = email,
    profileImageUrl = profileImageUrl,
    bio             = bio,
    createdAt       = createdAt
)

fun User.toEntity() = UserEntity(
    uid             = uid,
    fullName        = fullName,
    username        = username,
    email           = email,
    profileImageUrl = profileImageUrl,
    bio             = bio,
    createdAt       = createdAt
)