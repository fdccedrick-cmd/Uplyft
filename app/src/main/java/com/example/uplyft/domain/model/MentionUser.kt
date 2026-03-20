package com.example.uplyft.domain.model

data class MentionUser(
    val uid          : String = "",
    val username     : String = "",
    val fullName     : String = "",
    val profileImage : String = "",
    val isMutual     : Boolean = false  // follows each other
)