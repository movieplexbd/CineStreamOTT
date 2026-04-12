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
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: DownloadRepository
    private lateinit var notifManager: NotificationManager

    private val activeJobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        repo        = DownloadRepository(this)
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

        if (activeJobs.containsKey(movieId)) return START_NOT_STICKY   // already in progress

        DownloadTracker.start(movieId, movieTitle, bannerUrl)

        startForeground(notifId(movieId), buildNotif(movieId, movieTitle, -1))

        val job = scope.launch { downloadFile(movieId, movieTitle, videoUrl, bannerUrl) }
        activeJobs[movieId] = job

        return START_NOT_STICKY
    }

    private fun cancelDownload(movieId: String) {
        activeJobs[movieId]?.cancel()
        activeJobs.remove(movieId)
        DownloadTracker.remove(movieId)
        repo.getTempFile(movieId).delete()
        notifManager.cancel(notifId(movieId))
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
                connect()
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
                        if (now - lastNotify > 400) {
                            val pct = if (total > 0) ((downloaded * 100) / total).toInt() else -1
                            DownloadTracker.update(movieId, pct)
                            withContext(Dispatchers.Main) {
                                notifManager.notify(notifId(movieId), buildNotif(movieId, title, pct))
                            }
                            lastNotify = now
                        }
                    }
                }
            }
            conn.disconnect()

            if (repo.finalizeTempFile(movieId)) {
                val meta = DownloadedMovie(
                    movieId        = movieId,
                    title          = title,
                    bannerImageUrl = bannerUrl,
                    localFilePath  = repo.getLocalFilePath(movieId),
                    fileSize       = downloaded,
                    downloadedAt   = System.currentTimeMillis()
                )
                repo.saveMetadata(meta)
                showDoneNotif(movieId, title, success = true)
            } else {
                showDoneNotif(movieId, title, success = false)
            }

        } catch (e: CancellationException) {
            // silently cancelled
        } catch (e: Exception) {
            tempFile.delete()
            showDoneNotif(movieId, title, success = false)
        } finally {
            DownloadTracker.remove(movieId)
            activeJobs.remove(movieId)
            notifManager.cancel(notifId(movieId))
            withContext(Dispatchers.Main) { if (activeJobs.isEmpty()) stopSelf() }
        }
    }

    // ── Notification helpers ──────────────────────────────────────────────────

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

    private fun buildNotif(movieId: String, title: String, progress: Int): Notification =
        NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle("ডাউনলোড হচ্ছে")
            .setContentText(title)
            .setProgress(100, progress, progress < 0)
            .setOngoing(true)
            .setSilent(true)
            .addAction(R.drawable.ic_close, "বাতিল", cancelPi(movieId))
            .build()

    private fun showDoneNotif(movieId: String, title: String, success: Boolean) {
        val notif = NotificationCompat.Builder(this, OTTApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download_notif)
            .setContentTitle(if (success) "✓ ডাউনলোড সম্পন্ন" else "✗ ডাউনলোড ব্যর্থ")
            .setContentText(if (success) "$title সংরক্ষিত হয়েছে" else "$title ডাউনলোড করা যায়নি")
            .setAutoCancel(true)
            .build()
        notifManager.notify(notifId(movieId), notif)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
