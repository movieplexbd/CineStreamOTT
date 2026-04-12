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

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val destId = item.itemId

            if (destId == currentTabId) {
                // Same tab tapped again: pop everything back to the tab root
                navController.popBackStack(destId, false)
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

        // Prevent reselect from doing anything extra — handled above
        binding.bottomNavigation.setOnItemReselectedListener { /* handled in setOnItemSelectedListener */ }

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
