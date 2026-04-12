package com.ottapp.moviestream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.DownloadRepository
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.data.repository.UserRepository
import com.ottapp.moviestream.databinding.FragmentDetailContainerBinding
import com.ottapp.moviestream.service.DownloadService
import com.ottapp.moviestream.ui.player.PlayerActivity
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
    private val userRepo  = UserRepository()
    private lateinit var dlRepo: DownloadRepository

    private var currentMovie: Movie? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dlRepo = DownloadRepository(requireContext())

        // Back button
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

        // Banner
        binding.ivBanner.loadImage(movie.bannerImageUrl)

        // Title (on banner overlay)
        binding.tvTitleOverlay.text = movie.title.orEmpty()

        // Free badge
        if (movie.testMovie) binding.tvFreeBadge.show() else binding.tvFreeBadge.hide()

        // Meta
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

        // Download button state
        if (dlRepo.isDownloaded(movie.id)) {
            binding.btnDownload.text = "✓ ডাউনলোড হয়েছে"
            binding.btnDownload.isEnabled = false
        }

        binding.btnPlay.setOnClickListener { handlePlay(movie) }
        binding.btnDownload.setOnClickListener { handleDownload(movie) }
    }

    private fun handlePlay(movie: Movie) {
        lifecycleScope.launch {
            try {
                val user = userRepo.getCurrentUser()
                if (_binding == null) return@launch

                val canAccess = user?.isPremium == true || movie.testMovie
                if (!canAccess) {
                    showSubscriptionRequired()
                    return@launch
                }

                val videoUrl = if (dlRepo.isDownloaded(movie.id)) {
                    dlRepo.getLocalFilePath(movie.id)
                } else {
                    movie.videoStreamUrl
                }

                if (videoUrl.isNullOrEmpty()) {
                    requireContext().toast("ভিডিও URL পাওয়া যায়নি")
                    return@launch
                }

                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
                    putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
                    putExtra(Constants.EXTRA_VIDEO_URL,   videoUrl)
                    putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
                    putExtra(Constants.EXTRA_IS_LOCAL,    dlRepo.isDownloaded(movie.id))
                }
                startActivity(intent)

            } catch (e: Exception) {
                if (_binding != null) requireContext().toast("সমস্যা হয়েছে: ${e.message}")
            }
        }
    }

    private fun handleDownload(movie: Movie) {
        lifecycleScope.launch {
            try {
                val user = userRepo.getCurrentUser()
                if (_binding == null) return@launch

                val canAccess = user?.isPremium == true || movie.testMovie
                if (!canAccess) {
                    showSubscriptionRequired()
                    return@launch
                }

                if (dlRepo.isDownloaded(movie.id)) {
                    requireContext().toast("ইতিমধ্যে ডাউনলোড হয়েছে")
                    return@launch
                }

                val downloadUrl = movie.downloadUrl.orEmpty()
                    .ifEmpty { movie.videoStreamUrl.orEmpty() }
                if (downloadUrl.isEmpty()) {
                    requireContext().toast("ডাউনলোড URL পাওয়া যায়নি")
                    return@launch
                }

                val intent = Intent(requireContext(), DownloadService::class.java).apply {
                    putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
                    putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
                    putExtra(Constants.EXTRA_VIDEO_URL,   downloadUrl)
                    putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
                }
                requireContext().startForegroundService(intent)
                requireContext().toast("ডাউনলোড শুরু হয়েছে...")

                binding.btnDownload.text = "ডাউনলোড হচ্ছে..."
                binding.btnDownload.isEnabled = false

            } catch (e: Exception) {
                if (_binding != null) requireContext().toast("সমস্যা হয়েছে: ${e.message}")
            }
        }
    }

    private fun showSubscriptionRequired() {
        if (_binding == null) return
        binding.layoutLocked.visibility = View.VISIBLE
        binding.layoutLocked.animate().alpha(1f).setDuration(300).start()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
