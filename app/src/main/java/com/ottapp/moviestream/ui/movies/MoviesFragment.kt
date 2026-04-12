package com.ottapp.moviestream.ui.movies

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.ottapp.moviestream.R
import com.ottapp.moviestream.adapter.MovieGridAdapter
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.databinding.FragmentMoviesBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.show

class MoviesFragment : Fragment() {

    companion object {
        private const val TAG = "MoviesFragment"
    }

    private var _binding: FragmentMoviesBinding? = null
    private val binding get() = _binding
    private val viewModel: MoviesViewModel by viewModels()
    private var adapter: MovieGridAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return try {
            _binding = FragmentMoviesBinding.inflate(inflater, container, false)
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
            val ctx = context ?: return
            adapter = MovieGridAdapter { movie -> openDetail(movie) }
            binding?.rvMovies?.layoutManager = GridLayoutManager(ctx, 3)
            binding?.rvMovies?.adapter = adapter

            setupCategoryTabs()
            observeViewModel()
        } catch (e: Exception) {
            Log.e(TAG, "onViewCreated error: ${e.message}", e)
        }
    }

    private fun setupCategoryTabs() {
        val b = binding ?: return
        val tabs = listOf(
            b.tabAll      to Constants.CAT_ALL,
            b.tabBangla   to Constants.CAT_BANGLA,
            b.tabHindi    to Constants.CAT_HINDI,
            b.tabTrending to Constants.CAT_TRENDING
        )
        tabs.forEach { (btn, cat) ->
            btn.setOnClickListener {
                tabs.forEach { (bt, _) -> bt.isSelected = false }
                btn.isSelected = true
                viewModel.setCategory(cat)
            }
        }
        b.tabAll.isSelected = true
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            val b = binding ?: return@observe
            try {
                if (loading) {
                    b.shimmer.startShimmer()
                    b.shimmer.show()
                    b.rvMovies.hide()
                } else {
                    b.shimmer.stopShimmer()
                    b.shimmer.hide()
                    b.rvMovies.show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Loading observer error: ${e.message}")
            }
        }
        viewModel.filteredMovies.observe(viewLifecycleOwner) { movies ->
            val b = binding ?: return@observe
            adapter?.submitList(movies)
            if (movies.isEmpty()) {
                b.tvEmpty.show()
                b.rvMovies.hide()
            } else {
                b.tvEmpty.hide()
                b.rvMovies.show()
            }
        }
    }

    private fun openDetail(movie: Movie) {
        if (!isAdded || _binding == null) return
        try {
            val bundle = bundleOf(Constants.EXTRA_MOVIE_ID to movie.id)
            findNavController().navigate(R.id.action_movies_to_detail, bundle)
        } catch (e: Exception) {
            Log.e(TAG, "openDetail error: ${e.message}")
        }
    }

    override fun onDestroyView() {
        try { _binding?.shimmer?.stopShimmer() } catch (e: Exception) { }
        adapter = null
        _binding = null
        super.onDestroyView()
    }
}
