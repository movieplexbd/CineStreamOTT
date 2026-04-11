package com.ottapp.moviestream

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 1.5 সেকেন্ড পরে check করবে
        Handler(Looper.getMainLooper()).postDelayed({
            if (auth.currentUser != null) {
                // Already logged in → MainActivity
                startActivity(Intent(this, MainActivity::class.java))
            } else {
                // Not logged in → LoginActivity
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 1500)
    }
}
