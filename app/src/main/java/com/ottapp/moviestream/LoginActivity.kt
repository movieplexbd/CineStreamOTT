package com.ottapp.moviestream

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.ottapp.moviestream.data.repository.AuthRepository
import com.ottapp.moviestream.databinding.ActivityLoginBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var authRepository: AuthRepository? = null

    private var isSignUpMode = false

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (!idToken.isNullOrEmpty()) {
                setLoading(true)
                lifecycleScope.launch {
                    val repo = authRepository ?: return@launch
                    val res = repo.signInWithGoogle(idToken)
                    res.fold(
                        onSuccess = { goToMain() },
                        onFailure = { showError(friendlyError(it.message)) }
                    )
                }
            } else {
                showError("Google token পাওয়া যায়নি। Firebase Console-এ SHA-1 যোগ করুন।")
            }
        } catch (e: ApiException) {
            showError("Google সাইন-ইন ব্যর্থ (কোড: ${e.statusCode})")
        } catch (e: Exception) {
            showError("Google সাইন-ইন সমস্যা: ${e.message}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        try {
            authRepository = AuthRepository(this)
        } catch (e: Exception) {
            Log.e("LoginActivity", "AuthRepository init failed: ${e.message}", e)
        }

        if (OTTApplication.firebaseReady) {
            binding.btnGoogleSignIn.show()
        } else {
            binding.btnGoogleSignIn.hide()
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnEmailAction.setOnClickListener {
            handleEmailAuth()
        }

        binding.tvToggleMode.setOnClickListener {
            toggleMode()
        }

        binding.btnGoogleSignIn.setOnClickListener {
            launchGoogleSignIn()
        }
    }

    private fun launchGoogleSignIn() {
        val repo = authRepository
        if (repo == null) {
            showError("Firebase সঠিকভাবে চালু হয়নি")
            return
        }
        try {
            val signInClient = repo.getGoogleSignInClient(Constants.WEB_CLIENT_ID)
            signInClient.signOut().addOnCompleteListener {
                try {
                    googleSignInLauncher.launch(signInClient.signInIntent)
                } catch (e: Exception) {
                    showError("Google সাইন-ইন চালু করতে সমস্যা")
                }
            }
        } catch (e: Exception) {
            showError("Google সাইন-ইন কনফিগারেশন সমস্যা")
        }
    }

    private fun toggleMode() {
        isSignUpMode = !isSignUpMode
        if (isSignUpMode) {
            binding.tilConfirmPassword.show()
            binding.tilName.show()
            binding.btnEmailAction.text = getString(R.string.btn_signup)
            binding.tvToggleMode.text = getString(R.string.toggle_to_signin)
            binding.tvAuthTitle.text = getString(R.string.title_signup)
        } else {
            binding.tilConfirmPassword.hide()
            binding.tilName.hide()
            binding.btnEmailAction.text = getString(R.string.btn_signin)
            binding.tvToggleMode.text = getString(R.string.toggle_to_signup)
            binding.tvAuthTitle.text = getString(R.string.title_signin)
        }
    }

    private fun handleEmailAuth() {
        val repo = authRepository
        if (repo == null) {
            showError("Firebase সঠিকভাবে চালু হয়নি। অ্যাপ রিস্টার্ট করুন।")
            return
        }

        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            showError("ইমেইল এবং পাসওয়ার্ড দিন")
            return
        }
        if (password.length < 6) {
            showError("পাসওয়ার্ড কমপক্ষে ৬ অক্ষর হতে হবে")
            return
        }

        if (isSignUpMode) {
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()
            val name = binding.etName.text.toString().trim()
            if (password != confirmPassword) {
                showError("পাসওয়ার্ড মিলছে না")
                return
            }
            if (name.isEmpty()) {
                showError("আপনার নাম দিন")
                return
            }
            setLoading(true)
            lifecycleScope.launch {
                val result = repo.signUpWithEmail(email, password, name)
                result.fold(
                    onSuccess = { goToMain() },
                    onFailure = { showError(friendlyError(it.message)) }
                )
            }
        } else {
            setLoading(true)
            lifecycleScope.launch {
                val result = repo.signInWithEmail(email, password)
                result.fold(
                    onSuccess = { goToMain() },
                    onFailure = { showError(friendlyError(it.message)) }
                )
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun friendlyError(msg: String?): String {
        return when {
            msg == null -> "অজানা সমস্যা হয়েছে"
            msg.contains("password") -> "পাসওয়ার্ড ভুল আছে"
            msg.contains("no user") || msg.contains("identifier") -> "এই ইমেইলে কোনো অ্যাকাউন্ট নেই"
            msg.contains("already in use") -> "এই ইমেইল দিয়ে আগেই অ্যাকাউন্ট আছে"
            msg.contains("badly formatted") || msg.contains("format") -> "ইমেইল সঠিক নয়"
            msg.contains("network") -> "ইন্টারনেট সংযোগ নেই"
            msg.contains("12501") || msg.contains("sign_in_failed") -> "Google সাইন-ইন কনফিগারেশন সমস্যা। SHA-1 যোগ করুন।"
            msg.contains("CONFIGURATION_NOT_FOUND") || msg.contains("API_NOT_AVAILABLE") -> "Firebase কনফিগারেশন সমস্যা"
            else -> msg
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnEmailAction.isEnabled = !loading
        binding.btnGoogleSignIn.isEnabled = !loading
    }

    private fun showError(msg: String) {
        toast(msg)
        setLoading(false)
    }
}
