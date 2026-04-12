package com.ottapp.moviestream.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

    private var bannerAdapter: BannerAdapter? = null
    private var trendingAdapter: MovieGridAdapter? = null
    private var banglaAdapter: MovieGridAdapter? = null
    private var hindiAdapter: MovieGridAdapter? = null
    private var allAdapter: MovieGridAdapter? = null

    private val bannerHandler = Handler(Looper.getMainLooper())
    private var bannerRunnable: Runnable? = null
    private var bannerCount = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        try {
            setupBanner()
            setupAdapters()
            observeViewModel()
            setupSwipeRefresh()
            setupSearchButton()
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "onViewCreated error: ${e.message}", e)
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────

    private fun setupSearchButton() {
        _binding?.btnSearch?.setOnClickListener {
            try {
                val navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .setRestoreState(true)
                    .setPopUpTo(
                        findNavController().graph.startDestinationId,
                        inclusive = false,
                        saveState = true
                    )
                    .build()
                findNavController().navigate(R.id.searchFragment, null, navOptions)
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Search nav error: ${e.message}")
            }
        }
    }

    // ── Banner ───────────────────────────────────────────────────────────────

    private fun setupBanner() {
        val adapter = BannerAdapter { movie -> openMovieDetail(movie) }
        bannerAdapter = adapter
        _binding?.bannerPager?.let { pager ->
            pager.adapter = adapter
            pager.offscreenPageLimit = 1
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    if (_binding != null) updateDots(position)
                }
            })
        }
    }

    private fun setupDots(count: Int) {
        bannerCount = count
        val dotsLayout = _binding?.bannerDots ?: return
        dotsLayout.removeAllViews()
        if (count <= 1) return

        val density = resources.displayMetrics.density
        repeat(count) { i ->
            val isActive = (i == 0)
            val sizeDp = if (isActive) 10 else 7
            val sizePx = (sizeDp * density).toInt()
            try {
                val dot = ImageView(requireContext()).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).also { lp ->
                        lp.setMargins(4, 0, 4, 0)
                    }
                    setImageResource(if (isActive) R.drawable.dot_active else R.drawable.dot_inactive)
                }
                dotsLayout.addView(dot)
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "setupDots error: ${e.message}")
            }
        }
    }

    private fun updateDots(selected: Int) {
        val dotsLayout = _binding?.bannerDots ?: return
        val count = dotsLayout.childCount
        val density = resources.displayMetrics.density
        for (i in 0 until count) {
            val dot = dotsLayout.getChildAt(i) as? ImageView ?: continue
            val isActive = (i == selected)
            val sizeDp = if (isActive) 10 else 7
            val sizePx = (sizeDp * density).toInt()
            try {
                dot.layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).also { lp ->
                    lp.setMargins(4, 0, 4, 0)
                }
                dot.setImageResource(if (isActive) R.drawable.dot_active else R.drawable.dot_inactive)
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "updateDots error: ${e.message}")
            }
        }
    }

    private fun startAutoScroll(count: Int) {
        stopAutoScroll()
        if (count <= 1) return
        bannerRunnable = object : Runnable {
            override fun run() {
                val b = _binding ?: return
                try {
                    val next = (b.bannerPager.currentItem + 1) % count
                    b.bannerPager.setCurrentItem(next, true)
                } catch (e: Exception) {
                    android.util.Log.e("HomeFragment", "Auto-scroll error: ${e.message}")
                }
                bannerHandler.postDelayed(this, 4000)
            }
        }
        bannerHandler.postDelayed(bannerRunnable!!, 4000)
    }

    private fun stopAutoScroll() {
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        bannerRunnable = null
    }

    // ── Adapters ─────────────────────────────────────────────────────────────

    private fun setupAdapters() {
        val onClick: (Movie) -> Unit = { openMovieDetail(it) }

        val tAdp = MovieGridAdapter(onClick).also { trendingAdapter = it }
        _binding?.rvTrending?.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = tAdp
            isNestedScrollingEnabled = false
        }

        val bAdp = MovieGridAdapter(onClick).also { banglaAdapter = it }
        _binding?.rvBangla?.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = bAdp
            isNestedScrollingEnabled = false
        }

        val hAdp = MovieGridAdapter(onClick).also { hindiAdapter = it }
        _binding?.rvHindi?.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = hAdp
            isNestedScrollingEnabled = false
        }

        val aAdp = MovieGridAdapter(onClick).also { allAdapter = it }
        _binding?.rvAll?.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = aAdp
            isNestedScrollingEnabled = false
        }
    }

    // ── ViewModel ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            val b = _binding ?: return@observe
            try {
                if (loading) {
                    b.shimmerLayout.startShimmer()
                    b.shimmerLayout.show()
                    b.contentWrapper.hide()
                } else {
                    b.shimmerLayout.stopShimmer()
                    b.shimmerLayout.hide()
                    b.contentWrapper.show()
                    if (b.swipeRefresh.isRefreshing) {
                        b.swipeRefresh.isRefreshing = false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "loading observer error: ${e.message}")
            }
        }

        viewModel.bannerMovies.observe(viewLifecycleOwner) { movies ->
            val b = _binding ?: return@observe
            try {
                if (movies.isNotEmpty()) {
                    bannerAdapter?.submitList(movies)
                    setupDots(movies.size)
                    startAutoScroll(movies.size)
                } else {
                    bannerAdapter?.submitList(emptyList())
                    b.bannerDots.removeAllViews()
                    stopAutoScroll()
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "bannerMovies observer error: ${e.message}")
            }
        }

        viewModel.trendingMovies.observe(viewLifecycleOwner) { movies ->
            val b = _binding ?: return@observe
            try {
                trendingAdapter?.submitList(movies)
                b.sectionTrending.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "trendingMovies observer error: ${e.message}")
            }
        }

        viewModel.banglaMovies.observe(viewLifecycleOwner) { movies ->
            val b = _binding ?: return@observe
            try {
                banglaAdapter?.submitList(movies)
                b.sectionBangla.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "banglaMovies observer error: ${e.message}")
            }
        }

        viewModel.hindiMovies.observe(viewLifecycleOwner) { movies ->
            val b = _binding ?: return@observe
            try {
                hindiAdapter?.submitList(movies)
                b.sectionHindi.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "hindiMovies observer error: ${e.message}")
            }
        }

        viewModel.allMovies.observe(viewLifecycleOwner) { movies ->
            val b = _binding ?: return@observe
            try {
                allAdapter?.submitList(movies)
                b.sectionAll.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "allMovies observer error: ${e.message}")
            }
        }

        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            val b = _binding ?: return@observe
            try {
                if (user != null) {
                    val initial = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                    b.tvAvatarInitial.text = initial
                    if (user.photoUrl.isNotEmpty()) b.ivAvatar.loadImage(user.photoUrl)
                    b.tvSubscriptionBadge.text = if (user.isPremium) "PREMIUM" else "FREE"
                    b.tvSubscriptionBadge.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "currentUser observer error: ${e.message}")
            }
        }
    }

    private fun setupSwipeRefresh() {
        _binding?.swipeRefresh?.setOnRefreshListener {
            viewModel.loadData()
        }
    }

    private fun openMovieDetail(movie: Movie) {
        if (!isAdded || _binding == null) return
        try {
            val bundle = bundleOf(Constants.EXTRA_MOVIE_ID to movie.id)
            findNavController().navigate(R.id.action_home_to_detail, bundle)
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "Navigate to detail error: ${e.message}")
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
        try { _binding?.shimmerLayout?.stopShimmer() } catch (e: Exception) { /* ignore */ }
    }

    override fun onResume() {
        super.onResume()
        if (bannerCount > 1) startAutoScroll(bannerCount)
    }

    override fun onDestroyView() {
        stopAutoScroll()
        try { _binding?.shimmerLayout?.stopShimmer() } catch (e: Exception) { /* ignore */ }
        try { _binding?.bannerPager?.adapter = null } catch (e: Exception) { /* ignore */ }
        bannerAdapter = null
        trendingAdapter = null
        banglaAdapter = null
        hindiAdapter = null
        allAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
