package com.example.uplyft.data.remote.fcm

import android.content.Context
import android.util.Log
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject



class FCMApiService(private val context: Context) {

    private val client = OkHttpClient()

    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val stream      = context.assets.open("service_account.json")
        val credentials = GoogleCredentials
            .fromStream(stream)
            .createScoped("https://www.googleapis.com/auth/firebase.messaging")
        credentials.refreshIfExpired()
        credentials.accessToken.tokenValue
    }

    private suspend fun getProjectId(): String = withContext(Dispatchers.IO) {
        val stream = context.assets.open("service_account.json")
        JSONObject(stream.bufferedReader().readText()).getString("project_id")
    }

    suspend fun sendNotification(
        toToken : String,
        title   : String,
        body    : String,
        data    : Map<String, String> = emptyMap()
    ) = withContext(Dispatchers.IO) {
        try {
            val accessToken = getAccessToken()
            val projectId   = getProjectId()

            val dataJson = JSONObject()
            data.forEach { (k, v) -> dataJson.put(k, v) }

            val payload = JSONObject().apply {
                put("message", JSONObject().apply {
                    put("token", toToken)
                    put("notification", JSONObject().apply {
                        put("title", title)
                        put("body", body)
                    })
                    put("data", dataJson)
                    put("android", JSONObject().apply {
                        put("priority", "high")
                        put("notification", JSONObject().apply {
                            put("sound", "default")
                            put("channel_id", "uplyft_notifications")
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
                .post(
                    payload.toString()
                        .toRequestBody("application/json".toMediaType())
                )
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("FCM", "Failed: ${response.body?.string()}")
            }
        } catch (e: Exception) {
            Log.e("FCM", "Error: ${e.message}")
        }
    }
}