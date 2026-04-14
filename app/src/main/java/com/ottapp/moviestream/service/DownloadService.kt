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
    private lateinit var repo: DownloadRepository
    private lateinit var notifManager: NotificationManager

    private val activeJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        repo         = DownloadRepository(this)
        notifManager = getSystemService(NotificationManager::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(notifId(movieId), buildNotif(movieId, movieTitle, -1, "--"), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(notifId(movieId), buildNotif(movieId, movieTitle, -1, "--"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}", e)
            try { startForeground(notifId(movieId), buildNotif(movieId, movieTitle, -1, "--")) } catch (_: Exception) {}
        }

        val job = scope.launch { downloadFile(movieId, movieTitle, videoUrl, bannerUrl) }
        activeJobs[movieId] = job

        return START_NOT_STICKY
    }

    private fun cancelDownload(movieId: String) {
        activeJobs[movieId]?.cancel()
        activeJobs.remove(movieId)
        DownloadTracker.remove(movieId)
        try { repo.getTempFile(movieId).delete() } catch (_: Exception) {}
        try { notifManager.cancel(notifId(movieId)) } catch (_: Exception) {}
        if (activeJobs.isEmpty()) stopSelf()
    }

    private suspend fun downloadFile(
        movieId: String, title: String, url: String, bannerUrl: String
    ) {
        val tempFile = repo.getTempFile(movieId)
        try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout    = 30_000
                requestMethod  = "GET"
                setRequestProperty("User-Agent", "CineStream/1.0")
                connect()
            }

            val totalBytes = conn.contentLengthLong
            val totalMB    = if (totalBytes > 0) totalBytes / (1024.0 * 1024.0) else -1.0
            val sizeLabel  = if (totalMB > 0) formatSize(totalBytes) else ""

            var downloaded = 0L
            var lastProgress = -1

            BufferedInputStream(conn.inputStream).use { inp ->
                FileOutputStream(tempFile).use { out ->
                    val buf = ByteArray(8192)
                    while (coroutineContext.isActive) {
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n

                        if (totalBytes > 0) {
                            val pct = ((downloaded * 100) / totalBytes).toInt()
                            if (pct != lastProgress) {
                                lastProgress = pct
                                val dlSize   = formatSize(downloaded)
                                val totalStr = if (sizeLabel.isNotEmpty()) " / $sizeLabel" else ""
                                val label    = "$dlSize$totalStr ($pct%)"
                                DownloadTracker.updateProgress(movieId, pct)
                                withContext(Dispatchers.Main.immediate) {
                                    notifManager.notify(notifId(movieId), buildNotif(movieId, title, pct, label))
                                }
                            }
                        }
                    }
                }
            }

            if (!coroutineContext.isActive) {
                tempFile.delete()
                return
            }

            val finalFile = repo.getFinalFile(movieId)
            tempFile.renameTo(finalFile)

            val totalStr = if (sizeLabel.isNotEmpty()) sizeLabel else formatSize(downloaded)
            val movie = DownloadedMovie(
                movieId      = movieId,
                title        = title,
                bannerImageUrl = bannerUrl,
                localFilePath  = finalFile.absolutePath,
                fileSize       = totalStr,
                downloadedAt   = System.currentTimeMillis()
            )
            repo.saveDownload(movie)
            DownloadTracker.complete(movieId)

            withContext(Dispatchers.Main.immediate) {
                showDoneNotif(movieId, title, success = true, sizeLabel = totalStr)
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            DownloadTracker.remove(movieId)
        } catch (e: Exception) {
            Log.e(TAG, "download error: ${e.message}", e)
            tempFile.delete()
            DownloadTracker.error(movieId)
            withContext(Dispatchers.Main.immediate) {
                showDoneNotif(movieId, title, success = false, sizeLabel = "")
            }
        } finally {
            DownloadTracker.remove(movieId)
            activeJobs.remove(movieId)
            try { notifManager.cancel(notifId(movieId)) } catch (_: Exception) {}
            try {
                withContext(Dispatchers.Main.immediate) { if (activeJobs.isEmpty()) stopSelf() }
            } catch (_: Exception) { }
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
            bytes >= 1024 * 1024        -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024               -> "%.1f KB".format(bytes / 1024.0)
            else                        -> "$bytes B"
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

    private fun buildNotif(movieId: String, title: String, progress: Int, sizeLabel: String): Notification {
        val text = if (sizeLabel.isNotEmpty()) "$title  •  $sizeLabel" else title
        return NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("ডাউনলোড হচ্ছে")
            .setContentText(text)
            .setProgress(100, progress.coerceAtLeast(0), progress < 0)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_close, "বাতিল", cancelPi(movieId))
            .build()
    }

    private fun showDoneNotif(movieId: String, title: String, success: Boolean, sizeLabel: String) {
        try {
            val subText = if (success && sizeLabel.isNotEmpty()) "$title  •  $sizeLabel" else title
            val notif = NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download_notif)
                .setContentTitle(if (success) "ডাউনলোড সম্পন্ন" else "ডাউনলোড ব্যর্থ")
                .setContentText(if (success) subText else "$title ডাউনলোড করা যায়নি")
                .setAutoCancel(true)
                .build()
            notifManager.notify(notifId(movieId), notif)
        } catch (e: Exception) {
            Log.e(TAG, "Done notification error: ${e.message}")
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
