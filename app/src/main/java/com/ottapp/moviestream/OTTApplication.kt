package com.ottapp.moviestream

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.work.Configuration
import com.google.dagger.hilt.android.HiltAndroidApp
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics

@HiltAndroidApp
class OTTApplication : Application(), Configuration.Provider {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "ott_downloads"
        var firebaseReady = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        initFirebaseSafely()
        initCrashlytics()
        createNotificationChannels()
    }

    private fun initFirebaseSafely() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            firebaseReady = FirebaseApp.getApps(this).isNotEmpty()
        } catch (e: Exception) {
            Log.e("OTTApplication", "Firebase init failed: ${e.message}", e)
            firebaseReady = false
        }
    }

    private fun initCrashlytics() {
        try {
            if (firebaseReady) {
                val crashlytics = FirebaseCrashlytics.getInstance()
                crashlytics.setCrashlyticsCollectionEnabled(true)
            }
        } catch (e: Exception) {
            Log.e("OTTApplication", "Crashlytics init failed: ${e.message}", e)
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
                Log.e("OTTApplication", "Notification channel error: ${e.message}", e)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()
}
