package com.ottapp.moviestream

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.ottapp.moviestream.ui.onboarding.OnboardingActivity
import com.ottapp.moviestream.util.AccessManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_splash)
        } catch (e: Exception) {
            Log.e("SplashActivity", "Layout inflate error: ${e.message}", e)
        }

        lifecycleScope.launch {
            try {
                checkUpdate()
            } catch (e: Exception) {
                Log.e("SplashActivity", "Update check failed: ${e.message}", e)
                proceedAfterDelay()
            }
        }
    }

    private fun proceedAfterDelay() {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!isFinishing && !isDestroyed) {
                    navigate()
                }
            } catch (e: Exception) {
                Log.e("SplashActivity", "Navigation error: ${e.message}", e)
                safeNavigateTo(LoginActivity::class.java)
            }
        }, 1000)
    }

    private suspend fun checkUpdate() {
        val snapshot = FirebaseDatabase.getInstance().getReference("app_update").get().await()
        if (snapshot.exists()) {
            val latestVersion = snapshot.child("version_code").getValue(Int::class.java) ?: 0
            val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode
            val isForce = snapshot.child("force_update").getValue(Boolean::class.java) ?: false
            val updateUrl = snapshot.child("update_url").getValue(String::class.java) ?: ""

            if (latestVersion > currentVersion) {
                showUpdateDialog(isForce, updateUrl)
            } else {
                proceedAfterDelay()
            }
        } else {
            proceedAfterDelay()
        }
    }

    private fun showUpdateDialog(isForce: Boolean, url: String) {
        val builder = AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("A new version of CineStream is available. Please update for the best experience.")
            .setPositiveButton("Update Now") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {}
                if (isForce) finish()
            }
            .setCancelable(!isForce)

        if (!isForce) {
            builder.setNegativeButton("Later") { _, _ -> proceedAfterDelay() }
        }

        builder.show()
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
