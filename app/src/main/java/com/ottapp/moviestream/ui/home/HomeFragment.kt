package com.ottapp.moviestream.ui.home

  import android.os.Bundle
  import android.os.Handler
  import android.os.Looper
  import android.view.LayoutInflater
  import android.view.View
  import android.view.ViewGroup
  import androidx.core.os.bundleOf
  import androidx.fragment.app.Fragment
  import androidx.fragment.app.viewModels
  import androidx.navigation.fragment.findNavController
  import androidx.recyclerview.widget.LinearLayoutManager
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

      private lateinit var bannerAdapter:   BannerAdapter
      private lateinit var trendingAdapter: MovieGridAdapter
      private lateinit var banglaAdapter:   MovieGridAdapter
      private lateinit var hindiAdapter:    MovieGridAdapter
      private lateinit var allAdapter:      MovieGridAdapter

      private val bannerHandler = Handler(Looper.getMainLooper())
      private var bannerRunnable: Runnable? = null
      private var currentBannerIndex = 0

      override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
          _binding = FragmentHomeBinding.inflate(inflater, container, false)
          return binding.root
      }

      override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
          super.onViewCreated(view, savedInstanceState)
          setupAdapters()
          observeViewModel()
          setupSwipeRefresh()
      }

      private fun setupAdapters() {
          bannerAdapter = BannerAdapter { movie -> openMovieDetail(movie) }
          binding.viewPagerBanner.adapter = bannerAdapter
          binding.viewPagerBanner.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
              override fun onPageSelected(position: Int) {
                  currentBannerIndex = position
                  updateBannerDots(position)
              }
          })

          val onMovieClick: (Movie) -> Unit = { movie -> openMovieDetail(movie) }

          trendingAdapter = MovieGridAdapter(onMovieClick)
          binding.rvTrending.apply {
              layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
              adapter = trendingAdapter
          }

          banglaAdapter = MovieGridAdapter(onMovieClick)
          binding.rvBangla.apply {
              layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
              adapter = banglaAdapter
          }

          hindiAdapter = MovieGridAdapter(onMovieClick)
          binding.rvHindi.apply {
              layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
              adapter = hindiAdapter
          }

          allAdapter = MovieGridAdapter(onMovieClick)
          binding.rvAll.apply {
              layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
              adapter = allAdapter
          }
      }

      private fun observeViewModel() {
          viewModel.loading.observe(viewLifecycleOwner) { loading ->
              if (loading) binding.shimmerLayout.show() else binding.shimmerLayout.hide()
              if (!loading) binding.scrollContent.show() else binding.scrollContent.hide()
          }

          viewModel.bannerMovies.observe(viewLifecycleOwner) { movies ->
              bannerAdapter.submitList(movies)
              setupBannerDots(movies.size)
              startBannerAuto(movies.size)
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

      private fun setupBannerDots(count: Int) {
          binding.layoutDots.removeAllViews()
          val context = requireContext()
          repeat(count) { i ->
              val dot = android.widget.ImageView(context).apply {
                  setImageResource(if (i == 0) R.drawable.dot_active else R.drawable.dot_inactive)
                  val lp = android.widget.LinearLayout.LayoutParams(
                      android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                      android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                  ).apply { setMargins(6, 0, 6, 0) }
                  layoutParams = lp
              }
              binding.layoutDots.addView(dot)
          }
      }

      private fun updateBannerDots(position: Int) {
          val count = binding.layoutDots.childCount
          for (i in 0 until count) {
              val dot = binding.layoutDots.getChildAt(i) as? android.widget.ImageView
              dot?.setImageResource(if (i == position) R.drawable.dot_active else R.drawable.dot_inactive)
          }
      }

      private fun startBannerAuto(count: Int) {
          if (count <= 1) return
          bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
          bannerRunnable = object : Runnable {
              override fun run() {
                  if (_binding == null) return
                  val next = (currentBannerIndex + 1) % count
                  binding.viewPagerBanner.setCurrentItem(next, true)
                  bannerHandler.postDelayed(this, 4000)
              }
          }
          bannerHandler.postDelayed(bannerRunnable!!, 4000)
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
          bannerRunnable?.let { bannerHandler.removeCallbacks(it) }
          _binding = null
          super.onDestroyView()
      }
  }
  