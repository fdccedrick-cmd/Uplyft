package com.example.uplyft.data.local.dao

import androidx.room.Dao

@Dao
interface PostDao {
    // Define methods for inserting, querying, updating, and deleting posts
    // For example:
    // @Insert(onConflict = OnConflictStrategy.REPLACE)
    // suspend fun insertPost(post: PostEntity)
    //
    // @Query("SELECT * FROM posts WHERE postId = :postId LIMIT 1")
    // suspend fun getPostById(postId: String): PostEntity?
    //
    // @Query("DELETE FROM posts")
    // suspend fun clearPosts()
}