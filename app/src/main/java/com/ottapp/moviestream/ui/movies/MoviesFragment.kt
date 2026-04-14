package com.ottapp.moviestream.ui.movies

import android.os.Bundle
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

    private var _binding: FragmentMoviesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MoviesViewModel by viewModels()
    private lateinit var adapter: MovieGridAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMoviesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Pass loadNextPage as onLoadMore for infinite scroll
        adapter = MovieGridAdapter(
            onClick     = { movie -> openDetail(movie) },
            onLoadMore  = { viewModel.loadNextPage() }
        )
        binding.rvMovies.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvMovies.adapter = adapter

        setupCategoryTabs()
        observeViewModel()
    }

    private fun setupCategoryTabs() {
        val tabs = listOf(
            binding.tabAll      to Constants.CAT_ALL,
            binding.tabBangla   to Constants.CAT_BANGLA,
            binding.tabHindi    to Constants.CAT_HINDI,
            binding.tabTrending to Constants.CAT_TRENDING
        )
        tabs.forEach { (btn, cat) ->
            btn.setOnClickListener {
                tabs.forEach { (b, _) -> b.isSelected = false }
                btn.isSelected = true
                viewModel.setCategory(cat)
            }
        }
        binding.tabAll.isSelected = true
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) {
                binding.shimmer.startShimmer()
                binding.shimmer.show()
                binding.rvMovies.hide()
            } else {
                binding.shimmer.stopShimmer()
                binding.shimmer.hide()
                binding.rvMovies.show()
            }
        }
        viewModel.filteredMovies.observe(viewLifecycleOwner) { movies ->
            adapter.submitList(movies)
            if (movies.isEmpty()) {
                binding.tvEmpty.show()
                binding.rvMovies.hide()
            } else {
                binding.tvEmpty.hide()
                binding.rvMovies.show()
            }
        }
    }

    private fun openDetail(movie: Movie) {
        if (!isAdded || _binding == null) return
        try {
            val bundle = bundleOf(Constants.EXTRA_MOVIE_ID to movie.id)
            findNavController().navigate(R.id.action_movies_to_detail, bundle)
        } catch (_: Exception) { }
    }

    override fun onDestroyView() {
        _binding?.shimmer?.stopShimmer()
        _binding = null
        super.onDestroyView()
    }
}
