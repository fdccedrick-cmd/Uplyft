package com.example.uplyft.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.uplyft.databinding.ActivitySignupBinding
import com.example.uplyft.ui.main.MainActivity
import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.uplyft.viewmodel.AuthViewModel
import com.example.uplyft.utils.Resource
import kotlinx.coroutines.launch

// ui/auth/SignupActivity.kt
class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClicks()
        observeState()
    }

    private fun setupClicks() {
        binding.btnSignUp.setOnClickListener {
            val fullName = binding.etFullName.text.toString().trim()
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConPassword.text.toString().trim()
            if (!validateInputs(fullName, email, password, confirmPassword)) return@setOnClickListener
            viewModel.signup(email, password, fullName)
        }

        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.signupState.collect { state ->
                    when (state) {
                        is Resource.Loading -> setLoading(true)
                        is Resource.Success -> {
                            setLoading(false)
                            startActivity(Intent(this@SignupActivity, MainActivity::class.java))
                            finish()
                        }
                        is Resource.Error -> {
                            setLoading(false)
                            Toast.makeText(
                                this@SignupActivity,
                                state.message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        null -> Unit
                    }
                }
            }
        }
    }

    private fun validateInputs(fullName: String, email: String, password: String, confirmPassword: String): Boolean {
        // Clear previous errors
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Full name is required"
            binding.etFullName.requestFocus()
            return false
        }
        if (email.isEmpty()) {
            binding.etEmail.error = "Email is required"
            binding.etEmail.requestFocus()
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Enter a valid email"
            binding.etEmail.requestFocus()
            return false
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Password is required"
            binding.etPassword.requestFocus()
            return false
        }
        if (password.length < 6) {
            binding.tilPassword.error = "Min 6 characters"
            binding.etPassword.requestFocus()
            return false
        }
        if (confirmPassword.isEmpty()) {
            binding.tilConfirmPassword.error = "Confirm password is required"
            binding.etConPassword.requestFocus()
            return false
        }
        if (password != confirmPassword) {
            binding.tilConfirmPassword.error = "Passwords do not match"
            binding.etConPassword.requestFocus()
            return false
        }
        return true
    }

    private fun setLoading(loading: Boolean) {
        binding.btnSignUp.isEnabled = !loading
        binding.btnSignUp.text = if (loading) "Creating account…" else "Sign up"
    }
}