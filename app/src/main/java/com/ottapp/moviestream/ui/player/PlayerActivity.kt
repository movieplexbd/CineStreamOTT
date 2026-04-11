package com.ottapp.moviestream.ui.player

import android.app.PictureInPictureParams
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ottapp.moviestream.R
import com.ottapp.moviestream.databinding.ActivityPlayerBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import com.ottapp.moviestream.util.toFormattedTime

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var prefs: SharedPreferences

    private var movieId    = ""
    private var movieTitle = ""
    private var videoUrl   = ""

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private var controlsVisible = true
    private val HIDE_DELAY = 3500L

    private val speedLabels = arrayOf("0.5x","0.75x","1.0x","1.25x","1.5x","2.0x")
    private val speedValues = floatArrayOf(0.5f,0.75f,1.0f,1.25f,1.5f,2.0f)
    private var speedIndex  = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemUI()
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs      = getSharedPreferences("ott_prefs", MODE_PRIVATE)
        movieId    = intent.getStringExtra(Constants.EXTRA_MOVIE_ID)    ?: ""
        movieTitle = intent.getStringExtra(Constants.EXTRA_MOVIE_TITLE) ?: "Movie"
        videoUrl   = intent.getStringExtra(Constants.EXTRA_VIDEO_URL)   ?: ""

        binding.tvPlayerTitle.text = movieTitle
        setupPlayer()
        setupControls()
    }

    // ── Player ───────────────────────────────────────────────────────────────
    private fun setupPlayer() {
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            binding.playerView.useController = false

            exo.setMediaItem(MediaItem.fromUri(videoUrl))
            val saved = prefs.getLong(Constants.PREF_PLAYBACK_POSITION + movieId, 0L)
            if (saved > 5000) exo.seekTo(saved)
            exo.prepare()
            exo.playWhenReady = true

            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> binding.progressBuffering.show()
                        Player.STATE_READY     -> { binding.progressBuffering.hide(); startProgressLoop() }
                        Player.STATE_ENDED     -> { binding.btnPlayPause.setImageResource(R.drawable.ic_replay); savePosition(0L) }
                        else -> {}
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) {
                    binding.btnPlayPause.setImageResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play
                    )
                }
                override fun onPlayerError(error: PlaybackException) {
                    toast("প্লেব্যাক ত্রুটি: ${error.message}")
                    binding.progressBuffering.hide()
                }
            })
        }
    }

    // ── Controls ─────────────────────────────────────────────────────────────
    private fun setupControls() {
        binding.btnBack.setOnClickListener        { onBackPressedDispatcher.onBackPressed() }
        binding.btnPlayPause.setOnClickListener   { player?.run { if (isPlaying) pause() else play() }; resetHide() }
        binding.btnSeekBack.setOnClickListener    { player?.seekTo((player!!.currentPosition - 10_000).coerceAtLeast(0)); resetHide() }
        binding.btnSeekForward.setOnClickListener { player?.seekTo((player!!.currentPosition + 10_000).coerceAtMost(player!!.duration)); resetHide() }
        binding.tvSpeed.setOnClickListener        { showSpeedDialog() }
        binding.btnFullscreen.setOnClickListener  { enterPiP() }
        binding.playerView.setOnClickListener     { toggleControls() }

        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: android.widget.SeekBar)  {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar)   { player?.seekTo(sb.progress.toLong()); resetHide() }
            override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) binding.tvCurrentTime.text = p.toLong().toFormattedTime()
            }
        })
        resetHide()
    }

    // ── Progress loop ────────────────────────────────────────────────────────
    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressLoop = object : Runnable {
        override fun run() {
            player?.let {
                val pos = it.currentPosition
                val dur = it.duration.coerceAtLeast(1L)
                binding.seekBar.max      = dur.toInt()
                binding.seekBar.progress = pos.toInt()
                binding.tvCurrentTime.text = pos.toFormattedTime()
                binding.tvDuration.text    = dur.toFormattedTime()
            }
            progressHandler.postDelayed(this, 500)
        }
    }
    private fun startProgressLoop() = progressHandler.post(progressLoop)

    // ── Controls show/hide ───────────────────────────────────────────────────
    private fun toggleControls() { if (controlsVisible) hideControls() else showControls() }
    private fun showControls() {
        controlsVisible = true
        binding.controlsOverlay.show()
        resetHide()
    }
    private fun hideControls() {
        controlsVisible = false
        binding.controlsOverlay.animate().alpha(0f).setDuration(300).withEndAction {
            binding.controlsOverlay.hide()
            binding.controlsOverlay.alpha = 1f
        }.start()
    }
    private fun resetHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY)
    }

    // ── Speed dialog ─────────────────────────────────────────────────────────
    private fun showSpeedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("প্লেব্যাক স্পিড")
            .setSingleChoiceItems(speedLabels, speedIndex) { dlg, i ->
                speedIndex = i
                player?.setPlaybackSpeed(speedValues[i])
                binding.tvSpeed.text = speedLabels[i]
                dlg.dismiss()
            }.show()
    }

    // ── PiP ──────────────────────────────────────────────────────────────────
    private fun enterPiP() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(inPiP: Boolean, cfg: Configuration) {
        super.onPictureInPictureModeChanged(inPiP, cfg)
        if (inPiP) binding.controlsOverlay.hide() else binding.controlsOverlay.show()
    }

    // ── System UI ─────────────────────────────────────────────────────────────
    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            )
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    private fun savePosition(pos: Long) =
        prefs.edit().putLong(Constants.PREF_PLAYBACK_POSITION + movieId, pos).apply()

    override fun onPause() {
        super.onPause()
        player?.let { savePosition(it.currentPosition); it.pause() }
    }
    override fun onStop() {
        super.onStop()
        progressHandler.removeCallbacks(progressLoop)
    }
    override fun onDestroy() {
        super.onDestroy()
        hideHandler.removeCallbacks(hideRunnable)
        player?.release(); player = null
    }
}
