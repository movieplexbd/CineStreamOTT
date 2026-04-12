package com.ottapp.moviestream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
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
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.loadImage
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch

class MovieDetailFragment : Fragment() {

    private var _binding: FragmentDetailContainerBinding? = null
    private val binding get() = _binding!!

    private val movieRepo = MovieRepository()
    private lateinit var dlRepo: DownloadRepository
    private lateinit var accessManager: AccessManager

    private var currentMovie: Movie? = null

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

        binding.ivBanner.loadImage(movie.bannerImageUrl)
        binding.tvTitleOverlay.text = movie.title.orEmpty()

        if (movie.testMovie) binding.tvFreeBadge.show() else binding.tvFreeBadge.hide()

        binding.tvRating.text    = "⭐ ${movie.imdbRating}"
        binding.tvCategory.text  = movie.category.orEmpty()
        binding.tvDescription.text = movie.description.orEmpty()

        if (movie.year > 0) {
            binding.tvYear.text = movie.year.toString()
            binding.tvYear.show()
        }
        if (movie.duration.isNotEmpty()) {
            binding.tvDuration.text = movie.duration
            binding.tvDuration.show()
        }

        if (dlRepo.isDownloaded(movie.id)) {
            binding.btnDownload.text      = "ডাউনলোড হয়েছে ✓"
            binding.btnDownload.isEnabled = false
        } else {
            binding.btnDownload.text      = "ডাউনলোড করুন"
            binding.btnDownload.isEnabled = true
        }

        binding.btnWatch.setOnClickListener {
            lifecycleScope.launch {
                if (_binding == null) return@launch
                if (movie.testMovie) {
                    val url = if (dlRepo.isDownloaded(movie.id)) dlRepo.getLocalFilePath(movie.id)
                              else movie.videoStreamUrl
                    openPlayer(movie, url)
                    return@launch
                }
                val access = try { accessManager.checkAccess() } catch (e: Exception) { null }
                if (_binding == null) return@launch

                when (access) {
                    is AccessManager.AccessResult.Allowed,
                    is AccessManager.AccessResult.Trial,
                    is AccessManager.AccessResult.Premium,
                    is AccessManager.AccessResult.Pending -> {
                        val url = if (dlRepo.isDownloaded(movie.id)) dlRepo.getLocalFilePath(movie.id)
                                  else movie.videoStreamUrl
                        openPlayer(movie, url)
                    }
                    is AccessManager.AccessResult.Blocked -> {
                        requireContext().toast("আপনার অ্যাকাউন্ট ব্লক করা হয়েছে")
                    }
                    else -> {
                        goToSubscription()
                    }
                }
            }
        }

        binding.btnDownload.setOnClickListener {
            lifecycleScope.launch {
                if (_binding == null) return@launch
                if (movie.testMovie) {
                    startDownload(movie)
                    try { findNavController().navigate(R.id.action_global_to_download) }
                    catch (e: Exception) { requireContext().toast("ডাউনলোড শুরু হয়েছে") }
                    return@launch
                }
                val access = try { accessManager.checkAccess() } catch (e: Exception) { null }
                if (_binding == null) return@launch

                when (access) {
                    is AccessManager.AccessResult.Allowed,
                    is AccessManager.AccessResult.Trial,
                    is AccessManager.AccessResult.Premium,
                    is AccessManager.AccessResult.Pending -> {
                        startDownload(movie)
                        try { findNavController().navigate(R.id.action_global_to_download) }
                        catch (e: Exception) { requireContext().toast("ডাউনলোড শুরু হয়েছে") }
                    }
                    is AccessManager.AccessResult.Blocked -> {
                        requireContext().toast("আপনার অ্যাকাউন্ট ব্লক করা হয়েছে")
                    }
                    else -> {
                        goToSubscription()
                    }
                }
            }
        }

        binding.layoutLocked.setOnClickListener { goToSubscription() }
        binding.btnGoSubscribe.setOnClickListener { goToSubscription() }
    }

    private fun goToSubscription() {
        binding.layoutLocked.show()
        startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
    }

    private fun openPlayer(movie: Movie, url: String) {
        if (url.isBlank()) {
            requireContext().toast("ভিডিও পাওয়া যায়নি")
            return
        }
        val isLocal = dlRepo.isDownloaded(movie.id)
        val intent  = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
            putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
            putExtra(Constants.EXTRA_VIDEO_URL,   url)
            putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
            putExtra(Constants.EXTRA_IS_LOCAL,    isLocal)
        }
        startActivity(intent)
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
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
