package com.ottapp.moviestream.ui.admin

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ottapp.moviestream.R
import com.ottapp.moviestream.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityAdminBinding.inflate(layoutInflater)
            setContentView(binding.root)

            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "মাস্টার কন্ট্রোল"

            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.admin_nav_host_fragment) as? NavHostFragment
                ?: run {
                    Log.e("AdminActivity", "NavHostFragment not found — finishing")
                    finish()
                    return
                }

            val navController = navHostFragment.navController
            binding.adminBottomNavigation.setupWithNavController(navController)

            navController.addOnDestinationChangedListener { _, destination, _ ->
                supportActionBar?.title = destination.label
            }
        } catch (e: Exception) {
            Log.e("AdminActivity", "Crash in onCreate: ${e.message}", e)
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
