package com.ottapp.moviestream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.DownloadRepository
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.databinding.FragmentDetailContainerBinding
import com.ottapp.moviestream.service.DownloadService
import com.ottapp.moviestream.ui.player.PlayerActivity
import com.ottapp.moviestream.ui.subscription.SubscriptionActivity
import com.ottapp.moviestream.util.AccessManager
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.TrialManager
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.loadImage
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import com.ottapp.moviestream.util.toFormattedTime
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MovieDetailFragment : Fragment() {

    private var _binding: FragmentDetailContainerBinding? = null
    private val binding get() = _binding!!

    private val movieRepo     = MovieRepository()
    private lateinit var dlRepo: DownloadRepository
    private lateinit var accessManager: AccessManager
    private lateinit var trialManager: TrialManager

    private var currentMovie: Movie? = null
    private var inlinePlayer: ExoPlayer? = null
    private var isPlayerStarted = false

    private val hideHandler  = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hidePlayerControls() }
    private val HIDE_DELAY   = 3500L

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressLoop = object : Runnable {
        override fun run() {
            updateSeekbar()
            progressHandler.postDelayed(this, 500)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dlRepo        = DownloadRepository(requireContext())
        accessManager = AccessManager(requireContext())
        trialManager  = TrialManager(requireContext())

        binding.btnBack.setOnClickListener { findNavController().navigateUp() }

        val movieId = arguments?.getString(Constants.EXTRA_MOVIE_ID)
        if (movieId.isNullOrEmpty()) {
            requireContext().toast("মুভি পাওয়া যায়নি")
            findNavController().navigateUp()
            return
        }
        loadMovie(movieId)
    }

    private fun loadMovie(movieId: String) {
        binding.progressCenter.show()
        binding.layoutContent.hide()

        lifecycleScope.launch {
            try {
                val movie = movieRepo.getMovieById(movieId)
                if (_binding == null) return@launch
                if (movie == null) {
                    requireContext().toast("মুভি পাওয়া যায়নি")
                    findNavController().navigateUp()
                    return@launch
                }
                currentMovie = movie
                binding.progressCenter.hide()
                populateUI(movie)
                binding.layoutContent.show()
            } catch (e: Exception) {
                if (_binding == null) return@launch
                requireContext().toast("লোড করতে সমস্যা: ${e.message}")
                findNavController().navigateUp()
            }
        }
    }

    private fun populateUI(movie: Movie) {
        if (_binding == null) return

        binding.ivThumbnail.loadImage(movie.bannerImageUrl)
        binding.tvTitleOverlay.text = movie.title.orEmpty()
        binding.tvPlayerTitle.text  = movie.title.orEmpty()

        if (movie.testMovie) binding.tvFreeBadge.show() else binding.tvFreeBadge.hide()

        binding.tvRating.text   = "⭐ ${movie.imdbRating}"
        binding.tvCategory.text = movie.category.orEmpty()

        val desc = movie.description.orEmpty()
        binding.tvDescription.text = if (desc.isNotBlank()) desc else "এই মুভি সম্পর্কে বিস্তারিত তথ্য পাওয়া যায়নি।"

        if (movie.year > 0) { binding.tvYear.text = movie.year.toString(); binding.tvYear.show() }
        if (movie.duration.isNotEmpty()) { binding.tvDuration.text = movie.duration; binding.tvDuration.show() }

        if (dlRepo.isDownloaded(movie.id)) {
            binding.btnDownload.text      = "ডাউনলোড হয়েছে ✓"
            binding.btnDownload.isEnabled = false
        }

        lifecycleScope.launch { updateAccessCard(movie) }

        binding.btnWatch.setOnClickListener {
            lifecycleScope.launch { handleWatchPressed(movie) }
        }

        binding.btnGoSubscribe.setOnClickListener { goToSubscription() }
        binding.btnDownload.setOnClickListener {
            lifecycleScope.launch { handleDownloadPressed(movie) }
        }

        setupPlayerControls(movie)
    }

    private fun setupPlayerControls(movie: Movie) {
        // Tap area toggles controls during playback
        binding.playerTapArea.setOnClickListener {
            if (_binding == null) return@setOnClickListener
            if (binding.playerControls.visibility == View.VISIBLE) hidePlayerControls()
            else showPlayerControls()
        }

        binding.btnCtrlPlayPause.setOnClickListener {
            inlinePlayer?.let { p ->
                if (p.playbackState == Player.STATE_ENDED) { p.seekTo(0); p.play() }
                else if (p.isPlaying) p.pause() else p.play()
                scheduleHide()
            }
        }

        binding.btnCtrlSeekBack.setOnClickListener {
            inlinePlayer?.let { p ->
                p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                scheduleHide()
            }
        }

        binding.btnCtrlSeekForward.setOnClickListener {
            inlinePlayer?.let { p ->
                val max = p.duration.coerceAtLeast(0)
                p.seekTo((p.currentPosition + 10_000).coerceAtMost(max))
                scheduleHide()
            }
        }

        binding.ctrlSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    inlinePlayer?.let { p ->
                        val pos = (progress.toLong() * p.duration.coerceAtLeast(1) / 1000L).coerceAtLeast(0)
                        binding.tvCtrlPosition.text = pos.toFormattedTime()
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { progressHandler.removeCallbacks(progressLoop) }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                inlinePlayer?.let { p ->
                    val pos = (sb!!.progress.toLong() * p.duration.coerceAtLeast(1) / 1000L)
                    p.seekTo(pos)
                    startProgressLoop()
                }
                scheduleHide()
            }
        })

        binding.btnFullscreen.setOnClickListener {
            currentMovie?.let { m ->
                val pos = inlinePlayer?.currentPosition ?: 0L
                inlinePlayer?.pause()
                openPlayerActivity(m, pos)
            }
        }
    }

    private suspend fun updateAccessCard(movie: Movie) {
        if (_binding == null) return
        val access = try { accessManager.checkAccess() } catch (e: Exception) { null }
        if (_binding == null) return

        binding.cardSubscriptionInfo.show()

        when (access) {
            is AccessManager.AccessResult.Premium -> {
                binding.tvAccessIcon.text  = "👑"
                binding.tvAccessTitle.text = "প্রিমিয়াম সদস্য"
                binding.tvAccessSub.text   = "আপনার সাবস্ক্রিপশন সক্রিয় আছে"
                binding.layoutFeatures.hide()
                binding.btnGoSubscribe.hide()
            }
            is AccessManager.AccessResult.Pending -> {
                binding.tvAccessIcon.text  = "⏳"
                binding.tvAccessTitle.text = "পেমেন্ট যাচাই হচ্ছে"
                binding.tvAccessSub.text   = "অ্যাডমিন অনুমোদনের পর সম্পূর্ণ অ্যাক্সেস পাবেন"
                binding.layoutFeatures.hide()
                binding.btnGoSubscribe.hide()
            }
            is AccessManager.AccessResult.Trial -> {
                val remaining = trialManager.getRemainingTrialText()
                binding.tvAccessIcon.text  = "🆓"
                binding.tvAccessTitle.text = "ফ্রি ট্রায়াল — $remaining"
                binding.tvAccessSub.text   = "ট্রায়াল শেষে মাত্র ৳১০-তে সাবস্ক্রাইব করুন"
                binding.layoutFeatures.show()
                binding.btnGoSubscribe.show()
                binding.btnGoSubscribe.text = "সাবস্ক্রাইব করুন — মাত্র ৳১০"
            }
            is AccessManager.AccessResult.NoAccess -> {
                binding.tvAccessIcon.text  = "🔒"
                binding.tvAccessTitle.text = "ট্রায়াল শেষ হয়েছে"
                binding.tvAccessSub.text   = "সব মুভি দেখতে সাবস্ক্রাইব করুন"
                binding.layoutFeatures.show()
                binding.btnGoSubscribe.show()
                binding.btnGoSubscribe.text = "🔥 সাবস্ক্রাইব করুন — মাত্র ৳১০"
            }
            is AccessManager.AccessResult.Blocked -> {
                binding.tvAccessIcon.text  = "🚫"
                binding.tvAccessTitle.text = "অ্যাকাউন্ট ব্লক করা হয়েছে"
                binding.tvAccessSub.text   = "বিস্তারিত জানতে সাপোর্টে যোগাযোগ করুন"
                binding.layoutFeatures.hide()
                binding.btnGoSubscribe.hide()
            }
            else -> binding.cardSubscriptionInfo.hide()
        }
    }

    private suspend fun handleWatchPressed(movie: Movie) {
        if (_binding == null) return
        if (movie.testMovie) {
            val url = if (dlRepo.isDownloaded(movie.id)) dlRepo.getLocalFilePath(movie.id) else movie.videoStreamUrl
            startInlinePlayer(movie, url)
            return
        }
        val access = try { accessManager.checkAccess() } catch (e: Exception) { null }
        if (_binding == null) return
        when (access) {
            is AccessManager.AccessResult.Allowed,
            is AccessManager.AccessResult.Trial,
            is AccessManager.AccessResult.Premium,
            is AccessManager.AccessResult.Pending -> {
                val url = if (dlRepo.isDownloaded(movie.id)) dlRepo.getLocalFilePath(movie.id) else movie.videoStreamUrl
                startInlinePlayer(movie, url)
            }
            is AccessManager.AccessResult.Blocked ->
                requireContext().toast("আপনার অ্যাকাউন্ট ব্লক করা হয়েছে")
            else -> goToSubscription()
        }
    }

    private fun startInlinePlayer(movie: Movie, url: String) {
        if (_binding == null) return
        if (url.isBlank()) {
            requireContext().toast("ভিডিও পাওয়া যায়নি")
            return
        }

        binding.ivThumbnail.hide()
        binding.btnWatch.hide()
        binding.tvTitleOverlay.hide()
        binding.tvFreeBadge.hide()
        binding.inlinePlayerView.show()
        binding.progressPlayer.show()
        binding.playerTapArea.show()

        val exo = ExoPlayer.Builder(requireContext()).build()
        inlinePlayer = exo
        binding.inlinePlayerView.player = exo
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
        isPlayerStarted = true

        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (_binding == null) return
                when (state) {
                    Player.STATE_READY -> {
                        binding.progressPlayer.hide()
                        showPlayerControls()
                        startProgressLoop()
                        binding.tvCtrlDuration.text = exo.duration.coerceAtLeast(0).toFormattedTime()
                    }
                    Player.STATE_BUFFERING -> binding.progressPlayer.show()
                    Player.STATE_ENDED -> {
                        binding.progressPlayer.hide()
                        progressHandler.removeCallbacks(progressLoop)
                        binding.btnCtrlPlayPause.setImageResource(R.drawable.ic_replay)
                        showPlayerControls()
                    }
                    else -> {}
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (_binding == null) return
                binding.btnCtrlPlayPause.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (_binding == null) return
                binding.progressPlayer.hide()
                requireContext().toast("ভিডিও লোড হচ্ছে না, ফুলস্ক্রিনে চেষ্টা করুন")
                openPlayerActivity(movie, 0L)
            }
        })
    }

    private fun showPlayerControls() {
        if (_binding == null) return
        binding.playerControls.show()
        scheduleHide()
    }

    private fun hidePlayerControls() {
        if (_binding == null) return
        binding.playerControls.hide()
    }

    private fun scheduleHide() {
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, HIDE_DELAY)
    }

    private fun startProgressLoop() {
        progressHandler.removeCallbacks(progressLoop)
        progressHandler.post(progressLoop)
    }

    private fun updateSeekbar() {
        if (_binding == null) return
        inlinePlayer?.let { p ->
            val dur = p.duration.coerceAtLeast(0)
            val pos = p.currentPosition
            if (dur > 0) binding.ctrlSeekBar.progress = ((pos * 1000) / dur).toInt()
            binding.tvCtrlPosition.text = pos.toFormattedTime()
            binding.tvCtrlDuration.text = dur.toFormattedTime()
        }
    }

    private fun openPlayerActivity(movie: Movie, startPosition: Long = 0L) {
        val url = if (dlRepo.isDownloaded(movie.id)) dlRepo.getLocalFilePath(movie.id) else movie.videoStreamUrl
        if (url.isBlank()) { requireContext().toast("ভিডিও পাওয়া যায়নি"); return }
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
            putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
            putExtra(Constants.EXTRA_VIDEO_URL,   url)
            putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
            putExtra(Constants.EXTRA_IS_LOCAL,    dlRepo.isDownloaded(movie.id))
            putExtra(Constants.EXTRA_START_POS,   startPosition)
        })
    }

    private suspend fun handleDownloadPressed(movie: Movie) {
        if (_binding == null) return
        if (movie.testMovie) { startDownload(movie); return }
        val access = try { accessManager.checkAccess() } catch (e: Exception) { null }
        if (_binding == null) return
        when (access) {
            is AccessManager.AccessResult.Allowed,
            is AccessManager.AccessResult.Trial,
            is AccessManager.AccessResult.Premium,
            is AccessManager.AccessResult.Pending -> startDownload(movie)
            is AccessManager.AccessResult.Blocked ->
                requireContext().toast("আপনার অ্যাকাউন্ট ব্লক করা হয়েছে")
            else -> goToSubscription()
        }
    }

    private fun goToSubscription() {
        startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
    }

    private fun startDownload(movie: Movie) {
        val intent = Intent(requireContext(), DownloadService::class.java).apply {
            putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
            putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
            putExtra(Constants.EXTRA_VIDEO_URL,   movie.downloadUrl.ifEmpty { movie.videoStreamUrl })
            putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        requireContext().toast("ডাউনলোড শুরু হয়েছে...")
        try { findNavController().navigate(R.id.action_global_to_download) } catch (e: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        inlinePlayer?.pause()
        progressHandler.removeCallbacks(progressLoop)
        hideHandler.removeCallbacks(hideRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (isPlayerStarted) {
            inlinePlayer?.play()
            startProgressLoop()
        }
    }

    override fun onDestroyView() {
        progressHandler.removeCallbacks(progressLoop)
        hideHandler.removeCallbacks(hideRunnable)
        inlinePlayer?.release()
        inlinePlayer = null
        _binding = null
        super.onDestroyView()
    }
}
