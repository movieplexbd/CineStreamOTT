package com.ottapp.moviestream

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import com.ottapp.moviestream.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private var currentTabId: Int = R.id.homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        // Handle switching between different tabs
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val destId = item.itemId

            // Don't navigate if already on this tab (let reselect listener handle it)
            if (destId == currentTabId) {
                return@setOnItemSelectedListener true
            }

            currentTabId = destId

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
                // fallback: ignore
            }
            true
        }

        // When user taps the SAME tab they're already on: pop back to the tab root
        // This only fires on explicit user tap of an already-selected item (not on startup)
        binding.bottomNavigation.setOnItemReselectedListener {
            val currentDest = navController.currentDestination?.id
            // Only pop if we're deeper than the tab root (e.g. on a detail screen)
            if (currentDest != currentTabId) {
                navController.popBackStack(currentTabId, false)
            }
        }

        // Keep bottom nav in sync when navigating via code (e.g. search icon in home)
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
