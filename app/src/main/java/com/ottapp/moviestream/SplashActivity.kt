package com.ottapp.moviestream

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val TAG = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_splash)
        } catch (e: Exception) {
            Log.e(TAG, "Layout inflate error: ${e.message}", e)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, 1500)
    }

    private fun navigateToNextScreen() {
        if (isFinishing || isDestroyed) return

        try {
            val isLoggedIn = try {
                OTTApplication.firebaseReady && FirebaseAuth.getInstance().currentUser != null
            } catch (e: Exception) {
                Log.e(TAG, "Firebase auth check failed: ${e.message}", e)
                false
            }

            val targetClass = if (isLoggedIn) MainActivity::class.java else LoginActivity::class.java
            startActivity(Intent(this, targetClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Navigation error: ${e.message}", e)
            try {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            } catch (e2: Exception) {
                Log.e(TAG, "Fatal navigation error", e2)
                finish()
            }
        }
    }
}
