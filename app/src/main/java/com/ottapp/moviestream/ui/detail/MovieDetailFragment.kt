package com.ottapp.moviestream.ui.detail

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.ottapp.moviestream.data.repository.UserRepository
import com.ottapp.moviestream.databinding.FragmentDetailContainerBinding
import com.ottapp.moviestream.service.DownloadService
import com.ottapp.moviestream.ui.player.PlayerActivity
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.loadImageSafe
import com.ottapp.moviestream.util.show
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch

class MovieDetailFragment : Fragment() {

    companion object {
        private const val TAG = "MovieDetailFragment"
    }

    private var _binding: FragmentDetailContainerBinding? = null
    private val binding get() = _binding

    private val movieRepo = MovieRepository()
    private val userRepo  = UserRepository()
    private var dlRepo: DownloadRepository? = null

    private var currentMovie: Movie? = null

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return try {
            _binding = FragmentDetailContainerBinding.inflate(inflater, container, false)
            _binding?.root
        } catch (e: Exception) {
            Log.e(TAG, "Inflate error: ${e.message}", e)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (_binding == null) return

        try {
            dlRepo = DownloadRepository(requireContext())
        } catch (e: Exception) {
            Log.e(TAG, "DownloadRepository init error: ${e.message}")
        }

        binding?.btnBack?.setOnClickListener {
            try {
                findNavController().navigateUp()
            } catch (e: Exception) {
                activity?.onBackPressed()
            }
        }

        val movieId = arguments?.getString(Constants.EXTRA_MOVIE_ID)
        if (movieId.isNullOrEmpty()) {
            context?.toast("মুভি পাওয়া যায়নি")
            try { findNavController().navigateUp() } catch (e: Exception) { }
            return
        }
        loadMovie(movieId)
    }

    private fun loadMovie(movieId: String) {
        binding?.progressCenter?.show()
        binding?.layoutContent?.hide()

        lifecycleScope.launch(exceptionHandler) {
            try {
                val movie = movieRepo.getMovieById(movieId)
                if (_binding == null || !isAdded) return@launch

                if (movie == null) {
                    context?.toast("মুভি পাওয়া যায়নি")
                    try { findNavController().navigateUp() } catch (e: Exception) { }
                    return@launch
                }
                currentMovie = movie
                binding?.progressCenter?.hide()
                populateUI(movie)
                binding?.layoutContent?.show()

            } catch (e: Exception) {
                if (_binding == null || !isAdded) return@launch
                Log.e(TAG, "loadMovie error: ${e.message}", e)
                context?.toast("লোড করতে সমস্যা")
                try { findNavController().navigateUp() } catch (e2: Exception) { }
            }
        }
    }

    private fun populateUI(movie: Movie) {
        val b = binding ?: return
        if (!isAdded) return

        try {
            b.ivBanner.loadImageSafe(movie.bannerImageUrl)
            b.tvTitleOverlay.text = movie.title
            if (movie.testMovie) b.tvFreeBadge.show() else b.tvFreeBadge.hide()
            b.tvRating.text = "⭐ ${movie.imdbRating}"
            b.tvCategory.text = movie.category
            b.tvDescription.text = movie.description

            if (movie.year > 0) {
                b.tvYear.text = movie.year.toString()
                b.tvYear.show()
            }
            if (movie.duration.isNotEmpty()) {
                b.tvDuration.text = movie.duration
                b.tvDuration.show()
            }

            val dl = dlRepo
            if (dl != null && dl.isDownloaded(movie.id)) {
                b.btnDownload.text = "ডাউনলোড হয়েছে ✓"
                b.btnDownload.isEnabled = false
            } else {
                b.btnDownload.text = "ডাউনলোড করুন"
                b.btnDownload.isEnabled = true
            }

            b.btnWatch.setOnClickListener {
                lifecycleScope.launch(exceptionHandler) {
                    try {
                        val user = try { userRepo.getCurrentUser() } catch (e: Exception) { null }
                        if (_binding == null || !isAdded) return@launch

                        if (movie.testMovie || user?.isPremium == true) {
                            val url = if (dl != null && dl.isDownloaded(movie.id)) {
                                dl.getLocalFilePath(movie.id)
                            } else {
                                movie.videoStreamUrl
                            }
                            openPlayer(movie, url)
                        } else {
                            binding?.layoutLocked?.show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Watch error: ${e.message}")
                    }
                }
            }

            b.btnDownload.setOnClickListener {
                lifecycleScope.launch(exceptionHandler) {
                    try {
                        val user = try { userRepo.getCurrentUser() } catch (e: Exception) { null }
                        if (_binding == null || !isAdded) return@launch

                        if (movie.testMovie || user?.isPremium == true) {
                            startDownload(movie)
                            try {
                                findNavController().navigate(R.id.action_global_to_download)
                            } catch (e: Exception) {
                                context?.toast("ডাউনলোড শুরু হয়েছে")
                            }
                        } else {
                            binding?.layoutLocked?.show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Download error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "populateUI error: ${e.message}", e)
        }
    }

    private fun openPlayer(movie: Movie, url: String) {
        if (url.isBlank()) {
            context?.toast("ভিডিও পাওয়া যায়নি")
            return
        }
        try {
            val isLocal = dlRepo?.isDownloaded(movie.id) ?: false
            val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
                putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
                putExtra(Constants.EXTRA_VIDEO_URL,   url)
                putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
                putExtra(Constants.EXTRA_IS_LOCAL,    isLocal)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openPlayer error: ${e.message}")
            context?.toast("Player চালু করতে সমস্যা")
        }
    }

    private fun startDownload(movie: Movie) {
        try {
            val ctx = context ?: return
            val intent = Intent(ctx, DownloadService::class.java).apply {
                putExtra(Constants.EXTRA_MOVIE_ID,    movie.id)
                putExtra(Constants.EXTRA_MOVIE_TITLE, movie.title)
                putExtra(Constants.EXTRA_VIDEO_URL,   movie.downloadUrl.ifEmpty { movie.videoStreamUrl })
                putExtra(Constants.EXTRA_BANNER_URL,  movie.bannerImageUrl)
            }
            ContextCompat.startForegroundService(ctx, intent)
            ctx.toast("ডাউনলোড শুরু হয়েছে...")
        } catch (e: Exception) {
            Log.e(TAG, "startDownload error: ${e.message}")
            context?.toast("ডাউনলোড শুরু করতে সমস্যা")
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
