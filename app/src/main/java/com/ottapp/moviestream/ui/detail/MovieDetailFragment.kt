package com.ottapp.moviestream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.Actor
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.model.DownloadQuality
import com.ottapp.moviestream.data.repository.ActorRepository
import com.ottapp.moviestream.data.repository.DownloadRepository
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.data.repository.UserRepository
import com.ottapp.moviestream.databinding.FragmentDetailContainerBinding
import com.ottapp.moviestream.service.DownloadService
import com.ottapp.moviestream.ui.player.PlayerActivity
import com.ottapp.moviestream.ui.subscription.SubscriptionDialog
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.loadImage
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import com.ottapp.moviestream.util.WatchlistManager
import kotlinx.coroutines.launch

class MovieDetailFragment : Fragment() {

    private var _binding: FragmentDetailContainerBinding? = null
    private val binding get() = _binding!!

    private val movieRepo = MovieRepository()
    private val actorRepo = ActorRepository()
    private val userRepo  = UserRepository()
    private lateinit var dlRepo: DownloadRepository

    private var currentMovie: Movie? = null
    private lateinit var watchlistManager: WatchlistManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dlRepo = DownloadRepository(requireContext())
        watchlistManager = WatchlistManager(requireContext())

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

        // Strictly use detailThumbnailUrl for the detail page hero image
        binding.ivBanner.loadImage(movie.detailThumbnailUrl)
        binding.tvTitleOverlay.text = movie.title.orEmpty()

        if (movie.testMovie) binding.tvFreeBadge.show() else binding.tvFreeBadge.hide()

        binding.tvRating.text = "⭐ ${movie.imdbRating}"
        binding.tvCategory.text = movie.category.orEmpty()
        binding.tvDescription.text = movie.description.orEmpty()

        if (movie.year > 0) {
            binding.tvYear.text = movie.year.toString()
            binding.tvYear.show()
        }
        if (movie.duration.isNotEmpty()) {
            binding.tvDuration.text = movie.duration
            binding.tvDuration.show()
        }

        // Load Actors
        if (movie.actorIds.isNotEmpty()) {
            loadActors(movie.actorIds)
        } else {
            binding.layoutActors.hide()
        }

        // SETUP DOWNLOAD BUTTONS
        setupDownloadButtons(movie)

        // Watch button — opens player directly
        binding.btnWatch.setOnClickListener {
            lifecycleScope.launch {
                val user = try { userRepo.getCurrentUser() } catch (e: Exception) { null }
                if (_binding == null) return@launch

                if (movie.testMovie || user?.isPremium == true) {
                    val url = if (dlRepo.isDownloaded(movie.id)) {
                        dlRepo.getLocalFilePath(movie.id)
                    } else {
                        movie.videoStreamUrl
                    }
                    openPlayer(movie, url)
                } else {
                    binding.layoutLocked.show()
                    binding.btnSubscribe.setOnClickListener { openSubscriptionDialog() }
                }
            }
        }

