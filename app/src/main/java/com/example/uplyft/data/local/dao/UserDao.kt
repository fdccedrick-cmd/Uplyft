package com.example.uplyft.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.uplyft.data.local.entity.UserEntity

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE uid = :uid LIMIT 1")
    suspend fun getUserById(uid: String): UserEntity?

    @Query("UPDATE users SET profileImageUrl = :url WHERE uid = :uid")
    suspend fun updateProfileImage(uid: String, url: String)

    @Query("""
        UPDATE users 
        SET fullName = :fullName, username = :username, bio = :bio 
        WHERE uid = :uid
    """)
    suspend fun updateProfile(uid: String, fullName: String, username: String, bio: String)
    
    @Query("DELETE FROM users")
    suspend fun clearUsers()

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): UserEntity?
}