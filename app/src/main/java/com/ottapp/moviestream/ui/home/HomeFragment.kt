package com.ottapp.moviestream.ui.home

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
import com.ottapp.moviestream.databinding.FragmentHomeBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.loadImage
import com.ottapp.moviestream.util.show

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var trendingAdapter: MovieGridAdapter
    private lateinit var banglaAdapter:   MovieGridAdapter
    private lateinit var hindiAdapter:    MovieGridAdapter
    private lateinit var allAdapter:      MovieGridAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdapters()
        observeViewModel()
        setupSwipeRefresh()

        binding.btnSearch.setOnClickListener {
            try {
                findNavController().navigate(R.id.searchFragment)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun setupAdapters() {
        val onMovieClick: (Movie) -> Unit = { movie -> openMovieDetail(movie) }

        trendingAdapter = MovieGridAdapter(onMovieClick)
        binding.rvTrending.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = trendingAdapter
            isNestedScrollingEnabled = false
        }

        banglaAdapter = MovieGridAdapter(onMovieClick)
        binding.rvBangla.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = banglaAdapter
            isNestedScrollingEnabled = false
        }

        hindiAdapter = MovieGridAdapter(onMovieClick)
        binding.rvHindi.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = hindiAdapter
            isNestedScrollingEnabled = false
        }

        allAdapter = MovieGridAdapter(onMovieClick)
        binding.rvAll.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = allAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) binding.shimmerLayout.show() else binding.shimmerLayout.hide()
            if (!loading) binding.scrollContent.show() else binding.scrollContent.hide()
        }

        viewModel.allMovies.observe(viewLifecycleOwner) { movies ->
            allAdapter.submitList(movies)
            binding.sectionAll.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.trendingMovies.observe(viewLifecycleOwner) { movies ->
            trendingAdapter.submitList(movies)
            binding.sectionTrending.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.banglaMovies.observe(viewLifecycleOwner) { movies ->
            banglaAdapter.submitList(movies)
            binding.sectionBangla.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.hindiMovies.observe(viewLifecycleOwner) { movies ->
            hindiAdapter.submitList(movies)
            binding.sectionHindi.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                val initial = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                binding.tvAvatarInitial.text = initial
                if (user.photoUrl.isNotEmpty()) binding.ivAvatar.loadImage(user.photoUrl)
                binding.tvSubscriptionBadge.text = if (user.isPremium) "PREMIUM" else "FREE"
                binding.tvSubscriptionBadge.visibility = View.VISIBLE
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData()
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun openMovieDetail(movie: Movie) {
        if (!isAdded || _binding == null) return
        try {
            val bundle = bundleOf(Constants.EXTRA_MOVIE_ID to movie.id)
            findNavController().navigate(R.id.action_home_to_detail, bundle)
        } catch (e: Exception) { /* duplicate navigation ignore */ }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
