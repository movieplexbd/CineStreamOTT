package com.ottapp.moviestream

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.ottapp.moviestream.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    // Track current tab destination
    private var currentTabId: Int = R.id.homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        // Custom bottom nav behavior: stay on same fragment, fix back stack
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val destId = item.itemId
            if (destId == currentTabId) {
                // Already on this tab — do nothing (prevent duplicate navigation)
                return@setOnItemSelectedListener true
            }

            currentTabId = destId

            // Navigate to tab, clear back stack up to home, don't recreate if already in stack
            val navOptions = NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(
                    navController.graph.startDestinationId,
                    inclusive = false,
                    saveState = true
                )
                .build()

            try {
                navController.navigate(destId, null, navOptions)
            } catch (e: Exception) {
                // fallback
            }
            true
        }

        // Ignore reselect — stay on same screen
        binding.bottomNavigation.setOnItemReselectedListener { /* do nothing */ }

        // Keep bottom nav in sync when navigating via code
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val tabIds = setOf(
                R.id.homeFragment, R.id.moviesFragment, R.id.searchFragment,
                R.id.downloadFragment, R.id.profileFragment
            )
            if (destination.id in tabIds) {
                currentTabId = destination.id
                binding.bottomNavigation.selectedItemId = destination.id
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
