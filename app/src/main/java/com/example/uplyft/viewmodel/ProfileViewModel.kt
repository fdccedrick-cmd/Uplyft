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
import com.example.uplyft.data.remote.firebase.FollowFirebaseSource
import com.google.firebase.auth.FirebaseAuth
import com.example.uplyft.utils.UserProfileState
import com.example.uplyft.data.local.entity.FollowEntity


class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val db             = AppDatabase.getInstance(application)
    private val followSource   = FollowFirebaseSource(application.applicationContext)
    private val cloudinary     by lazy { CloudinaryService(application.applicationContext) }

    private val repository = PostRepository(
        context           = application.applicationContext,
        postDao           = AppDatabase.getInstance(application).postDao(),
        firebaseSource    = PostFirebaseSource(),
        cloudinaryService = CloudinaryService(application.applicationContext)
    )

    private val _profileState = MutableStateFlow<Resource<UserProfileState>?>(null)
    val profileState: StateFlow<Resource<UserProfileState>?> = _profileState

    private val _updateState = MutableStateFlow<Resource<String>?>(null)
    val updateState: StateFlow<Resource<String>?> = _updateState

    fun loadProfile(uid: String) {
        viewModelScope.launch {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val isOwn      = currentUid == uid

            _profileState.value = null
            val cachedUser = withContext(Dispatchers.IO) {
                db.userDao().getUserById(uid)?.toDomain()
            }

            val cachedIsFollowing = withContext(Dispatchers.IO) {
                if (isOwn) false
                else {
                    val id = "${currentUid}_${uid}"
                    db.followDao().isFollowing(id) ?: false
                }
            }
            val cachedIsFollowingBack = withContext(Dispatchers.IO) {
                if (isOwn) false
                else db.followDao().isFollowing("${uid}_${currentUid}") ?: false
            }

            if (cachedUser != null) {
                _profileState.value = Resource.Success(
                    UserProfileState(
                        user        = cachedUser,
                        isFollowing = cachedIsFollowing,
                        isFollowingBack = cachedIsFollowingBack,
                        isOwnProfile = isOwn
                    )
                )
            } else {
                _profileState.value = Resource.Loading
            }

            // ─── Step 2: Fetch fresh from Firestore in background ─────
            try {
                val userDoc = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance()
                        .collection(USERS_COLLECTION)
                        .document(uid)
                        .get()
                        .await()
                }
                val freshUser = userDoc.toObject(User::class.java)
                    ?.copy(uid = uid) ?: return@launch

                val followersCount  = withContext(Dispatchers.IO) {
                    followSource.getFollowersCount(uid)
                }
                val followingCount  = withContext(Dispatchers.IO) {
                    followSource.getFollowingCount(uid)
                }
                val freshIsFollowing = withContext(Dispatchers.IO) {
                    if (isOwn) false
                    else followSource.isFollowing(currentUid, uid)
                }
                val freshIsFollowingBack = withContext(Dispatchers.IO) {
                    if (isOwn) false
                    else followSource.isFollowing(uid, currentUid)
                }
                val posts = withContext(Dispatchers.IO) {
                    PostFirebaseSource().fetchUserPosts(uid, currentUserId = currentUid)
                }

                // Update Room cache
                withContext(Dispatchers.IO) {
                    db.userDao().insertUser(freshUser.toEntity())
                    // ✅ sync follow state to Room
                    if (!isOwn) {
                        val followId     = "${currentUid}_${uid}"
                        val followBackId = "${uid}_${currentUid}"   // ← declare here

                        if (freshIsFollowing) {
                            db.followDao().insertFollow(
                                FollowEntity(id = followId,
                                    followerId  = currentUid,
                                    followingId = uid)
                            )
                        } else {
                            db.followDao().deleteFollow(followId)
                        }

                        if (freshIsFollowingBack) {
                            db.followDao().insertFollow(
                                FollowEntity(id = followBackId,
                                    followerId  = uid,
                                    followingId = currentUid)
                            )
                        } else {
                            db.followDao().deleteFollow(followBackId)
                        }
                    }
                }

                // Emit fresh complete state
                _profileState.value = Resource.Success(
                    UserProfileState(
                        user           = freshUser,
                        posts          = posts,
                        followersCount = followersCount,
                        followingCount = followingCount,
                        isFollowing    = freshIsFollowing,
                        isFollowingBack = freshIsFollowingBack,
                        isOwnProfile   = isOwn
                    )
                )
            } catch (e: Exception) {
                if (_profileState.value !is Resource.Success) {
                    _profileState.value = Resource.Error(
                        e.message ?: "Failed to load profile"
                    )
                }
            }
        }
    }

    fun toggleFollow(targetUserId: String) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModelScope.launch {
            try {
                val currentState = (_profileState.value as? Resource.Success)?.data
                    ?: return@launch
                val isCurrentlyFollowing = currentState.isFollowing
                val followId = "${currentUid}_${targetUserId}"

                withContext(Dispatchers.IO) {
                    if (isCurrentlyFollowing) {
                        followSource.unfollowUser(currentUid, targetUserId)
                        db.followDao().deleteFollow(followId)
                    } else {
                        followSource.followUser(currentUid, targetUserId)
                        db.followDao().insertFollow(
                            FollowEntity(
                                id          = followId,
                                followerId  = currentUid,
                                followingId = targetUserId
                            )
                        )
                    }
                }

                val newCount = if (isCurrentlyFollowing)
                    currentState.followersCount - 1
                else
                    currentState.followersCount + 1

                _profileState.value = Resource.Success(
                    currentState.copy(
                        isFollowing    = !isCurrentlyFollowing,
                        followersCount = newCount.coerceAtLeast(0)
                    )
                )
            } catch (e: Exception) {
                // silently fail
            }
        }
    }

    fun updateProfileImage(imageUri: Uri, uid: String) {
        viewModelScope.launch {
            _updateState.value = Resource.Loading
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
                _updateState.value = Resource.Success(imageUrl)
                // Reload profile to reflect new image
                loadProfile(uid)
            } catch (e: Exception) {
                _updateState.value = Resource.Error(e.message ?: "Failed to update photo")
            }
        }
    }

    fun getUserPosts(userId: String): Flow<List<Post>> {
        return repository.observeUserPosts(userId)
    }
}