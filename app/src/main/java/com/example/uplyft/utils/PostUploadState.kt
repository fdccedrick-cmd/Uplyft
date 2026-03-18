package com.example.uplyft.utils

sealed class PostUploadState {
    object Saving    : PostUploadState()   // saved locally
    object Uploading : PostUploadState()   // uploading to Cloudinary
    object Syncing   : PostUploadState()   // saving to Firestore
    object Done      : PostUploadState()   // fully complete
    data class Error(val message: String) : PostUploadState()
}