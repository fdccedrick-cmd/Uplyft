package com.example.uplyft.ui.main

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.uplyft.R
import com.example.uplyft.databinding.ActivityMainBinding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNavigation()

    }
    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        val navController = navHostFragment.navController

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