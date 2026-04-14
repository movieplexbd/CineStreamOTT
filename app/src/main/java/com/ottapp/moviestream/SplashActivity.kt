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
  import com.airbnb.lottie.LottieAnimationView
  import com.airbnb.lottie.LottieDrawable
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

              // Start Lottie loading animation
              val lottieView = findViewById<LottieAnimationView>(R.id.lottie_loading)
              lottieView?.apply {
                  setAnimation(R.raw.lottie_loading)
                  repeatCount = LottieDrawable.INFINITE
                  playAnimation()
              }

              // Fade-in logo
              val logo = findViewById<android.widget.ImageView>(R.id.iv_splash_logo)
              logo?.apply {
                  alpha = 0f
                  animate().alpha(1f).setDuration(600).start()
              }

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
          }, 1500)
      }

      private suspend fun checkUpdate() {
          val snapshot = FirebaseDatabase.getInstance().getReference("app_update").get().await()
          if (snapshot.exists()) {
              val latestVersion = snapshot.child("version_code").getValue(Int::class.java) ?: 0
              val currentVersion = packageManager.getPackageInfo(packageName, 0).versionCode

              if (latestVersion > currentVersion) {
                  val downloadUrl = snapshot.child("download_url").getValue(String::class.java) ?: ""
                  val message = snapshot.child("message").getValue(String::class.java)
                      ?: "নতুন আপডেট পাওয়া গেছে। এখনই আপডেট করুন।"
                  val force = snapshot.child("force_update").getValue(Boolean::class.java) ?: false

                  runOnUiThread {
                      AlertDialog.Builder(this@SplashActivity)
                          .setTitle("আপডেট পাওয়া গেছে 🎬")
                          .setMessage(message)
                          .setCancelable(!force)
                          .setPositiveButton("আপডেট করুন") { _, _ ->
                              try {
                                  startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                                  if (force) finish()
                              } catch (e: Exception) {
                                  if (!force) navigate()
                              }
                          }
                          .apply {
                              if (!force) {
                                  setNegativeButton("পরে করব") { _, _ -> navigate() }
                              }
                          }
                          .show()
                  }
              } else {
                  proceedAfterDelay()
              }
          } else {
              proceedAfterDelay()
          }
      }

      private fun navigate() {
          val user = FirebaseAuth.getInstance().currentUser
          if (user == null) {
              val prefs = getSharedPreferences("onboarding", MODE_PRIVATE)
              if (!prefs.getBoolean("seen", false)) {
                  safeNavigateTo(OnboardingActivity::class.java)
              } else {
                  safeNavigateTo(LoginActivity::class.java)
              }
          } else {
              safeNavigateTo(MainActivity::class.java)
          }
      }

      private fun safeNavigateTo(cls: Class<*>) {
          try {
              if (!isFinishing && !isDestroyed) {
                  startActivity(Intent(this, cls))
                  finish()
              }
          } catch (e: Exception) {
              Log.e("SplashActivity", "Navigate error: ${e.message}")
          }
      }
  }
  