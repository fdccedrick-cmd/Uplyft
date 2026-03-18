package com.example.uplyft.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.uplyft.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

// data/local/dao/PostDao.kt
@Dao
interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun observeAllPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeUserPosts(userId: String): Flow<List<PostEntity>>

    @Query("UPDATE posts SET imageUrl = :url, isSynced = 1 WHERE postId = :postId")
    suspend fun updatePostUrl(postId: String, url: String)

    @Query("UPDATE posts SET likesCount = :count WHERE postId = :postId")
    suspend fun updateLikeCount(postId: String, count: Int)

    @Query("DELETE FROM posts WHERE postId = :postId")
    suspend fun deletePost(postId: String)

    @Query("SELECT * FROM posts WHERE isSynced = 0")
    suspend fun getUnsyncedPosts(): List<PostEntity>
}