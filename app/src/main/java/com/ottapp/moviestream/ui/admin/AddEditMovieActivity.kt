package com.ottapp.moviestream.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.databinding.ActivityAddEditMovieBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch

class AddEditMovieActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditMovieBinding
    private val repo = MovieRepository()
    private var movieId: String? = null

    private val categories = listOf("Bangla Dubbed", "Hindi Dubbed", "English")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditMovieBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = spinnerAdapter

        movieId = intent.getStringExtra("movie_id")

        if (movieId != null) {
            supportActionBar?.title = "মুভি এডিট করুন"
            loadExistingMovie(movieId!!)
        } else {
            supportActionBar?.title = "নতুন মুভি যোগ করুন"
        }

        binding.btnSave.setOnClickListener { saveMovie() }

        // Show hint about test movie limit
        binding.switchFree.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                toast("সতর্কতা: সর্বোচ্চ ${Constants.MAX_TEST_MOVIES}টি ফ্রি মুভি রাখা যাবে")
            }
        }
    }

    private fun loadExistingMovie(id: String) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val movie = repo.getMovieById(id)
                movie?.let { populateForm(it) }
            } catch (e: Exception) {
                toast("লোড করতে সমস্যা হয়েছে")
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun populateForm(movie: Movie) {
        binding.etTitle.setText(movie.title)
        binding.etDescription.setText(movie.description)
        binding.etBannerUrl.setText(movie.bannerImageUrl)
        binding.etDetailThumbnailUrl.setText(movie.detailThumbnailUrl)
        binding.etVideoUrl.setText(movie.videoStreamUrl)
        binding.etDownloadUrl.setText(movie.downloadUrl)
        binding.etYear.setText(if (movie.year > 0) movie.year.toString() else "")
        binding.etDuration.setText(movie.duration)
        binding.etRating.setText(if (movie.imdbRating > 0) movie.imdbRating.toString() else "")

        val idx = categories.indexOf(movie.category)
        if (idx >= 0) binding.spinnerCategory.setSelection(idx)

        binding.switchTrending.isChecked = movie.trending
        binding.switchFree.isChecked     = movie.testMovie
    }

    private fun saveMovie() {
        val title = binding.etTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.etTitle.error = "শিরোনাম দিন"
            return
        }
        val videoUrl = binding.etVideoUrl.text.toString().trim()
        if (videoUrl.isEmpty()) {
            binding.etVideoUrl.error = "ভিডিও URL দিন"
            return
        }

        val category  = categories.getOrElse(binding.spinnerCategory.selectedItemPosition) { "" }
        val rating    = binding.etRating.text.toString().toDoubleOrNull() ?: 0.0
        val year      = binding.etYear.text.toString().toIntOrNull() ?: 0
        val isTestMov = binding.switchFree.isChecked

        val movie = Movie(
            id             = movieId ?: "",
            title          = title,
            description    = binding.etDescription.text.toString().trim(),
            bannerImageUrl = binding.etBannerUrl.text.toString().trim(),
            detailThumbnailUrl = binding.etDetailThumbnailUrl.text.toString().trim(),
            videoStreamUrl = videoUrl,
            downloadUrl    = binding.etDownloadUrl.text.toString().trim(),
            category       = category,
            imdbRating     = rating,
            year           = year,
            duration       = binding.etDuration.text.toString().trim(),
            trending       = binding.switchTrending.isChecked,
            testMovie      = isTestMov
        )

        binding.btnSave.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // ─── Critical: enforce max 2 test movies ───────────────────
                if (isTestMov) {
                    val allMovies = repo.getAllMovies()
                    // Count test movies EXCLUDING the current movie being edited
                    val existingTestCount = allMovies.count { m ->
                        m.testMovie && m.id != (movieId ?: "")
                    }
                    if (existingTestCount >= Constants.MAX_TEST_MOVIES) {
                        toast("❌ সর্বোচ্চ ${Constants.MAX_TEST_MOVIES}টি ফ্রি (Test) মুভি রাখা যাবে। আগে অন্য একটি মুভির ফ্রি টগল বন্ধ করুন।")
                        binding.btnSave.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                        return@launch
                    }
                }
                // ──────────────────────────────────────────────────────────

                if (movieId != null) {
                    repo.updateMovie(movie)
                    toast("মুভি আপডেট হয়েছে ✓")
                } else {
                    repo.addMovie(movie)
                    toast("মুভি যোগ হয়েছে ✓")
                }
                finish()

            } catch (e: Exception) {
                toast("সমস্যা হয়েছে: ${e.message}")
                binding.btnSave.isEnabled = true
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }
}
