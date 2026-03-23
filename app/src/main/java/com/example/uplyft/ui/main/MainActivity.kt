package com.example.uplyft.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.uplyft.R
import com.example.uplyft.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()
        requestNotificationPermission()
        handleNotificationDeepLink()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    // ─────────────────────────────────────────────
    // DEEP LINK — tap notification → navigate
    // ─────────────────────────────────────────────

    private fun handleNotificationDeepLink() {
        val type      = intent.getStringExtra("type")      ?: return
        val postId    = intent.getStringExtra("postId")    ?: ""
        val commentId = intent.getStringExtra("commentId") ?: ""

        // wait for nav controller to be ready
        binding.root.post {
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.navHostFragment) as? NavHostFragment
                ?: return@post
            val navController = navHostFragment.navController

            when (type) {
                "follow",
                "follow_back" -> {
                    // navigate to notifications or profile
                }
                "like_post",
                "comment",
                "reply",
                "like_comment" -> {
                    if (postId.isNotEmpty()) {
                        val bundle = Bundle().apply {
                            putString("postId", postId)
                        }
                        navController.navigate(
                            R.id.postDetailFragment, bundle
                        )
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────
    // NAVIGATION
    // ─────────────────────────────────────────────

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController   = navHostFragment.navController

        binding.bottomNavigationView.setupWithNavController(navController)
        binding.bottomNavigationView.setOnItemReselectedListener { /* no-op */ }

        val mainFragments = setOf(
            R.id.homeFragment,
            R.id.searchFragment,
            R.id.profileFragment
        )

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in mainFragments) {
                binding.bottomNavigationView.visibility = View.VISIBLE
                binding.divider.visibility              = View.VISIBLE
            } else {
                binding.bottomNavigationView.visibility = View.GONE
                binding.divider.visibility              = View.GONE
            }
        }
    }
}