package com.example.uplyft.domain.model

data class Comment(
    val commentId  : String  = "",
    val postId     : String  = "",
    val userId     : String  = "",
    val username   : String  = "",
    val userImage  : String  = "",
    val text       : String  = "",
    val gifUrl     : String  = "",
    val isGif      : Boolean = false,
    val isPending  : Boolean = false,
    val likesCount : Int     = 0,
    val isLiked    : Boolean = false,
    val replyCount : Int     = 0,
    val parentId   : String  = "",   // empty = top level, set = reply
    val createdAt  : Long    = System.currentTimeMillis()
)