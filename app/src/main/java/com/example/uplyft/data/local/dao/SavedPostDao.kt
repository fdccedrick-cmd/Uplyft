package com.example.uplyft.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.uplyft.data.local.entity.SavedPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPost(savedPost: SavedPostEntity)

    @Query("DELETE FROM saved_posts WHERE userId = :userId AND postId = :postId")
    suspend fun deleteSavedPost(userId: String, postId: String)

    @Query("SELECT postId FROM saved_posts WHERE userId = :userId ORDER BY savedAt DESC")
    fun observeSavedPostIds(userId: String): Flow<List<String>>

    @Query("SELECT postId FROM saved_posts WHERE userId = :userId ORDER BY savedAt DESC")
    suspend fun getSavedPostIds(userId: String): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_posts WHERE userId = :userId AND postId = :postId)")
    suspend fun isPostSaved(userId: String, postId: String): Boolean

    @Query("SELECT COUNT(*) FROM saved_posts WHERE userId = :userId")
    suspend fun getSavedPostCount(userId: String): Int

    @Query("DELETE FROM saved_posts WHERE userId = :userId")
    suspend fun clearAllSavedPosts(userId: String)
}

