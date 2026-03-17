package com.example.uplyft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.domain.model.User
import com.example.uplyft.data.repository.AuthRepository
import com.example.uplyft.data.remote.firebase.AuthFirebaseSource
import com.example.uplyft.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = AuthRepository(
        firebaseSource = AuthFirebaseSource(),
        userDao = db.userDao()
    )

    private val _loginState = MutableStateFlow<Resource<User>?>(null)
    val loginState: StateFlow<Resource<User>?> = _loginState

    private val _signupState = MutableStateFlow<Resource<User>?>(null)
    val signupState: StateFlow<Resource<User>?> = _signupState

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _loginState.value = Resource.Loading
            _loginState.value = repository.login(email, password)
        }
    }

    fun signup(email: String, password: String, fullName: String) {
        viewModelScope.launch {
            _signupState.value = Resource.Loading
            _signupState.value = repository.signup(email, password, fullName)
        }
    }

    fun isLoggedIn() = repository.isLoggedIn()

    fun logout() {
        viewModelScope.launch {
            repository.logout(db.userDao())
        }
    }
}