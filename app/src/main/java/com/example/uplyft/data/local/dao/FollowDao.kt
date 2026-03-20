package com.example.uplyft.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.uplyft.data.local.entity.FollowEntity

@Dao
interface FollowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: FollowEntity)

    @Query("SELECT * FROM follows WHERE id = :id LIMIT 1")
    suspend fun getFollow(id: String): FollowEntity?

    @Query("DELETE FROM follows WHERE id = :id")
    suspend fun deleteFollow(id: String)

    @Query("SELECT isFollowing FROM follows WHERE id = :id LIMIT 1")
    suspend fun isFollowing(id: String): Boolean?
}