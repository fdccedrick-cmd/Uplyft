package com.example.uplyft

import android.app.Application
import com.cloudinary.android.MediaManager
import com.example.uplyft.utils.Constants
// UplyftApplication.kt
class UplyftApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initCloudinary()
    }

    private fun initCloudinary() {
        val config = hashMapOf(
            "cloud_name" to Constants.CLOUDINARY_CLOUD_NAME,
            "api_key"    to Constants.CLOUDINARY_API_KEY,
            "api_secret" to Constants.CLOUDINARY_API_SECRET
        )
        MediaManager.init(this, config)
    }
}