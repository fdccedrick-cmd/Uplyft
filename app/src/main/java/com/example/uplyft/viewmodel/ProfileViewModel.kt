package com.example.uplyft.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.local.entity.toDomain
import com.example.uplyft.data.local.entity.toEntity
import com.example.uplyft.data.remote.cloudinary.CloudinaryService
import com.example.uplyft.data.remote.firebase.PostFirebaseSource
import com.example.uplyft.data.repository.PostRepository
import com.example.uplyft.domain.model.Post
import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.Resource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.firebase.firestore.ktx.toObjects
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await



class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = AppDatabase.getInstance(application)
    private val firebaseSource = PostFirebaseSource()
    private val cloudinary     = CloudinaryService(application.applicationContext)

    private val repository = PostRepository(
        postDao           = db.postDao(),
        firebaseSource    = firebaseSource,
        cloudinaryService = cloudinary
    )

    private val _profileState = MutableStateFlow<Resource<User>?>(null)
    val profileState: StateFlow<Resource<User>?> = _profileState

    private val _updateState = MutableStateFlow<Resource<String>?>(null)
    val updateState: StateFlow<Resource<String>?> = _updateState

    // Load user profile from Firestore + Room cache
    fun loadProfile(uid: String) {
        viewModelScope.launch {
            _profileState.value = Resource.Loading
            try {
                // Try Room cache first
                val cached = withContext(Dispatchers.IO) {
                    db.userDao().getUserById(uid)?.toDomain()
                }
                if (cached != null) _profileState.value = Resource.Success(cached)

                // Then fetch fresh from Firestore
                val fresh = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection(USERS_COLLECTION)
                        .document(uid)
                        .get()
                        .await()
                        .toObject(User::class.java)
                        ?.copy(uid = uid)
                }
                if (fresh != null) {
                    // Update Room cache
                    withContext(Dispatchers.IO) {
                        db.userDao().insertUser(fresh.toEntity())
                    }
                    _profileState.value = Resource.Success(fresh)
                }
            } catch (e: Exception) {
                if (_profileState.value !is Resource.Success) {
                    _profileState.value = Resource.Error(e.message ?: "Failed to load profile")
                }
            }
        }
    }

    // Upload new profile image to Cloudinary then update Firestore
    fun updateProfileImage(imageUri: Uri, uid: String) {
        viewModelScope.launch {
            _updateState.value = Resource.Loading
            try {
                // Upload to Cloudinary
                val imageUrl = withContext(Dispatchers.IO) {
                    cloudinary.uploadImage(imageUri)
                }
                // Update Firestore
                withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection(USERS_COLLECTION)
                        .document(uid)
                        .update("profileImageUrl", imageUrl)
                        .await()
                }
                // Update Room cache
                withContext(Dispatchers.IO) {
                    db.userDao().updateProfileImage(uid, imageUrl)
                }
                _updateState.value = Resource.Success(imageUrl)
            } catch (e: Exception) {
                _updateState.value = Resource.Error(e.message ?: "Failed to update photo")
            }
        }
    }

    // Get user posts from Room Flow
    fun getUserPosts(userId: String): Flow<List<Post>> {
        return repository.observeUserPosts(userId)
    }
}