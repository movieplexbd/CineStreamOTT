package com.ottapp.moviestream.ui.admin

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.FirebaseDatabase
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.databinding.FragmentAdminMoviesBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminMoviesFragment : Fragment() {

    private var _binding: FragmentAdminMoviesBinding? = null
    private val binding get() = _binding!!
    private val repo = MovieRepository()
    private lateinit var adapter: AdminMovieAdapter
    private var allMovies: List<Movie> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAdminMoviesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AdminMovieAdapter(
            onEdit   = { movie -> openAddEdit(movie) },
            onDelete = { movie -> confirmDelete(movie) }
        )
        binding.rvMovies.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMovies.adapter = adapter

        binding.fabAdd.setOnClickListener { openAddEdit(null) }

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
                
                // Analytics
                binding.tvTotal_movies.text = allMovies.size.toString()
                
                val db = FirebaseDatabase.getInstance().reference
                val usersSnapshot = db.child(Constants.DB_USERS).get().await()
                binding.tvActive_users.text = usersSnapshot.childrenCount.toString()
                
                val trendingMovie = allMovies.maxByOrNull { it.views }?.title ?: "N/A"
                binding.tvTrending_movie.text = trendingMovie
                
            } catch (e: Exception) {
                requireContext().toast("লোড করতে সমস্যা: ${e.message}")
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
        val intent = Intent(requireContext(), AddEditMovieActivity::class.java)
        if (movie != null) intent.putExtra("movie_id", movie.id)
        startActivity(intent)
    }

    private fun confirmDelete(movie: Movie) {
        AlertDialog.Builder(requireContext())
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
                requireContext().toast("মুছে ফেলা হয়েছে")
                loadMovies()
            } catch (e: Exception) {
                requireContext().toast("মুছতে সমস্যা: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadMovies()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
