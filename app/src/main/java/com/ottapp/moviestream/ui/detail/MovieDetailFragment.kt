package com.ottapp.moviestream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class MovieDetailFragment : Fragment() {

    private var _binding: FragmentDetailContainerBinding? = null
    private val binding get() = _binding!!

    private val movieRepo = MovieRepository()
    private lateinit var dlRepo: DownloadRepository
    private lateinit var accessManager: AccessManager
    private lateinit var trialManager: TrialManager

    private var currentMovie: Movie? = null
    private var inlinePlayer: ExoPlayer? = null
    private var isPlayerStarted = false

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

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

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

        // Thumbnail (shown before playback starts)
        binding.ivThumbnail.loadImage(movie.bannerImageUrl)
        binding.tvTitleOverlay.text = movie.title.orEmpty()

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

        // Show subscription/trial status card
        lifecycleScope.launch { updateAccessCard(movie) }

        // Watch (play inline) button
        binding.btnWatch.setOnClickListener {
            lifecycleScope.launch { handleWatchPressed(movie) }
        }

        // Fullscreen → open PlayerActivity from current position
        binding.btnFullscreen.setOnClickListener {
            currentMovie?.let { m ->
                val pos = inlinePlayer?.currentPosition ?: 0L
                inlinePlayer?.pause()
                openPlayerActivity(m, pos)
            }
        }

        // Download button
        binding.btnDownload.setOnClickListener {
            lifecycleScope.launch { handleDownloadPressed(movie) }
        }

        // Subscribe button
        binding.btnGoSubscribe.setOnClickListener { goToSubscription() }
    }

    private suspend fun updateAccessCard(movie: Movie) {
        if (_binding == null) return
        val access = try { accessManager.checkAccess() } catch (e: Exception) { null }
        if (_binding == null) return

        val card = binding.cardSubscriptionInfo
        card.show()

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
                binding.tvAccessTitle.text = "ফ্রি ট্রায়াল চলছে — $remaining"
                binding.tvAccessSub.text   = "ট্রায়াল শেষ হলে সাবস্ক্রাইব করুন মাত্র ৳১০-তে"
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
            else -> {
                card.hide()
            }
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

        // Hide thumbnail overlay, show player
        binding.playOverlay.hide()
        binding.ivThumbnail.hide()
        binding.inlinePlayerView.show()
        binding.progressPlayer.show()
        binding.btnFullscreen.show()
        binding.tvFreeBadge.hide()
        binding.btnWatch.hide()

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
                    Player.STATE_READY     -> binding.progressPlayer.hide()
                    Player.STATE_BUFFERING -> binding.progressPlayer.show()
                    Player.STATE_ENDED     -> {
                        binding.progressPlayer.hide()
                        // re-show play overlay on end
                        binding.playOverlay.show()
                        binding.ivThumbnail.show()
                        binding.inlinePlayerView.hide()
                        binding.btnWatch.text = "↩  পুনরায় দেখুন"
                        binding.btnWatch.show()
                        binding.btnFullscreen.hide()
                    }
                    else -> {}
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (_binding == null) return
                binding.progressPlayer.hide()
                requireContext().toast("ভিডিও লোড হচ্ছে না, ফুলস্ক্রিনে চেষ্টা করুন")
                // Fallback: open in PlayerActivity
                openPlayerActivity(movie, 0L)
            }
        })
    }

    private fun openPlayerActivity(movie: Movie, startPosition: Long = 0L) {
        val url = if (dlRepo.isDownloaded(movie.id)) dlRepo.getLocalFilePath(movie.id) else movie.videoStreamUrl
        if (url.isBlank()) { requireContext().toast("ভিডিও পাওয়া যায়নি"); return }
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
            putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
            putExtra(Constants.EXTRA_VIDEO_URL,   url)
            putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
            putExtra(Constants.EXTRA_IS_LOCAL,    dlRepo.isDownloaded(movie.id))
            putExtra(Constants.EXTRA_START_POS,   startPosition)
        }
        startActivity(intent)
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

    private fun releasePlayer() {
        inlinePlayer?.release()
        inlinePlayer = null
    }

    override fun onPause() {
        super.onPause()
        inlinePlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (isPlayerStarted) inlinePlayer?.play()
    }

    override fun onDestroyView() {
        releasePlayer()
        _binding = null
        super.onDestroyView()
    }
}
