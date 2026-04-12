package com.ottapp.moviestream

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.multidex.MultiDexApplication
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase

class OTTApplication : MultiDexApplication(), Configuration.Provider {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "ott_downloads"
        var firebaseReady = false
            private set

        private const val TAG = "OTTApplication"
        private const val DB_URL = "https://movies-bee24-default-rtdb.firebaseio.com"
    }

    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashHandler()
        initFirebaseSafely()
        createNotificationChannels()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "UNCAUGHT EXCEPTION on ${thread.name}: ${throwable.message}", throwable)
            try {
                defaultHandler?.uncaughtException(thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error in default crash handler", e)
            }
        }
    }

    private fun initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            firebaseReady = FirebaseApp.getApps(this).isNotEmpty()

            if (firebaseReady) {
                try {
                    val db = FirebaseDatabase.getInstance(DB_URL)
                    db.setPersistenceEnabled(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Firebase persistence setup: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed: ${e.message}", e)
            firebaseReady = false
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val downloadChannel = NotificationChannel(
                    DOWNLOAD_CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Movie download progress"
                    setSound(null, null)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.createNotificationChannel(downloadChannel)
            } catch (e: Exception) {
                Log.e(TAG, "Notification channel error: ${e.message}", e)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
