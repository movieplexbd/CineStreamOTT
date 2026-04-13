package com.ottapp.moviestream.ui.admin

  import android.content.Intent
  import android.os.Bundle
  import android.text.Editable
  import android.text.TextWatcher
  import android.view.*
  import android.widget.*
  import androidx.appcompat.app.AlertDialog
  import androidx.appcompat.app.AppCompatActivity
  import androidx.lifecycle.lifecycleScope
  import androidx.recyclerview.widget.DiffUtil
  import androidx.recyclerview.widget.LinearLayoutManager
  import androidx.recyclerview.widget.ListAdapter
  import androidx.recyclerview.widget.RecyclerView
  import com.ottapp.moviestream.data.model.Movie
  import com.ottapp.moviestream.data.repository.MovieRepository
  import com.ottapp.moviestream.databinding.ActivityAdminBinding
  import com.ottapp.moviestream.databinding.ItemAdminMovieBinding
  import com.ottapp.moviestream.util.toast
  import kotlinx.coroutines.launch

  class AdminActivity : AppCompatActivity() {

      private lateinit var binding: ActivityAdminBinding
      private val repo = MovieRepository()
      private lateinit var adapter: AdminMovieAdapter
      private var allMovies: List<Movie> = emptyList()

      override fun onCreate(savedInstanceState: Bundle?) {
          super.onCreate(savedInstanceState)
          binding = ActivityAdminBinding.inflate(layoutInflater)
          setContentView(binding.root)

          setSupportActionBar(binding.toolbar)
          supportActionBar?.setDisplayHomeAsUpEnabled(true)
          supportActionBar?.title = "অ্যাডমিন প্যানেল"

          adapter = AdminMovieAdapter(
              onEdit   = { movie -> openAddEdit(movie) },
              onDelete = { movie -> confirmDelete(movie) }
          )
          binding.rvMovies.layoutManager = LinearLayoutManager(this)
          binding.rvMovies.adapter = adapter

          binding.fabAdd.setOnClickListener { openAddEdit(null) }
          binding.btnManageReels.setOnClickListener {
              startActivity(Intent(this, AdminReelsActivity::class.java))
          }

          binding.etSearch.addTextChangedListener(object : TextWatcher {
              override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
              override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filterMovies(s.toString()) }
              override fun afterTextChanged(s: Editable?) {}
          })

          loadMovies()
      }

      private fun loadMovies() {
          binding.progressBar.visibility = View.VISIBLE
          lifecycleScope.launch {
              try {
                  allMovies = repo.getAllMovies()
                  adapter.submitList(allMovies)
                  binding.tvCount.text = "মোট ${allMovies.size}টি মুভি"
              } catch (e: Exception) {
                  toast("লোড করতে সমস্যা: ${e.message}")
              } finally {
                  binding.progressBar.visibility = View.GONE
              }
          }
      }

      private fun filterMovies(query: String) {
          val q = query.lowercase().trim()
          val filtered = if (q.isEmpty()) allMovies
          else allMovies.filter {
              it.title.lowercase().contains(q) || it.category.lowercase().contains(q)
          }
          adapter.submitList(filtered)
          binding.tvCount.text = "মোট ${filtered.size}টি মুভি"
      }

      private fun openAddEdit(movie: Movie?) {
          val intent = Intent(this, AddEditMovieActivity::class.java)
          if (movie != null) intent.putExtra("movie_id", movie.id)
          startActivity(intent)
      }

      private fun confirmDelete(movie: Movie) {
          AlertDialog.Builder(this)
              .setTitle("মুভি মুছবেন?")
              .setMessage("''${movie.title}'' মুছে ফেলা হবে। নিশ্চিত?")
              .setPositiveButton("হ্যাঁ, মুছুন") { _, _ -> deleteMovie(movie) }
              .setNegativeButton("না", null)
              .show()
      }

      private fun deleteMovie(movie: Movie) {
          lifecycleScope.launch {
              try {
                  repo.deleteMovie(movie.id)
                  toast("মুছে ফেলা হয়েছে")
                  loadMovies()
              } catch (e: Exception) {
                  toast("মুছতে সমস্যা: ${e.message}")
              }
          }
      }

      override fun onResume() {
          super.onResume()
          loadMovies()
      }

      override fun onSupportNavigateUp(): Boolean { finish(); return true }
  }

  class AdminMovieAdapter(
      private val onEdit:   (Movie) -> Unit,
      private val onDelete: (Movie) -> Unit
  ) : ListAdapter<Movie, AdminMovieAdapter.VH>(Diff()) {

      inner class VH(private val b: ItemAdminMovieBinding) : RecyclerView.ViewHolder(b.root) {
          fun bind(movie: Movie) {
              b.tvTitle.text    = movie.title.ifEmpty { "শিরোনাম নেই" }
              b.tvCategory.text = movie.category.ifEmpty { "ক্যাটাগরি নেই" }
              b.tvRating.text   = "⭐ ${movie.imdbRating}"
              b.tvFree.text     = if (movie.testMovie) "ফ্রি" else "প্রিমিয়াম"
              b.tvFree.setBackgroundResource(
                  if (movie.testMovie) com.ottapp.moviestream.R.drawable.bg_badge_free
                  else com.ottapp.moviestream.R.drawable.bg_premium_badge
              )
              b.tvTrending.visibility = if (movie.trending) View.VISIBLE else View.GONE
              b.btnEdit.setOnClickListener   { onEdit(movie) }
              b.btnDelete.setOnClickListener { onDelete(movie) }
          }
      }

      override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
          VH(ItemAdminMovieBinding.inflate(LayoutInflater.from(parent.context), parent, false))

      override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

      class Diff : DiffUtil.ItemCallback<Movie>() {
          override fun areItemsTheSame(a: Movie, b: Movie) = a.id == b.id
          override fun areContentsTheSame(a: Movie, b: Movie) = a == b
      }
  }
  