        // Watchlist button
        try {
            val wlBtn = binding.btnWatchlist
            if (wlBtn != null) {
                val inList = watchlistManager.isInWatchlist(movie.id)
                wlBtn.text = if (inList) "ওয়াচলিস্টে আছে ✓" else "ওয়াচলিস্টে যোগ করুন"
                wlBtn.setOnClickListener {
                    val added = watchlistManager.toggleWatchlist(movie)
                    wlBtn.text = if (added) "ওয়াচলিস্টে আছে ✓" else "ওয়াচলিস্টে যোগ করুন"
                    requireContext().toast(if (added) "ওয়াচলিস্টে যোগ হয়েছে!" else "ওয়াচলিস্ট থেকে সরানো হয়েছে")
                }
            }
        } catch (e: Exception) { }
    }

    private fun setupDownloadButtons(movie: Movie) {
        binding.layoutDownloads.removeAllViews()
        
        val qualities = movie.downloads.ifEmpty {
            // Fallback to legacy downloadUrl if downloads list is empty
            if (movie.downloadUrl.isNotEmpty()) {
                listOf(DownloadQuality("Download", movie.downloadUrl, ""))
            } else {
                emptyList()
            }
        }

        if (qualities.isEmpty()) {
            binding.layoutDownloads.hide()
            return
        }

        binding.layoutDownloads.show()
        
        qualities.forEach { quality ->
            val btn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            params.setMargins(8, 0, 8, 0)
            btn.layoutParams = params
            
            // Text with quality and size
            val btnText = if (quality.size.isNotEmpty()) {
                "⬇ ${quality.quality}\n(${quality.size})"
            } else {
                "⬇ ${quality.quality}"
            }
            btn.text = btnText
            btn.textSize = 12f
            btn.setPadding(0, 20, 0, 20)
            btn.setLineSpacing(0f, 0.8f)
            
            btn.setTextColor(ContextCompat.getColor(requireContext(), R.color.red))
            btn.setStrokeColorResource(R.color.red)
            btn.strokeWidth = 3
            btn.cornerRadius = 20
            
            if (dlRepo.isDownloaded(movie.id)) {
                btn.alpha = 0.5f
                btn.isEnabled = false
                btn.text = "ডাউনলোড\nহয়েছে ✓"
            } else {
                btn.setOnClickListener {
                    handleDownloadClick(movie, quality.url)
                }
            }
            
            binding.layoutDownloads.addView(btn)
        }
    }

    private fun handleDownloadClick(movie: Movie, downloadUrl: String) {
        lifecycleScope.launch {
            val user = try { userRepo.getCurrentUser() } catch (e: Exception) { null }
            if (_binding == null) return@launch

            if (movie.testMovie || user?.isPremium == true) {
                startDownload(movie, downloadUrl)
                // Navigate to download tab
                try {
                    findNavController().navigate(R.id.action_global_to_download)
                } catch (e: Exception) {
                    requireContext().toast("ডাউনলোড শুরু হয়েছে")
                }
            } else {
                binding.layoutLocked.show()
                binding.btnSubscribe.setOnClickListener { openSubscriptionDialog() }
            }
        }
    }

    private fun openSubscriptionDialog() {
        try {
            if (!isAdded || parentFragmentManager.isStateSaved) return
            SubscriptionDialog.newInstance().show(parentFragmentManager, SubscriptionDialog.TAG)
        } catch (e: Exception) {
            requireContext().toast("সাবস্ক্রিপশন পেজ খুলতে সমস্যা")
        }
    }

    private fun openPlayer(movie: Movie, url: String) {
        if (url.isBlank()) {
            requireContext().toast("ভিডিও পাওয়া যায়নি")
            return
        }
        val isLocal = dlRepo.isDownloaded(movie.id)
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
            putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
            putExtra(Constants.EXTRA_VIDEO_URL,   url)
            putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
            putExtra(Constants.EXTRA_IS_LOCAL,    isLocal)
        }
        startActivity(intent)
    }

    private fun startDownload(movie: Movie, downloadUrl: String) {
        val intent = Intent(requireContext(), DownloadService::class.java).apply {
            putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
            putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
            putExtra(Constants.EXTRA_VIDEO_URL,   downloadUrl)
            putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
        requireContext().toast("ডাউনলোড শুরু হয়েছে...")
    }

    private fun loadActors(actorIds: List<String>) {
        lifecycleScope.launch {
            try {
                val actors = actorRepo.getActorsByIds(actorIds)
                if (_binding == null) return@launch
                
                if (actors.isNotEmpty()) {
                    binding.layoutActors.show()
                    val adapter = MovieActorAdapter { actor ->
                        val bundle = Bundle().apply { putString("actor_id", actor.id) }
                        findNavController().navigate(R.id.action_detail_to_actor, bundle)
                    }
                    binding.rvActors.adapter = adapter
                    adapter.submitList(actors)
                } else {
                    binding.layoutActors.hide()
                }
            } catch (e: Exception) {
                binding.layoutActors.hide()
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
