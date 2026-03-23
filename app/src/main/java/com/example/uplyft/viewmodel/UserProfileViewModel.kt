package com.example.uplyft.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.uplyft.data.local.AppDatabase
import com.example.uplyft.data.remote.firebase.PostFirebaseSource
import com.example.uplyft.data.remote.firebase.FollowFirebaseSource
import com.example.uplyft.domain.model.User
import com.example.uplyft.utils.Constants.USERS_COLLECTION
import com.example.uplyft.utils.Resource
import com.example.uplyft.utils.UserProfileState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db               = AppDatabase.getInstance(application)
    private val followSource     = FollowFirebaseSource(application.applicationContext)
    private val firebaseSource   = PostFirebaseSource()

    private val _profileState = MutableStateFlow<Resource<UserProfileState>?>(null)
    val profileState: StateFlow<Resource<UserProfileState>?> = _profileState

    private val _followState = MutableStateFlow<Resource<Boolean>?>(null)
    val followState: StateFlow<Resource<Boolean>?> = _followState

    fun loadUserProfile(targetUserId: String) {
        viewModelScope.launch {
            _profileState.value = Resource.Loading
            try {
                val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val isOwn = currentUid == targetUserId

                // Fetch user data
                val userDoc = FirebaseFirestore.getInstance()
                    .collection(USERS_COLLECTION)
                    .document(targetUserId)
                    .get()
                    .await()
                val user = userDoc.toObject(User::class.java)
                    ?.copy(uid = targetUserId) ?: return@launch

                // Fetch all data in parallel
                val followersCount = followSource.getFollowersCount(targetUserId)
                val followingCount = followSource.getFollowingCount(targetUserId)
                val isFollowing    = if (isOwn) false
                else followSource.isFollowing(currentUid, targetUserId)

                // Fetch user posts from Firestore
                val posts = firebaseSource.fetchUserPosts(targetUserId, currentUserId = currentUid)

                _profileState.value = Resource.Success(
                    UserProfileState(
                        user           = user,
                        posts          = posts,
                        followersCount = followersCount,
                        followingCount = followingCount,
                        isFollowing    = isFollowing,
                        isOwnProfile   = isOwn
                    )
                )
            } catch (e: Exception) {
                _profileState.value = Resource.Error(e.message ?: "Failed to load profile")
            }
        }
    }

    fun toggleFollow(targetUserId: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModelScope.launch {
            _followState.value = Resource.Loading
            try {
                val currentState = (_profileState.value as? Resource.Success)?.data ?: return@launch
                val isCurrentlyFollowing = currentState.isFollowing

                if (isCurrentlyFollowing) {
                    followSource.unfollowUser(currentUid, targetUserId)
                } else {
                    followSource.followUser(currentUid, targetUserId)
                }

                // Update state optimistically
                val newFollowersCount = if (isCurrentlyFollowing)
                    currentState.followersCount - 1
                else
                    currentState.followersCount + 1

                _profileState.value = Resource.Success(
                    currentState.copy(
                        isFollowing    = !isCurrentlyFollowing,
                        followersCount = newFollowersCount
                    )
                )
                _followState.value = Resource.Success(!isCurrentlyFollowing)

            } catch (e: Exception) {
                _followState.value = Resource.Error(e.message ?: "Failed")
            }
        }
    }
}