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
import com.ottapp.moviestream.util.loadImageSafe
import com.ottapp.moviestream.util.show

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding

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
    ): View? {
        return try {
            _binding = FragmentHomeBinding.inflate(inflater, container, false)
            _binding?.root
        } catch (e: Exception) {
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (_binding == null) return

        try {
            setupBanner()
            setupAdapters()
            observeViewModel()
            setupSwipeRefresh()

            binding?.btnSearch?.setOnClickListener {
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
                } catch (e: Exception) { }
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "onViewCreated error: ${e.message}", e)
        }
    }

    private fun setupBanner() {
        bannerAdapter = BannerAdapter { movie -> openMovieDetail(movie) }
        binding?.bannerPager?.adapter = bannerAdapter
        binding?.bannerPager?.offscreenPageLimit = 1

        binding?.bannerPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (_binding != null) updateDots(position)
            }
        })
    }

    private fun setupDots(count: Int) {
        bannerCount = count
        val b = binding ?: return
        b.bannerDots.removeAllViews()
        if (count <= 1) return

        val ctx = context ?: return
        repeat(count) { i ->
            val isActive = i == 0
            val dot = ImageView(ctx).apply {
                val sizeDp = if (isActive) 10 else 7
                val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
                layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).also { lp ->
                    lp.setMargins(4, 0, 4, 0)
                }
                setImageResource(if (isActive) R.drawable.dot_active else R.drawable.dot_inactive)
            }
            b.bannerDots.addView(dot)
        }
    }

    private fun updateDots(selected: Int) {
        val b = binding ?: return
        val count = b.bannerDots.childCount
        for (i in 0 until count) {
            val dot = b.bannerDots.getChildAt(i) as? ImageView ?: continue
            val isActive = (i == selected)
            val sizeDp = if (isActive) 10 else 7
            val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
            dot.layoutParams = ViewGroup.MarginLayoutParams(sizePx, sizePx).also { lp ->
                lp.setMargins(4, 0, 4, 0)
            }
            dot.setImageResource(if (isActive) R.drawable.dot_active else R.drawable.dot_inactive)
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
                    bannerHandler.postDelayed(this, 4000)
                } catch (e: Exception) { }
            }
        }
        bannerHandler.postDelayed(bannerRunnable!!, 4000)
    }

    private fun stopAutoScroll() {
        bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
        bannerRunnable = null
    }

    private fun setupAdapters() {
        val onClick: (Movie) -> Unit = { openMovieDetail(it) }
        val ctx = context ?: return

        trendingAdapter = MovieGridAdapter(onClick)
        binding?.rvTrending?.apply {
            layoutManager = GridLayoutManager(ctx, 3)
            adapter = trendingAdapter
            isNestedScrollingEnabled = false
        }

        banglaAdapter = MovieGridAdapter(onClick)
        binding?.rvBangla?.apply {
            layoutManager = GridLayoutManager(ctx, 3)
            adapter = banglaAdapter
            isNestedScrollingEnabled = false
        }

        hindiAdapter = MovieGridAdapter(onClick)
        binding?.rvHindi?.apply {
            layoutManager = GridLayoutManager(ctx, 3)
            adapter = hindiAdapter
            isNestedScrollingEnabled = false
        }

        allAdapter = MovieGridAdapter(onClick)
        binding?.rvAll?.apply {
            layoutManager = GridLayoutManager(ctx, 3)
            adapter = allAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun observeViewModel() {
        viewModel.loading.observe(viewLifecycleOwner) { loading ->
            val b = binding ?: return@observe
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
                android.util.Log.e("HomeFragment", "Loading observer error: ${e.message}")
            }
        }

        viewModel.bannerMovies.observe(viewLifecycleOwner) { movies ->
            if (_binding == null) return@observe
            try {
                if (movies.isNotEmpty()) {
                    bannerAdapter?.submitList(movies)
                    setupDots(movies.size)
                    startAutoScroll(movies.size)
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Banner observer error: ${e.message}")
            }
        }

        viewModel.trendingMovies.observe(viewLifecycleOwner) { movies ->
            val b = binding ?: return@observe
            trendingAdapter?.submitList(movies)
            b.sectionTrending.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.banglaMovies.observe(viewLifecycleOwner) { movies ->
            val b = binding ?: return@observe
            banglaAdapter?.submitList(movies)
            b.sectionBangla.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.hindiMovies.observe(viewLifecycleOwner) { movies ->
            val b = binding ?: return@observe
            hindiAdapter?.submitList(movies)
            b.sectionHindi.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.allMovies.observe(viewLifecycleOwner) { movies ->
            val b = binding ?: return@observe
            allAdapter?.submitList(movies)
            b.sectionAll.visibility = if (movies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.currentUser.observe(viewLifecycleOwner) { user ->
            val b = binding ?: return@observe
            try {
                if (user != null) {
                    val initial = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                    b.tvAvatarInitial.text = initial
                    if (user.photoUrl.isNotEmpty()) {
                        b.ivAvatar.loadImageSafe(user.photoUrl)
                    }
                    b.tvSubscriptionBadge.text = if (user.isPremium) "PREMIUM" else "FREE"
                    b.tvSubscriptionBadge.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "User observer error: ${e.message}")
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding?.swipeRefresh?.setOnRefreshListener {
            viewModel.loadData()
        }
    }

    private fun openMovieDetail(movie: Movie) {
        if (!isAdded || _binding == null) return
        try {
            val bundle = bundleOf(Constants.EXTRA_MOVIE_ID to movie.id)
            findNavController().navigate(R.id.action_home_to_detail, bundle)
        } catch (e: Exception) { }
    }

    override fun onPause() {
        super.onPause()
        stopAutoScroll()
    }

    override fun onResume() {
        super.onResume()
        if (bannerCount > 1) startAutoScroll(bannerCount)
    }

    override fun onDestroyView() {
        stopAutoScroll()
        try {
            _binding?.shimmerLayout?.stopShimmer()
        } catch (e: Exception) { }
        try {
            _binding?.bannerPager?.adapter = null
            _binding?.rvTrending?.adapter = null
            _binding?.rvBangla?.adapter = null
            _binding?.rvHindi?.adapter = null
            _binding?.rvAll?.adapter = null
        } catch (e: Exception) { }
        bannerAdapter = null
        trendingAdapter = null
        banglaAdapter = null
        hindiAdapter = null
        allAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
