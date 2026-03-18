package com.example.uplyft.viewmodel


import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import com.example.uplyft.utils.Resource
import com.example.uplyft.data.remote.cloudinary.CloudinaryService
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class EditProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db         = AppDatabase.getInstance(application)
    private val cloudinary by lazy { CloudinaryService(application.applicationContext) }

    private val _loadState = MutableStateFlow<Resource<User>?>(null)
    val loadState: StateFlow<Resource<User>?> = _loadState

    private val _saveState = MutableStateFlow<Resource<Unit>?>(null)
    val saveState: StateFlow<Resource<Unit>?> = _saveState

    fun loadUser(uid: String) {
        viewModelScope.launch {
            _loadState.value = Resource.Loading
            try {
                val user = withContext(Dispatchers.IO) {
                    db.userDao().getUserById(uid)?.toDomain()
                } ?: withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection(USERS_COLLECTION)
                        .document(uid)
                        .get()
                        .await()
                        .toObject(User::class.java)
                        ?.copy(uid = uid)
                }
                if (user != null) _loadState.value = Resource.Success(user)
                else _loadState.value = Resource.Error("User not found")
            } catch (e: Exception) {
                _loadState.value = Resource.Error(e.message ?: "Failed to load")
            }
        }
    }

    fun saveProfile(
        uid     : String,
        fullName: String,
        username: String,
        bio     : String
    ) {
        if (fullName.isBlank()) {
            _saveState.value = Resource.Error("Name cannot be empty")
            return
        }
        if (username.isBlank()) {
            _saveState.value = Resource.Error("Username cannot be empty")
            return
        }

        viewModelScope.launch {
            _saveState.value = Resource.Loading
            try {
                val updates = mapOf(
                    "fullName" to fullName.trim(),
                    "username" to username.trim().lowercase(),
                    "bio"      to bio.trim()
                )

                // Update Firestore
                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection(USERS_COLLECTION)
                        .document(uid)
                        .update(updates)
                        .await()
                }

                // Update Room cache
                withContext(Dispatchers.IO) {
                    db.userDao().updateProfile(uid, fullName.trim(),
                        username.trim().lowercase(), bio.trim())
                }

                _saveState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveState.value = Resource.Error(e.message ?: "Failed to save")
            }
        }
    }

    fun updateProfileImage(imageUri: Uri, uid: String) {
        viewModelScope.launch {
            _saveState.value = Resource.Loading
            try {
                val imageUrl = withContext(Dispatchers.IO) {
                    cloudinary.uploadImage(imageUri)
                }
                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection(USERS_COLLECTION)
                        .document(uid)
                        .update("profileImageUrl", imageUrl)
                        .await()
                    db.userDao().updateProfileImage(uid, imageUrl)
                }
                _saveState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _saveState.value = Resource.Error(e.message ?: "Failed to update photo")
            }
        }
    }
}