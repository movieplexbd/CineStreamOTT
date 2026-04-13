package com.ottapp.moviestream.ui.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.ottapp.moviestream.R
import com.ottapp.moviestream.adapter.BannerAdapter
import com.ottapp.moviestream.adapter.MovieGridAdapter
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.databinding.FragmentHomeBinding
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.WatchHistoryEntry
import com.ottapp.moviestream.adapter.ContinueWatchingAdapter
import com.ottapp.moviestream.util.hide
import com.ottapp.moviestream.util.loadImage
import com.ottapp.moviestream.util.show

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val vm: HomeViewModel by viewModels()

    private var bannerAdapter: BannerAdapter? = null
    private var trendingAdapter: MovieGridAdapter? = null
    private var banglaAdapter: MovieGridAdapter? = null
    private var hindiAdapter: MovieGridAdapter? = null
    private var allAdapter: MovieGridAdapter? = null
    private var continueWatchingAdapter: ContinueWatchingAdapter? = null

    private val scrollHandler = Handler(Looper.getMainLooper())
    private var scrollRunnable: Runnable? = null
    private var bannerTotal = 0

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            val binding = FragmentHomeBinding.inflate(inflater, container, false)
            _binding = binding
            binding.root
        } catch (e: Exception) {
            android.util.Log.e("HomeFragment", "inflate error: ${e.message}", e)
            null
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (_binding == null) return
        try { initAdapters() }  catch (e: Exception) { log("initAdapters: ${e.message}") }
        try { initBanner() }    catch (e: Exception) { log("initBanner: ${e.message}") }
        try { observeData() }   catch (e: Exception) { log("observeData: ${e.message}") }
        try { initRefresh() }   catch (e: Exception) { log("initRefresh: ${e.message}") }
        try { initSearch() }    catch (e: Exception) { log("initSearch: ${e.message}") }
        try { initReels() }     catch (e: Exception) { log("initReels: ${e.message}") }
    }

    override fun onResume() {
        super.onResume()
        if (bannerTotal > 1) startScroll(bannerTotal)
    }

    override fun onPause() {
        super.onPause()
        stopScroll()
        runCatching { _binding?.shimmerLayout?.stopShimmer() }
    }

    override fun onDestroyView() {
        stopScroll()
        runCatching { _binding?.shimmerLayout?.stopShimmer() }
        runCatching { _binding?.bannerPager?.adapter = null }
        bannerAdapter = null
        trendingAdapter = null
        banglaAdapter = null
        hindiAdapter = null
        allAdapter = null
        continueWatchingAdapter = null
        _binding = null
        super.onDestroyView()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun initAdapters() {
        val onClick: (Movie) -> Unit = { goToDetail(it) }

        trendingAdapter = MovieGridAdapter(onClick)
        banglaAdapter   = MovieGridAdapter(onClick)
        hindiAdapter    = MovieGridAdapter(onClick)
        allAdapter      = MovieGridAdapter(onClick)

        _binding?.rvTrending?.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = trendingAdapter
            isNestedScrollingEnabled = false
        }
        _binding?.rvBangla?.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = banglaAdapter
            isNestedScrollingEnabled = false
        }
        _binding?.rvHindi?.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = hindiAdapter
            isNestedScrollingEnabled = false
        }
        _binding?.rvAll?.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = allAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun initBanner() {
        val adp = BannerAdapter { goToDetail(it) }
        bannerAdapter = adp
        _binding?.bannerPager?.let { pager ->
            pager.adapter = adp
            pager.offscreenPageLimit = 1
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateDots(position)
                }
            })
        }
    }

    private fun initRefresh() {
        _binding?.swipeRefresh?.setOnRefreshListener {
            vm.loadData()
        }
    }

    private fun initSearch() {
        _binding?.btnSearch?.setOnClickListener {
            try {
                findNavController().navigate(R.id.searchFragment)
            } catch (e: Exception) {
                log("search nav: ${e.message}")
            }
        }
    }

    private fun initReels() {
        _binding?.btnReels?.setOnClickListener {
            try {
                findNavController().navigate(R.id.reelsFragment)
            } catch (e: Exception) {
                log("reels nav: ${e.message}")
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observers
    // ─────────────────────────────────────────────────────────────────────────

    private fun observeData() {
        vm.loading.observe(viewLifecycleOwner) { isLoading ->
            val b = _binding ?: return@observe
            runCatching {
                if (isLoading) {
                    b.shimmerLayout.startShimmer()
                    b.shimmerLayout.show()
                    b.contentWrapper.hide()
                } else {
                    b.shimmerLayout.stopShimmer()
                    b.shimmerLayout.hide()
                    b.contentWrapper.show()
                    b.swipeRefresh.isRefreshing = false
                }
            }
        }

        vm.bannerMovies.observe(viewLifecycleOwner) { list ->
            if (_binding == null) return@observe
            runCatching {
                bannerAdapter?.submitList(list)
                buildDots(list.size)
                if (list.size > 1) startScroll(list.size) else stopScroll()
            }
        }

        vm.continueWatching.observe(viewLifecycleOwner) { list ->
            val b = _binding ?: return@observe
            runCatching {
                val section = b.root.findViewById<android.view.View>(R.id.section_continue_watching)
                val rv = b.root.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_continue_watching)
                if (list.isEmpty()) {
                    section?.visibility = android.view.View.GONE
                } else {
                    section?.visibility = android.view.View.VISIBLE
                    if (continueWatchingAdapter == null) {
                        continueWatchingAdapter = ContinueWatchingAdapter { entry ->
                            try {
                                findNavController().navigate(R.id.action_home_to_detail,
                                    androidx.core.os.bundleOf(Constants.EXTRA_MOVIE_ID to entry.movieId))
                            } catch (e: Exception) {}
                        }
                        rv?.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
                        rv?.adapter = continueWatchingAdapter
                    }
                    continueWatchingAdapter?.submitList(list)
                }
            }
        }

        vm.trendingMovies.observe(viewLifecycleOwner) { list ->
            val b = _binding ?: return@observe
            runCatching {
                trendingAdapter?.submitList(list)
                b.sectionTrending.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        vm.banglaMovies.observe(viewLifecycleOwner) { list ->
            val b = _binding ?: return@observe
            runCatching {
                banglaAdapter?.submitList(list)
                b.sectionBangla.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        vm.hindiMovies.observe(viewLifecycleOwner) { list ->
            val b = _binding ?: return@observe
            runCatching {
                hindiAdapter?.submitList(list)
                b.sectionHindi.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        vm.allMovies.observe(viewLifecycleOwner) { list ->
            val b = _binding ?: return@observe
            runCatching {
                allAdapter?.submitList(list)
                b.sectionAll.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        vm.currentUser.observe(viewLifecycleOwner) { user ->
            val b = _binding ?: return@observe
            runCatching {
                if (user == null) return@runCatching
                val initial = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                b.tvAvatarInitial.text = initial
                b.tvAvatarInitial.show()
                if (user.photoUrl.isNotEmpty()) {
                    b.ivAvatar.loadImage(user.photoUrl)
                    b.ivAvatar.show()
                }
                b.tvSubscriptionBadge.text = if (user.isPremium) "PREMIUM" else "FREE"
                b.tvSubscriptionBadge.show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Banner dots
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildDots(count: Int) {
        bannerTotal = count
        val container = _binding?.bannerDots ?: return
        container.removeAllViews()
        if (count <= 1) return
        val dp = resources.displayMetrics.density
        repeat(count) { i ->
            runCatching {
                val sz = ((if (i == 0) 10 else 7) * dp).toInt()
                val iv = ImageView(requireContext())
                val lp = LinearLayout.LayoutParams(sz, sz)
                lp.setMargins(4, 0, 4, 0)
                iv.layoutParams = lp
                iv.setImageResource(if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive)
                container.addView(iv)
            }
        }
    }

    private fun updateDots(selected: Int) {
        val container = _binding?.bannerDots ?: return
        val dp = resources.displayMetrics.density
        for (i in 0 until container.childCount) {
            runCatching {
                val iv = container.getChildAt(i) as? ImageView ?: return@runCatching
                val active = (i == selected)
                val sz = ((if (active) 10 else 7) * dp).toInt()
                val lp = LinearLayout.LayoutParams(sz, sz)
                lp.setMargins(4, 0, 4, 0)
                iv.layoutParams = lp
                iv.setImageResource(if (active) R.drawable.dot_active else R.drawable.dot_inactive)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Auto scroll
    // ─────────────────────────────────────────────────────────────────────────

    private fun startScroll(count: Int) {
        stopScroll()
        if (count <= 1) return
        scrollRunnable = object : Runnable {
            override fun run() {
                val pager = _binding?.bannerPager ?: return
                runCatching {
                    pager.setCurrentItem((pager.currentItem + 1) % count, true)
                }
                scrollHandler.postDelayed(this, 4000)
            }
        }
        scrollHandler.postDelayed(scrollRunnable!!, 4000)
    }

    private fun stopScroll() {
        scrollRunnable?.let { scrollHandler.removeCallbacks(it) }
        scrollRunnable = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────

    private fun goToDetail(movie: Movie) {
        if (!isAdded || _binding == null) return
        runCatching {
            findNavController().navigate(
                R.id.action_home_to_detail,
                bundleOf(Constants.EXTRA_MOVIE_ID to movie.id)
            )
        }
    }

    private fun log(msg: String) = android.util.Log.e("HomeFragment", msg)
}
