package com.ottapp.moviestream

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
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
    private lateinit var authRepository: AuthRepository
    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            account.idToken?.let { token ->
                firebaseSignIn(token)
            } ?: run {
                showError("Google Sign-In ব্যর্থ হয়েছে")
            }
        } catch (e: ApiException) {
            showError("Sign-In Error: ${e.statusCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authRepository = AuthRepository(this)
        googleSignInClient = authRepository.getGoogleSignInClient(Constants.WEB_CLIENT_ID)

        binding.btnGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }
    }

    private fun startGoogleSignIn() {
        setLoading(true)
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private fun firebaseSignIn(idToken: String) {
        lifecycleScope.launch {
            val result = authRepository.signInWithGoogle(idToken)
            result.fold(
                onSuccess = {
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                },
                onFailure = { e ->
                    setLoading(false)
                    showError("Login ব্যর্থ: ${e.message}")
                }
            )
        }
    }

    private fun setLoading(loading: Boolean) {
        if (loading) {
            binding.btnGoogleSignIn.hide()
            binding.progressBar.show()
        } else {
            binding.btnGoogleSignIn.show()
            binding.progressBar.hide()
        }
    }

    private fun showError(msg: String) {
        toast(msg)
        setLoading(false)
    }
}
