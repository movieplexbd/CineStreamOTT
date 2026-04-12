package com.ottapp.moviestream

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.ottapp.moviestream.ui.onboarding.OnboardingActivity
import com.ottapp.moviestream.util.AccessManager
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_splash)
        } catch (e: Exception) {
            Log.e("SplashActivity", "Layout inflate error: ${e.message}", e)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!isFinishing && !isDestroyed) {
                    navigate()
                }
            } catch (e: Exception) {
                Log.e("SplashActivity", "Navigation error: ${e.message}", e)
                safeNavigateTo(LoginActivity::class.java)
            }
        }, 1500)
    }

    private fun navigate() {
        if (!OnboardingActivity.isOnboardingShown(this)) {
            safeNavigateTo(OnboardingActivity::class.java)
            return
        }

        val isLoggedIn = try {
            OTTApplication.firebaseReady && FirebaseAuth.getInstance().currentUser != null
        } catch (e: Exception) {
            Log.e("SplashActivity", "Firebase auth check failed: ${e.message}", e)
            false
        }

        if (isLoggedIn) {
            checkAccessThenNavigate()
        } else {
            safeNavigateTo(LoginActivity::class.java)
        }
    }

    private fun checkAccessThenNavigate() {
        lifecycleScope.launch {
            try {
                val access = AccessManager(this@SplashActivity).checkAccess()
                when (access) {
                    is AccessManager.AccessResult.Blocked ->
                        safeNavigateTo(LoginActivity::class.java)
                    else ->
                        safeNavigateTo(MainActivity::class.java)
                }
            } catch (e: Exception) {
                Log.e("SplashActivity", "Access check error: ${e.message}", e)
                safeNavigateTo(MainActivity::class.java)
            }
        }
    }

    private fun safeNavigateTo(cls: Class<*>) {
        try {
            startActivity(Intent(this, cls))
            finish()
        } catch (e: Exception) {
            Log.e("SplashActivity", "Navigate to $cls failed: ${e.message}", e)
            finish()
        }
    }
}
