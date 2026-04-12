package com.ottapp.moviestream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import com.ottapp.moviestream.util.loadImageSafe
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

class MovieDetailBottomSheet : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "MovieDetailBottomSheet"
        fun newInstance(movieId: String) = MovieDetailBottomSheet().apply {
            arguments = Bundle().apply { putString(Constants.EXTRA_MOVIE_ID, movieId) }
        }
    }

    private var _binding: BottomSheetMovieDetailBinding? = null
    private val binding get() = _binding

    private val movieRepo = MovieRepository()
    private val userRepo  = UserRepository()
    private var dlRepo: DownloadRepository? = null
    private var currentMovie: Movie? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            _binding = BottomSheetMovieDetailBinding.inflate(inflater, container, false)
            _binding?.root
        } catch (e: Exception) {
            Log.e(TAG, "Inflate error: ${e.message}", e)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            dlRepo = DownloadRepository(requireContext())
        } catch (e: Exception) {
            Log.e(TAG, "DownloadRepository init error: ${e.message}")
        }

        val movieId = arguments?.getString(Constants.EXTRA_MOVIE_ID)
        if (movieId.isNullOrEmpty()) {
            dismissSafe()
            return
        }
        loadMovieDetails(movieId)
    }

    private fun dismissSafe() {
        try { dismiss() } catch (e: Exception) {
            Log.e(TAG, "Dismiss error: ${e.message}")
        }
    }

    private fun loadMovieDetails(movieId: String) {
        binding?.progressDetail?.show()
        lifecycleScope.launch(exceptionHandler) {
            try {
                val movie = movieRepo.getMovieById(movieId)
                if (_binding == null || !isAdded) return@launch
                if (movie == null) {
                    context?.toast("মুভি পাওয়া যায়নি")
                    dismissSafe()
                    return@launch
                }
                currentMovie = movie
                binding?.progressDetail?.hide()
                populateUI(movie)
            } catch (e: Exception) {
                if (_binding == null || !isAdded) return@launch
                Log.e(TAG, "Load error: ${e.message}")
                context?.toast("লোড করতে সমস্যা")
                dismissSafe()
            }
        }
    }

    private fun populateUI(movie: Movie) {
        val b = binding ?: return
        if (!isAdded) return

        try {
            b.ivBanner.loadImageSafe(movie.bannerImageUrl)
            b.tvTitle.text = movie.title
            b.tvDescription.text = movie.description
            b.tvRating.text = "⭐ ${movie.imdbRating} IMDb"
            b.tvCategory.text = movie.category
            if (movie.year > 0) b.tvYear.text = movie.year.toString()

            if (movie.testMovie) b.tvFreeBadge.show() else b.tvFreeBadge.hide()

            val dl = dlRepo
            if (dl != null && dl.isDownloaded(movie.id)) {
                b.btnDownload.text = "✓ ডাউনলোড হয়েছে"
                b.btnDownload.isEnabled = false
            }

            b.btnPlay.setOnClickListener     { handlePlay(movie) }
            b.btnDownload.setOnClickListener { handleDownload(movie) }
            b.btnClose.setOnClickListener    { dismissSafe() }
        } catch (e: Exception) {
            Log.e(TAG, "populateUI error: ${e.message}")
        }
    }

    private fun handlePlay(movie: Movie) {
        lifecycleScope.launch(exceptionHandler) {
            try {
                val user = try { userRepo.getCurrentUser() } catch (e: Exception) { null }
                val canAccess = user?.isPremium == true || movie.testMovie

                if (_binding == null || !isAdded) return@launch

                if (!canAccess) {
                    showSubscriptionRequired()
                    return@launch
                }

                val dl = dlRepo
                val videoUrl = if (dl != null && dl.isDownloaded(movie.id)) {
                    dl.getLocalFilePath(movie.id)
                } else {
                    movie.videoStreamUrl
                }

                if (videoUrl.isNullOrEmpty()) {
                    context?.toast("ভিডিও URL পাওয়া যায়নি")
                    return@launch
                }

                val ctx = context ?: return@launch
                val intent = Intent(ctx, PlayerActivity::class.java).apply {
                    putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
                    putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
                    putExtra(Constants.EXTRA_VIDEO_URL,   videoUrl)
                    putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
                    putExtra(Constants.EXTRA_IS_LOCAL,    dl?.isDownloaded(movie.id) ?: false)
                }
                startActivity(intent)
                dismissSafe()
            } catch (e: Exception) {
                Log.e(TAG, "handlePlay error: ${e.message}")
                if (_binding != null && isAdded) {
                    context?.toast("সমস্যা হয়েছে")
                }
            }
        }
    }

    private fun handleDownload(movie: Movie) {
        lifecycleScope.launch(exceptionHandler) {
            try {
                val user = try { userRepo.getCurrentUser() } catch (e: Exception) { null }
                val canAccess = user?.isPremium == true || movie.testMovie

                if (_binding == null || !isAdded) return@launch

                if (!canAccess) {
                    showSubscriptionRequired()
                    return@launch
                }

                val dl = dlRepo
                if (dl != null && dl.isDownloaded(movie.id)) {
                    context?.toast("ইতিমধ্যে ডাউনলোড হয়েছে")
                    return@launch
                }

                val downloadUrl = movie.downloadUrl.ifEmpty { movie.videoStreamUrl }
                if (downloadUrl.isEmpty()) {
                    context?.toast("ডাউনলোড URL পাওয়া যায়নি")
                    return@launch
                }

                val ctx = context ?: return@launch
                val intent = Intent(ctx, DownloadService::class.java).apply {
                    putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
                    putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
                    putExtra(Constants.EXTRA_VIDEO_URL,   downloadUrl)
                    putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
                }
                ContextCompat.startForegroundService(ctx, intent)
                ctx.toast("ডাউনলোড শুরু হয়েছে...")
                if (_binding != null) {
                    binding?.btnDownload?.text = "ডাউনলোড হচ্ছে..."
                    binding?.btnDownload?.isEnabled = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleDownload error: ${e.message}")
                if (_binding != null && isAdded) {
                    context?.toast("সমস্যা হয়েছে")
                }
            }
        }
    }

    private fun showSubscriptionRequired() {
        val b = binding ?: return
        try {
            b.layoutLocked.show()
            b.layoutLocked.animate().alpha(1f).setDuration(300).start()
        } catch (e: Exception) {
            Log.e(TAG, "showSubscriptionRequired error: ${e.message}")
        }
    }

    override fun onDestroyView() { _binding = null; super.onDestroyView() }
}
