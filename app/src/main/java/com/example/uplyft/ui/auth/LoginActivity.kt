package com.example.uplyft.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.uplyft.databinding.ActivityLoginBinding
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

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (viewModel.isLoggedIn()) {
            goToMain(); return
        }

        setupClicks()
        observeState()
    }

    private fun setupClicks() {
        binding.btnLogin.setOnClickListener {
            val email    = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (!validateInputs(email, password)) return@setOnClickListener
            viewModel.login(email, password)
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.loginState.collect { state ->
                    when (state) {
                        is Resource.Loading -> setLoading(true)
                        is Resource.Success -> {
                            setLoading(false)
                            goToMain()
                        }
                        is Resource.Error -> {
                            setLoading(false)
                            Toast.makeText(
                                this@LoginActivity,
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

    private fun validateInputs(email: String, password: String): Boolean {
        // Clear previous errors
        binding.tilPassword.error = null

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
        return true
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnLogin.text = if (loading) "Logging in…" else "Login"
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}