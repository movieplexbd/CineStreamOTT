package com.ottapp.moviestream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ottapp.moviestream.R
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.DownloadRepository
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.data.repository.UserRepository
import com.ottapp.moviestream.databinding.BottomSheetMovieDetailBinding
import com.ottapp.moviestream.service.DownloadService
import com.ottapp.moviestream.ui.player.PlayerActivity
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.loadImage
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch

class MovieDetailBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMovieDetailBinding? = null
    private val binding get() = _binding!!

    private val movieRepo = MovieRepository()
    private val userRepo  = UserRepository()
    private lateinit var dlRepo: DownloadRepository
    private var currentMovie: Movie? = null

    companion object {
        fun newInstance(movieId: String) = MovieDetailBottomSheet().apply {
            arguments = Bundle().apply { putString(Constants.EXTRA_MOVIE_ID, movieId) }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetMovieDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dlRepo = DownloadRepository(requireContext())

        val movieId = arguments?.getString(Constants.EXTRA_MOVIE_ID) ?: run { dismiss(); return }
        loadMovieDetails(movieId)
    }

    private fun loadMovieDetails(movieId: String) {
        _binding?.progressDetail?.show()
        lifecycleScope.launch {
            val movie = movieRepo.getMovieById(movieId)
            if (_binding == null) return@launch
            if (movie == null) {
                requireContext().toast("মুভি পাওয়া যায়নি")
                dismiss()
                return@launch
            }
            currentMovie = movie
            binding.progressDetail.hide()
            populateUI(movie)
        }
    }

    private fun populateUI(movie: Movie) {
        binding.ivBanner.loadImage(movie.bannerImageUrl)
        binding.tvTitle.text = movie.title.orEmpty()
        binding.tvDescription.text = movie.description.orEmpty()
        binding.tvRating.text = "⭐ ${movie.imdbRating} IMDb"
        binding.tvCategory.text = movie.category.orEmpty()
        if (movie.year > 0) binding.tvYear.text = movie.year.toString()

        if (movie.testMovie) {
            binding.tvFreeBadge.show()
        } else {
            binding.tvFreeBadge.hide()
        }

        if (dlRepo.isDownloaded(movie.id)) {
            binding.btnDownload.text = "✓ ডাউনলোড হয়েছে"
            binding.btnDownload.isEnabled = false
        }

        binding.btnPlay.setOnClickListener     { handlePlay(movie) }
        binding.btnDownload.setOnClickListener { handleDownload(movie) }
        binding.btnClose.setOnClickListener    { dismiss() }
    }

    private fun handlePlay(movie: Movie) {
        lifecycleScope.launch {
            val user = userRepo.getCurrentUser()
            val canAccess = user?.isPremium == true || movie.testMovie

            if (!canAccess) {
                if (_binding != null) showSubscriptionRequired()
                return@launch
            }

            if (_binding == null) return@launch

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
            dismiss()
        }
    }

    private fun handleDownload(movie: Movie) {
        lifecycleScope.launch {
            val user = userRepo.getCurrentUser()
            val canAccess = user?.isPremium == true || movie.testMovie

            if (!canAccess) {
                if (_binding != null) showSubscriptionRequired()
                return@launch
            }

            if (_binding == null) return@launch

            if (dlRepo.isDownloaded(movie.id)) {
                requireContext().toast("ইতিমধ্যে ডাউনলোড হয়েছে")
                return@launch
            }

            val downloadUrl = movie.downloadUrl.orEmpty().ifEmpty { movie.videoStreamUrl.orEmpty() }
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
        }
    }

    private fun showSubscriptionRequired() {
        binding.layoutLocked.show()
        binding.layoutLocked.animate().alpha(1f).setDuration(300).start()
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
