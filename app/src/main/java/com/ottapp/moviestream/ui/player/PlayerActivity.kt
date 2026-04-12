package com.ottapp.moviestream.ui.player

import android.app.PictureInPictureParams
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.ottapp.moviestream.R
import com.ottapp.moviestream.databinding.ActivityPlayerBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import com.ottapp.moviestream.util.toFormattedTime
import kotlin.math.abs

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PlayerActivity"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var prefs: SharedPreferences

    private var movieId    = ""
    private var movieTitle = ""
    private var videoUrl   = ""

    private val hideHandler  = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private var controlsVisible = true
    private val HIDE_DELAY = 3500L

    private val speedLabels = arrayOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
    private val speedValues = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex  = 2

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressLoop = object : Runnable {
        override fun run() {
            updateProgressBar()
            progressHandler.postDelayed(this, 500)
        }
    }

    private lateinit var gestureDetector: GestureDetector
    private var swipeStartX = 0f
    private var swipeStartPos = 0L
    private var isSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityPlayerBinding.inflate(layoutInflater)
            setContentView(binding.root)
            hideSystemUI()

            prefs      = getSharedPreferences("ott_prefs", MODE_PRIVATE)
            movieId    = intent.getStringExtra(Constants.EXTRA_MOVIE_ID)    ?: ""
            movieTitle = intent.getStringExtra(Constants.EXTRA_MOVIE_TITLE) ?: "Movie"
            videoUrl   = intent.getStringExtra(Constants.EXTRA_VIDEO_URL)   ?: ""
            val passedPos = intent.getLongExtra(Constants.EXTRA_START_POS, -1L)
            if (passedPos > 0L) prefs.edit().putLong(Constants.PREF_PLAYBACK_POSITION + movieId, passedPos).apply()

            if (videoUrl.isBlank()) {
                toast("ভিডিও URL পাওয়া যায়নি")
                finish()
                return
            }

            binding.tvPlayerTitle.text = movieTitle
            setupPlayer()
            setupControls()
            setupGestures()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate error: ${e.message}", e)
            toast("Player শুরু করতে সমস্যা")
            finish()
        }
    }

    private fun setupPlayer() {
        try {
            val exo = ExoPlayer.Builder(this).build()
            player = exo
            binding.playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(videoUrl))
            val saved = prefs.getLong(Constants.PREF_PLAYBACK_POSITION + movieId, 0L)
            if (saved > 5000L) exo.seekTo(saved)
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> {
                            binding.progressBuffering.show()
                            binding.loadingOverlay.show()
                        }
                        Player.STATE_READY -> {
                            binding.progressBuffering.hide()
                            binding.loadingOverlay.hide()
                            startProgressLoop()
                        }
                        Player.STATE_ENDED -> {
                            binding.btnPlayPause.setImageResource(R.drawable.ic_replay)
                            savePosition(0L)
                        }
                        else -> {}
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    binding.btnPlayPause.setImageResource(
                        if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    binding.progressBuffering.hide()
                    binding.loadingOverlay.hide()
                    toast("ভিডিও লোড করতে সমস্যা: ${error.message}")
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "setupPlayer error: ${e.message}", e)
            toast("Player শুরু করতে সমস্যা: ${e.message}")
            finish()
        }
    }

    private fun setupControls() {
        binding.controlsOverlay.setOnClickListener {
            if (controlsVisible) {
                hideControls()
            } else {
                showControls()
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener {
            player?.let { p ->
                if (p.playbackState == Player.STATE_ENDED) {
                    p.seekTo(0)
                    p.play()
                } else {
                    if (p.isPlaying) p.pause() else p.play()
                }
                scheduleHide()
            }
        }

        binding.btnSeekBack.setOnClickListener {
            player?.let { p ->
                p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                showSeekAnimation(false)
                scheduleHide()
            }
        }

        binding.btnSeekForward.setOnClickListener {
            player?.let { p ->
                p.seekTo((p.currentPosition + 10_000).coerceAtMost(p.duration.coerceAtLeast(0)))
                showSeekAnimation(true)
                scheduleHide()
            }
        }

        binding.tvSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % speedValues.size
            val speed = speedValues[speedIndex]
            player?.setPlaybackSpeed(speed)
            binding.tvSpeed.text = speedLabels[speedIndex]
            scheduleHide()
        }

        binding.btnPip.setOnClickListener { enterPip() }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player?.let { p ->
                        val dur = p.duration.coerceAtLeast(1)
                        val pos = (progress.toLong() * dur / 1000L).coerceAtLeast(0)
                        binding.tvPosition.text = pos.toFormattedTime()
                    }
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                progressHandler.removeCallbacks(progressLoop)
            }
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                player?.let { p ->
                    val dur = p.duration.coerceAtLeast(1)
                    val pos = (seekBar!!.progress.toLong() * dur / 1000L).coerceAtLeast(0)
                    p.seekTo(pos)
                    startProgressLoop()
                }
                scheduleHide()
            }
        })
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val screenWidth = binding.root.width
                val x = e.x
                if (x < screenWidth / 2) {
                    player?.let { p ->
                        p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                        showSeekAnimation(false)
                    }
                } else {
                    player?.let { p ->
                        p.seekTo((p.currentPosition + 10_000).coerceAtMost(p.duration.coerceAtLeast(0)))
                        showSeekAnimation(true)
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (controlsVisible) hideControls() else showControls()
                return true
            }
        })

        binding.gestureArea.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.x
                    swipeStartPos = player?.currentPosition ?: 0L
                    isSeeking = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - swipeStartX
                    if (abs(deltaX) > 20) {
                        isSeeking = true
                        val seekDelta = (deltaX * 0.3f).toLong() * 1000L
                        val maxDur = player?.duration?.coerceAtLeast(0L) ?: 0L
                        val newPos = (swipeStartPos + seekDelta).coerceIn(0L, maxDur)
                        binding.tvSeekIndicator.text = if (deltaX > 0)
                            "+${(seekDelta / 1000).toInt()}s" else "${(seekDelta / 1000).toInt()}s"
                        binding.tvSeekIndicator.show()
                        binding.tvPosition.text = newPos.toFormattedTime()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isSeeking) {
                        val deltaX = event.x - swipeStartX
                        val seekDelta = (deltaX * 0.3f).toLong() * 1000L
                        val maxDur = player?.duration?.coerceAtLeast(0L) ?: 0L
                        val newPos = (swipeStartPos + seekDelta).coerceIn(0L, maxDur)
                        player?.seekTo(newPos)
                        binding.tvSeekIndicator.hide()
                        scheduleHide()
                    }
                }
            }
            true
        }
    }

    private fun showSeekAnimation(forward: Boolean) {
        try {
            val indicator = if (forward) binding.seekForwardIndicator else binding.seekBackIndicator
            indicator.show()
            indicator.animate().alpha(1f).setDuration(200).withEndAction {
                indicator.animate().alpha(0f).setStartDelay(500).setDuration(300).withEndAction {
                    indicator.hide()
                }.start()
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Seek animation error: ${e.message}")
        }
    }

    private fun startProgressLoop() {
        progressHandler.removeCallbacks(progressLoop)
        progressHandler.post(progressLoop)
    }

    private fun updateProgressBar() {
        player?.let { p ->
            val duration = p.duration.coerceAtLeast(0)
            val position = p.currentPosition
            if (duration > 0) {
                binding.seekBar.progress = ((position * 1000) / duration).toInt()
            }
            binding.tvPosition.text = position.toFormattedTime()
            binding.tvDuration.text = duration.toFormattedTime()
        }
    }

    private fun showControls() {
        controlsVisible = true
        binding.controlsOverlay.animate().alpha(1f).setDuration(200).start()
        binding.controlsOverlay.show()
        scheduleHide()
    }

    private fun hideControls() {
        controlsVisible = false
        binding.controlsOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            if (!controlsVisible) binding.controlsOverlay.visibility = View.INVISIBLE
        }.start()
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY)
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                toast("PiP মোড সাপোর্টেড নয়")
            }
        }
    }

    override fun onPictureInPictureModeChanged(inPiP: Boolean, cfg: Configuration) {
        super.onPictureInPictureModeChanged(inPiP, cfg)
        try {
            if (inPiP) binding.controlsOverlay.hide() else binding.controlsOverlay.show()
        } catch (e: Exception) {
            Log.e(TAG, "PiP mode change error: ${e.message}")
        }
    }

    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { ctrl ->
                    ctrl.hide(WindowInsets.Type.systemBars())
                    ctrl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "hideSystemUI error: ${e.message}")
        }
    }

    private fun savePosition(pos: Long) {
        try {
            prefs.edit().putLong(Constants.PREF_PLAYBACK_POSITION + movieId, pos).apply()
        } catch (e: Exception) {
            Log.e(TAG, "savePosition error: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            player?.let { savePosition(it.currentPosition); it.pause() }
        } catch (e: Exception) {
            Log.e(TAG, "onPause error: ${e.message}")
        }
    }

    override fun onStop() {
        super.onStop()
        progressHandler.removeCallbacks(progressLoop)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        progressHandler.removeCallbacks(progressLoop)
        try {
            player?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Player release error: ${e.message}")
        }
        player = null
    }
}
