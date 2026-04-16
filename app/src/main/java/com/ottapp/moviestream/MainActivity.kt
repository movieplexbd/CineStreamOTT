package com.ottapp.moviestream

  import android.Manifest
  import android.content.pm.PackageManager
  import android.os.Build
  import android.os.Bundle
  import android.util.Log
  import android.view.View
  import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
  import androidx.core.app.ActivityCompat
  import androidx.core.content.ContextCompat
  import androidx.navigation.NavController
  import androidx.navigation.NavOptions
  import androidx.navigation.fragment.NavHostFragment
  import com.ottapp.moviestream.databinding.ActivityMainBinding

  @AndroidEntryPoint
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

              val isTablet = resources.getBoolean(R.bool.is_tablet)
              if (isTablet) {
                  setupNavigationRail()
              } else {
                  setupBottomNav()
              }

              requestNotificationPermissionIfNeeded()
          } catch (e: Exception) {
              Log.e("MainActivity", "onCreate error: ${e.message}", e)
          }
      }

      private fun requestNotificationPermissionIfNeeded() {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
          val permission = Manifest.permission.POST_NOTIFICATIONS
          if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return
          ActivityCompat.requestPermissions(this, arrayOf(permission), 1002)
      }

      private fun setupBottomNav() {
          val nc = navController ?: return

          binding.bottomNavigation.setOnItemSelectedListener { item ->
              val destId = item.itemId

              if (destId == currentTabId) {
                  nc.popBackStack(destId, false)
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
                  if (currentDest != null) nc.popBackStack(currentDest, false)
              } catch (e: Exception) {
                  Log.e("MainActivity", "Reselect error: ${e.message}")
              }
          }
      }

      private fun setupNavigationRail() {
          val nc = navController ?: return
          val rail = binding.navigationRail
          if (rail == null || rail.visibility == View.GONE) {
              setupBottomNav()
              return
          }

          rail.visibility = View.VISIBLE

          rail.setOnItemSelectedListener { item ->
              val destId = item.itemId

              if (destId == currentTabId) {
                  nc.popBackStack(destId, false)
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
                  Log.e("MainActivity", "Rail navigation error: ${e.message}")
              }
              true
          }
      }
  }
  