package com.ottapp.moviestream

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.ottapp.moviestream.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var navController: NavController? = null

    private var currentTabId: Int = R.id.homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityMainBinding.inflate(layoutInflater)
            setContentView(binding.root)

            val navHost = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            if (navHost == null) {
                Log.e("MainActivity", "NavHostFragment not found")
                return
            }
            navController = navHost.navController

            setupBottomNav()
        } catch (e: Exception) {
            Log.e("MainActivity", "onCreate error: ${e.message}", e)
        }
    }

    private fun setupBottomNav() {
        val nc = navController ?: return

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val destId = item.itemId

            if (destId == currentTabId) {
                return@setOnItemSelectedListener true
            }

            currentTabId = destId

            val navOptions = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                    nc.graph.startDestinationId,
                    inclusive = false,
                    saveState = true
                )
                .build()

            try {
                nc.navigate(destId, null, navOptions)
            } catch (e: Exception) {
                Log.e("MainActivity", "Navigation error: ${e.message}")
            }
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener {
            try {
                val currentDest = nc.currentDestination?.id
                if (currentDest != currentTabId) {
                    nc.popBackStack(currentTabId, false)
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Reselect error: ${e.message}")
            }
        }

        nc.addOnDestinationChangedListener { _, destination, _ ->
            try {
                val tabIds = setOf(
                    R.id.homeFragment, R.id.moviesFragment, R.id.searchFragment,
                    R.id.downloadFragment, R.id.profileFragment
                )
                if (destination.id in tabIds) {
                    currentTabId = destination.id
                    binding.bottomNavigation.selectedItemId = destination.id
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Destination changed error: ${e.message}")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController?.navigateUp() ?: false || super.onSupportNavigateUp()
    }
}
