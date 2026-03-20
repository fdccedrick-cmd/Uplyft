package com.example.uplyft.utils

import com.example.uplyft.domain.model.Post
import com.example.uplyft.domain.model.User

data class UserProfileState(
    val user          : User    = User(),
    val posts         : List<Post> = emptyList(),
    val followersCount: Int     = 0,
    val followingCount: Int     = 0,
    val isFollowing   : Boolean = false,
    val isFollowingBack : Boolean = false,
    val isOwnProfile  : Boolean = false
)