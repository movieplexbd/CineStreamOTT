package com.ottapp.moviestream.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.ottapp.moviestream.R
import com.ottapp.moviestream.adapter.BannerAdapter
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

    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var trendingAdapter: MovieGridAdapter
    private lateinit var banglaAdapter: MovieGridAdapter
    private lateinit var hindiAdapter: MovieGridAdapter
    private lateinit var allAdapter: MovieGridAdapter

    private val bannerHandler = Handler(Looper.getMainLooper())
    private var bannerRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupBanner()
        setupAdapters()
        observeViewModel()
        setupSwipeRefresh()

        binding.btnSearch.setOnClickListener {
            try {
                val navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(findNavController().graph.startDestinationId, inclusive = false, saveState = true)
                    .build()
                findNavController().navigate(R.id.searchFragment, null, navOptions)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    private fun setupBanner() {
        bannerAdapter = BannerAdapter { movie -> openMovieDetail(movie) }
        binding.bannerPager.adapter = bannerAdapter
        binding.bannerPager.offscreenPageLimit = 1

        binding.bannerPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
            }
        })
    }

    private fun setupDots(count: Int) {
        binding.bannerDots.removeAllViews()
        if (count <= 1) return
        repeat(count) { i ->
            val dot = ImageView(requireContext()).apply {
                val size = if (i == 0) 10 else 7
                val px = (size * resources.displayMetrics.density).toInt()
                layoutParams = ViewGroup.MarginLayoutParams(px, px).apply {
                    setMargins(4, 0, 4, 0)
                }
                setImageResource(if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive)
            }
            binding.bannerDots.addView(dot)
        }
    }

    private fun updateDots(selected: Int) {
        val count = binding.bannerDots.childCount
        for (i in 0 until count) {
            val dot = binding.bannerDots.getChildAt(i) as? ImageView ?: continue
            val isActive = i == selected
            dot.setImageResource(if (isActive) R.drawable.dot_active else R.drawable.dot_inactive)
            val size = if (isActive) 10 else 7
            val px = (size * resources.displayMetrics.density).toInt()
            dot.layoutParams = ViewGroup.MarginLayoutParams(px, px).apply {
                setMargins(4, 0, 4, 0)
            }
        }
    }

    private fun startAutoScroll(count: Int) {
        stopAutoScroll()
        if (count <= 1) return
        bannerRunnable = object : Runnable {
            override fun run() {
                val next = (binding.bannerPager.currentItem + 1) % count
                binding.bannerPager.setCurrentItem(next, true)
                bannerHandler.postDelayed(this, 4000)
            }
        }
        bannerHandler.postDelayed(bannerRunnable!!, 4000)
    }

    private fun stopAutoScroll() {
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        bannerRunnable = null
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

        viewModel.trendingMovies.observe(viewLifecycleOwner) { movies ->
            // Use trending movies for banner
            if (movies.isNotEmpty()) {
                val bannerMovies = movies.take(5)
                bannerAdapter.submitList(bannerMovies)
                setupDots(bannerMovies.size)
                startAutoScroll(bannerMovies.size)
            }
            trendingAdapter.submitList(movies)
            binding.sectionTrending.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.allMovies.observe(viewLifecycleOwner) { movies ->
            allAdapter.submitList(movies)
            binding.sectionAll.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
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

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }

    override fun onResume() {
        super.onResume()
        val count = bannerAdapter.itemCount
        if (count > 1) startAutoScroll(count)
    }

    override fun onDestroyView() {
        stopAutoScroll()
        _binding = null
        super.onDestroyView()
    }
}
