package com.example.uplyft.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.uplyft.data.local.dao.UserDao
import com.example.uplyft.data.local.entity.UserEntity
import com.example.uplyft.utils.Constants.DATABASE_NAME
import com.example.uplyft.data.local.dao.PostDao
import com.example.uplyft.data.local.entity.PostEntity
import com.example.uplyft.data.local.dao.FollowDao
import com.example.uplyft.data.local.entity.FollowEntity

@Database(
    entities = [PostEntity::class, UserEntity::class, FollowEntity::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun userDao(): UserDao
    abstract fun followDao(): FollowDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}