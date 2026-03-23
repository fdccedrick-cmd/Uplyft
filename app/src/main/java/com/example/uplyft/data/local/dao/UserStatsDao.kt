package com.example.uplyft.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.uplyft.data.local.entity.UserStatsEntity

@Dao
interface UserStatsDao {

    @Query("SELECT * FROM user_stats WHERE userId = :userId")
    suspend fun getUserStats(userId: String): UserStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserStats(stats: UserStatsEntity)

    @Query("DELETE FROM user_stats WHERE userId = :userId")
    suspend fun deleteUserStats(userId: String)
}

