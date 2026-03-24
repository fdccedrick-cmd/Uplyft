package com.example.uplyft.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.uplyft.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun observeAllPosts(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    suspend fun getAllPostsSync(): List<PostEntity>

    @Query("SELECT * FROM posts WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeUserPosts(userId: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE userId = :userId ORDER BY createdAt DESC")
    suspend fun getUserPosts(userId: String): List<PostEntity>

    @Query("SELECT * FROM posts WHERE postId = :postId LIMIT 1")
    suspend fun getPostById(postId: String): PostEntity?

    @Query("UPDATE posts SET imageUrl = :url, isSynced = 1 WHERE postId = :postId")
    suspend fun updatePostUrl(postId: String, url: String)

    @Query("UPDATE posts SET likesCount = :count WHERE postId = :postId")
    suspend fun updateLikeCount(postId: String, count: Int)

    @Query("UPDATE posts SET isLiked = :isLiked, likesCount = :count WHERE postId = :postId")
    suspend fun updateLikeState(postId: String, isLiked: Boolean, count: Int)

    @Query("UPDATE posts SET commentsCount = :count WHERE postId = :postId")
    suspend fun updateCommentCount(postId: String, count: Int)

    @Query("UPDATE posts SET isSynced = :synced WHERE postId = :postId")
    suspend fun markAsSynced(postId: String, synced: Boolean)

    @Query("UPDATE posts SET uploadStatus = :status WHERE postId = :postId")
    suspend fun updateUploadStatus(postId: String, status: String)

    @Query("UPDATE posts SET imageUrl = :url, imageUrls = :urls, isSynced = :synced WHERE postId = :postId")
    suspend fun updatePostUrlsAndSync(postId: String, url: String, urls: String, synced: Boolean)

    @Query("DELETE FROM posts WHERE postId = :postId")
    suspend fun deletePost(postId: String)

    @Query("SELECT * FROM posts WHERE isSynced = 0")
    suspend fun getUnsyncedPosts(): List<PostEntity>

    @Query("SELECT * FROM posts WHERE isSynced = 0")
    fun observeUnsyncedPosts(): Flow<List<PostEntity>>
}