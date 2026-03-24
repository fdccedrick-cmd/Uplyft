package com.example.uplyft.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.content.Context
import android.app.AlertDialog
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.uplyft.R
import com.example.uplyft.databinding.ActivityMainBinding
import com.example.uplyft.utils.NotificationTypes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val PREF_NAME = "permission_denials"
        private const val PREF_APP_PREFS = "app_prefs"
        private const val KEY_FIRST_LAUNCH = "is_first_launch"
        private const val MAX_DENIALS = 2
        private const val NOTIFICATION_PERMISSION_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()

        // Request notification permission with slight delay to ensure smooth UX
        binding.root.postDelayed({
            requestNotificationPermission()
        }, 500)

        binding.root.post {
            handleNotificationDeepLink(intent)
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        saveFcmToken()
    }

    // ─────────────────────────────────────────────
    // FCM TOKEN
    // ─────────────────────────────────────────────

    private fun saveFcmToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uid)
                    .update("fcmToken", token)
            }
    }

    // ─────────────────────────────────────────────
    // NOTIFICATION PERMISSION — Android 13+
    // ─────────────────────────────────────────────

    private fun requestNotificationPermission() {
        Log.d("MainActivity", "requestNotificationPermission called, SDK_INT=${Build.VERSION.SDK_INT}")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

            Log.d("MainActivity", "Notification permission isGranted=$isGranted")

            if (!isGranted) {
                // Check denial count
                val denialCount = getNotificationDenialCount()
                Log.d("MainActivity", "Notification denial count=$denialCount")

                if (denialCount >= MAX_DENIALS) {
                    // After 2 denials, show settings dialog
                    Log.d("MainActivity", "Max denials reached, showing settings dialog")
                    showNotificationSettingsDialog()
                } else {
                    // Request permission
                    Log.d("MainActivity", "Requesting notification permission")
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(permission),
                        NOTIFICATION_PERMISSION_CODE
                    )
                }
            } else {
                Log.d("MainActivity", "Notification permission already granted")
            }
        } else {
            Log.d("MainActivity", "Android version < 13, notification permission not needed")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("MainActivity", "onRequestPermissionsResult: requestCode=$requestCode")

        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, reset denial count
                Log.d("MainActivity", "Notification permission GRANTED")
                resetNotificationDenialCount()
            } else {
                // Permission denied, increment denial count
                val denialCount = incrementNotificationDenialCount()
                Log.d("MainActivity", "Notification permission DENIED, count=$denialCount")

                if (denialCount >= MAX_DENIALS) {
                    // After 2 denials, show settings dialog
                    Log.d("MainActivity", "Showing settings dialog after max denials")
                    showNotificationSettingsDialog()
                }
            }
        }
    }

    private fun getNotificationDenialCount(): Int {
        return getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt("denial_notification", 0)
    }

    private fun incrementNotificationDenialCount(): Int {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt("denial_notification", 0)
        val newCount = currentCount + 1
        prefs.edit().putInt("denial_notification", newCount).apply()
        return newCount
    }

    private fun resetNotificationDenialCount() {
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt("denial_notification", 0)
            .apply()
    }

    private fun showNotificationSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notifications Permission Required")
            .setMessage("You've denied notification permission multiple times. Please enable Notifications in Settings to receive updates.")
            .setPositiveButton("Open Settings") { _, _ ->
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    startActivity(this)
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    // ─────────────────────────────────────────────
    // DEEP LINK — tap notification → navigate
    // ─────────────────────────────────────────────

    private fun handleNotificationDeepLink(intent: Intent?) {
        val type      = intent?.getStringExtra("type")      ?: return
        val postId    = intent.getStringExtra("postId")     ?: ""
        val fromUser  = intent.getStringExtra("fromUserId") ?: ""

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as? NavHostFragment
            ?: return
        val navController = navHostFragment.navController

        when (type) {
            // ✅ like or comment → open post detail
            NotificationTypes.LIKE_POST,
            NotificationTypes.COMMENT,
            NotificationTypes.LIKE_COMMENT -> {
                if (postId.isNotEmpty()) {
                    val bundle = Bundle().apply {
                        putString("postId", postId)
                    }
                    try {
                        navController.navigate(
                            R.id.postDetailFragment, bundle
                        )
                    } catch (e: Exception) {
                        Log.e("DeepLink", "Navigate failed: ${e.message}")
                    }
                }
            }

            // ✅ follow → open that user's profile
            NotificationTypes.FOLLOW,
            NotificationTypes.FOLLOW_BACK -> {
                if (fromUser.isNotEmpty()) {
                    val bundle = Bundle().apply {
                        putString("userId", fromUser)
                    }
                    try {
                        navController.navigate(
                            R.id.userProfileFragment, bundle
                        )
                    } catch (e: Exception) {
                        Log.e("DeepLink", "Navigate failed: ${e.message}")
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // NAVIGATION - Instagram Style
    // ─────────────────────────────────────────────

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController   = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)
        binding.bottomNavigationView.setOnItemReselectedListener { /* no-op */ }

        // Define main tab destinations (root level)
        val mainTabDestinations = setOf(
            R.id.homeFragment,
            R.id.searchFragment,
            R.id.profileFragment,
            R.id.notificationsFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Instagram-style: Show bottom nav only for root-level main tabs
            // Hide for all secondary/detail screens (Comments, UserProfile, PostDetail, etc.)
            val shouldShowBottomNav = destination.id in mainTabDestinations

            binding.bottomNavigationView.visibility = if (shouldShowBottomNav) View.VISIBLE else View.GONE
            binding.divider.visibility = if (shouldShowBottomNav) View.VISIBLE else View.GONE
        }
    }
}