package com.example.uplyft.data.repository

import com.example.uplyft.data.local.dao.UserDao
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.data.local.entity.toEntity
import com.example.uplyft.data.remote.firebase.AuthFirebaseSource
import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.Resource

class AuthRepository(
    private val firebaseSource: AuthFirebaseSource,
    private val userDao: UserDao
) {
    suspend fun login(email: String, password: String): Resource<User> {
        return try {
            val user = firebaseSource.login(email, password)
            userDao.insertUser(user.toEntity())   // cache locally
            Resource.Success(user)
        } catch (e: Exception) {
            // fallback: try local cache
            val uid = firebaseSource.getCurrentUserId()
            val cached = uid?.let { userDao.getUserById(it)?.toDomain() }
            if (cached != null) Resource.Success(cached)
            else Resource.Error(e.message ?: "Login failed")
        }
    }

    suspend fun signup(email: String, password: String, fullName: String): Resource<User> {
        return try {
            val user = firebaseSource.signup(email, password, fullName)
            userDao.insertUser(user.toEntity())
            Resource.Success(user)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Signup failed")
        }
    }

    fun isLoggedIn() = firebaseSource.isLoggedIn()

    suspend fun logout(userDao: UserDao) {
        firebaseSource.logout()
        userDao.clearUsers()
    }
}