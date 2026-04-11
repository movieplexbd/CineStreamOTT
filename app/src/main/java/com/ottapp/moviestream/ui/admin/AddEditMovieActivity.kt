package com.ottapp.moviestream.ui.admin

  import android.os.Bundle
  import android.view.View
  import androidx.appcompat.app.AppCompatActivity
  import androidx.lifecycle.lifecycleScope
  import com.ottapp.moviestream.data.model.Movie
  import com.ottapp.moviestream.data.repository.MovieRepository
  import com.ottapp.moviestream.databinding.ActivityAddEditMovieBinding
  import com.ottapp.moviestream.util.toast
  import kotlinx.coroutines.launch

  class AddEditMovieActivity : AppCompatActivity() {

      private lateinit var binding: ActivityAddEditMovieBinding
      private val repo = MovieRepository()
      private var movieId: String? = null
      private var existingMovie: Movie? = null

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          binding = ActivityAddEditMovieBinding.inflate(layoutInflater)
          setContentView(binding.root)

          setSupportActionBar(binding.toolbar)
          supportActionBar?.setDisplayHomeAsUpEnabled(true)

          movieId = intent.getStringExtra("movie_id")

          if (movieId != null) {
              supportActionBar?.title = "মুভি এডিট করুন"
              loadExistingMovie(movieId!!)
          } else {
              supportActionBar?.title = "নতুন মুভি যোগ করুন"
          }

          binding.btnSave.setOnClickListener { saveMovie() }
      }

      private fun loadExistingMovie(id: String) {
          binding.progressBar.visibility = View.VISIBLE
          lifecycleScope.launch {
              try {
                  existingMovie = repo.getMovieById(id)
                  existingMovie?.let { populateForm(it) }
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
          binding.etVideoUrl.setText(movie.videoStreamUrl)
          binding.etDownloadUrl.setText(movie.downloadUrl)
          binding.etYear.setText(if (movie.year > 0) movie.year.toString() else "")
          binding.etDuration.setText(movie.duration)
          binding.etRating.setText(movie.imdbRating.toString())
          
          val cats = listOf("Bangla Dubbed", "Hindi Dubbed", "English")
          val idx  = cats.indexOf(movie.category)
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

          val cats = listOf("Bangla Dubbed", "Hindi Dubbed", "English")
          val category = cats.getOrElse(binding.spinnerCategory.selectedItemPosition) { "" }

          val rating = binding.etRating.text.toString().toDoubleOrNull() ?: 0.0
          val year   = binding.etYear.text.toString().toIntOrNull() ?: 0

          val movie = Movie(
              id              = movieId ?: "",
              title           = title,
              description     = binding.etDescription.text.toString().trim(),
              bannerImageUrl  = binding.etBannerUrl.text.toString().trim(),
              videoStreamUrl  = binding.etVideoUrl.text.toString().trim(),
              downloadUrl     = binding.etDownloadUrl.text.toString().trim(),
              category        = category,
              imdbRating      = rating,
              year            = year,
              duration        = binding.etDuration.text.toString().trim(),
              trending        = binding.switchTrending.isChecked,
              testMovie       = binding.switchFree.isChecked
          )

          binding.btnSave.isEnabled = false
          binding.progressBar.visibility = View.VISIBLE

          lifecycleScope.launch {
              try {
                  if (movieId != null) {
                      repo.updateMovie(movie)
                      toast("মুভি আপডেট হয়েছে")
                  } else {
                      repo.addMovie(movie)
                      toast("মুভি যোগ হয়েছে")
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
  