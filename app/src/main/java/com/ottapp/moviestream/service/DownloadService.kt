package com.ottapp.moviestream.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ottapp.moviestream.OTTApplication
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.DownloadedMovie
import com.ottapp.moviestream.data.repository.DownloadRepository
import com.ottapp.moviestream.util.Constants
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: DownloadRepository
    private lateinit var notificationManager: NotificationManager

    private val activeJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        repo = DownloadRepository(this)
        notificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val movieId    = intent?.getStringExtra(Constants.EXTRA_MOVIE_ID)    ?: return START_NOT_STICKY
        val movieTitle = intent.getStringExtra(Constants.EXTRA_MOVIE_TITLE) ?: "Movie"
        val videoUrl   = intent.getStringExtra(Constants.EXTRA_VIDEO_URL)   ?: return START_NOT_STICKY
        val bannerUrl  = intent.getStringExtra(Constants.EXTRA_BANNER_URL)  ?: ""

        startForeground(
            Constants.DOWNLOAD_NOTIFICATION_ID,
            buildNotification(movieTitle, 0)
        )

        val job = scope.launch {
            downloadFile(movieId, movieTitle, videoUrl, bannerUrl)
        }
        activeJobs[movieId] = job

        return START_NOT_STICKY
    }

    private suspend fun downloadFile(
        movieId: String,
        title: String,
        url: String,
        bannerUrl: String
    ) {
        val tempFile = repo.getTempFile(movieId)
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.apply {
                connectTimeout = 15_000
                readTimeout    = 30_000
                requestMethod  = "GET"
                connect()
            }

            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L
            var lastNotifyTime  = 0L

            val inputStream  = BufferedInputStream(connection.inputStream)
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int

            outputStream.use { out ->
                inputStream.use { inp ->
                    while (inp.read(buffer).also { bytesRead = it } != -1) {
                        if (!coroutineContext.isActive) {
                            tempFile.delete()
                            return
                        }
                        out.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastNotifyTime > 500) {
                            val progress = if (totalBytes > 0)
                                ((downloadedBytes * 100) / totalBytes).toInt()
                            else -1
                            updateNotification(title, progress)
                            lastNotifyTime = now
                        }
                    }
                }
            }

            connection.disconnect()

            val success = repo.finalizeTempFile(movieId)
            if (success) {
                val meta = DownloadedMovie(
                    movieId      = movieId,
                    title        = title,
                    bannerImageUrl = bannerUrl,
                    localFilePath  = repo.getLocalFilePath(movieId),
                    fileSize       = downloadedBytes,
                    downloadedAt   = System.currentTimeMillis()
                )
                repo.saveMetadata(meta)
                showCompleteNotification(title)
            } else {
                showErrorNotification(title)
            }

        } catch (e: Exception) {
            tempFile.delete()
            showErrorNotification(title)
        } finally {
            activeJobs.remove(movieId)
            if (activeJobs.isEmpty()) stopSelf()
        }
    }

    private fun buildNotification(title: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("ডাউনলোড হচ্ছে")
            .setContentText(title)
            .setProgress(100, progress, progress < 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, progress: Int) {
        notificationManager.notify(
            Constants.DOWNLOAD_NOTIFICATION_ID,
            buildNotification(title, progress)
        )
    }

    private fun showCompleteNotification(title: String) {
        val notif = NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("ডাউনলোড সম্পন্ন")
            .setContentText("$title ডাউনলোড হয়েছে")
            .setAutoCancel(true)
            .build()
        notificationManager.notify(title.hashCode(), notif)
    }

    private fun showErrorNotification(title: String) {
        val notif = NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("ডাউনলোড ব্যর্থ")
            .setContentText("$title ডাউনলোড করা যায়নি")
            .setAutoCancel(true)
            .build()
        notificationManager.notify(title.hashCode() + 1, notif)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
