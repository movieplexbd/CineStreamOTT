package com.ottapp.moviestream.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ottapp.moviestream.OTTApplication
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.DownloadedMovie
import com.ottapp.moviestream.data.repository.DownloadRepository
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.DownloadTracker
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadService : Service() {

    companion object {
        const val ACTION_CANCEL         = "action_cancel_download"
        const val EXTRA_MOVIE_ID_CANCEL = "extra_movie_id_cancel"
        private const val TAG = "DownloadService"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var repo: DownloadRepository? = null
    private var notifManager: NotificationManager? = null

    private val activeJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        try {
            repo = DownloadRepository(this)
            notifManager = getSystemService(NotificationManager::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (intent?.action == ACTION_CANCEL) {
                val id = intent.getStringExtra(EXTRA_MOVIE_ID_CANCEL) ?: return START_NOT_STICKY
                cancelDownload(id)
                return START_NOT_STICKY
            }

            val movieId    = intent?.getStringExtra(Constants.EXTRA_MOVIE_ID)    ?: return START_NOT_STICKY
            val movieTitle = intent.getStringExtra(Constants.EXTRA_MOVIE_TITLE) ?: "Movie"
            val videoUrl   = intent.getStringExtra(Constants.EXTRA_VIDEO_URL)   ?: return START_NOT_STICKY
            val bannerUrl  = intent.getStringExtra(Constants.EXTRA_BANNER_URL)  ?: ""

            if (activeJobs.containsKey(movieId)) return START_NOT_STICKY

            DownloadTracker.start(movieId, movieTitle, bannerUrl)

            startForegroundSafe(movieId, movieTitle)

            val job = scope.launch { downloadFile(movieId, movieTitle, videoUrl, bannerUrl) }
            activeJobs[movieId] = job
        } catch (e: Exception) {
            Log.e(TAG, "onStartCommand error: ${e.message}", e)
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun startForegroundSafe(movieId: String, movieTitle: String) {
        try {
            val notification = buildNotif(movieId, movieTitle, -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    startForeground(
                        notifId(movieId),
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground with type failed: ${e.message}")
                    startForeground(notifId(movieId), notification)
                }
            } else {
                startForeground(notifId(movieId), notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed completely: ${e.message}", e)
        }
    }

    private fun cancelDownload(movieId: String) {
        try {
            activeJobs[movieId]?.cancel()
            activeJobs.remove(movieId)
            DownloadTracker.remove(movieId)
            try { repo?.getTempFile(movieId)?.delete() } catch (_: Exception) {}
            try { notifManager?.cancel(notifId(movieId)) } catch (_: Exception) {}
            if (activeJobs.isEmpty()) stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "cancelDownload error: ${e.message}")
        }
    }

    private suspend fun downloadFile(
        movieId: String, title: String, url: String, bannerUrl: String
    ) {
        val dlRepo = repo ?: return
        val tempFile = dlRepo.getTempFile(movieId)
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 30_000
                requestMethod  = "GET"
                setRequestProperty("User-Agent", "CineStream/2.0")
                connect()
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode")
            }

            val total      = conn.contentLengthLong
            var downloaded = 0L
            var lastNotify = 0L

            val input  = BufferedInputStream(conn.inputStream)
            val output = FileOutputStream(tempFile)
            val buf    = ByteArray(8 * 1024)
            var read: Int

            output.use { out ->
                input.use { inp ->
                    while (inp.read(buf).also { read = it } != -1) {
                        if (!coroutineContext.isActive) { tempFile.delete(); return }
                        out.write(buf, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 500) {
                            val pct = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                            DownloadTracker.update(movieId, pct)
                            try {
                                withContext(Dispatchers.Main) {
                                    notifManager?.notify(notifId(movieId), buildNotif(movieId, title, pct))
                                }
                            } catch (_: Exception) {}
                            lastNotify = now
                        }
                    }
                }
            }
            conn.disconnect()

            if (dlRepo.finalizeTempFile(movieId)) {
                val meta = DownloadedMovie(
                    movieId        = movieId,
                    title          = title,
                    bannerImageUrl = bannerUrl,
                    localFilePath  = dlRepo.getLocalFilePath(movieId),
                    fileSize       = downloaded,
                    downloadedAt   = System.currentTimeMillis()
                )
                dlRepo.saveMetadata(meta)
                showDoneNotif(movieId, title, success = true)
            } else {
                showDoneNotif(movieId, title, success = false)
            }

        } catch (e: CancellationException) {
            try { tempFile.delete() } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            try { tempFile.delete() } catch (_: Exception) {}
            showDoneNotif(movieId, title, success = false)
        } finally {
            DownloadTracker.remove(movieId)
            activeJobs.remove(movieId)
            try { notifManager?.cancel(notifId(movieId)) } catch (_: Exception) {}
            try {
                withContext(Dispatchers.Main.immediate) { if (activeJobs.isEmpty()) stopSelf() }
            } catch (_: Exception) { }
        }
    }

    private fun notifId(movieId: String) = movieId.hashCode().and(0x7FFFFFFF)

    private fun cancelPi(movieId: String): PendingIntent {
        val i = Intent(this, DownloadService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_MOVIE_ID_CANCEL, movieId)
        }
        return PendingIntent.getService(
            this, movieId.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildNotif(movieId: String, title: String, progress: Int): Notification {
        return try {
            NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notif)
                .setContentTitle("ডাউনলোড হচ্ছে")
                .setContentText(title)
                .setProgress(100, progress.coerceAtLeast(0), progress < 0)
                .setOngoing(true)
                .setSilent(true)
                .addAction(R.drawable.ic_close, "বাতিল", cancelPi(movieId))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "buildNotif error: ${e.message}")
            NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notif)
                .setContentTitle("ডাউনলোড হচ্ছে")
                .setContentText(title)
                .build()
        }
    }

    private fun showDoneNotif(movieId: String, title: String, success: Boolean) {
        try {
            val notif = NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notif)
                .setContentTitle(if (success) "ডাউনলোড সম্পন্ন" else "ডাউনলোড ব্যর্থ")
                .setContentText(if (success) "$title সংরক্ষিত হয়েছে" else "$title ডাউনলোড করা যায়নি")
                .setAutoCancel(true)
                .build()
            notifManager?.notify(notifId(movieId), notif)
        } catch (e: Exception) {
            Log.e(TAG, "Done notification error: ${e.message}")
        }
    }

    override fun onDestroy() {
        try { scope.cancel() } catch (e: Exception) { }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
