package com.ottapp.moviestream.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.model.User
import com.ottapp.moviestream.util.WatchHistoryManager
import com.ottapp.moviestream.util.WatchHistoryEntry
import com.ottapp.moviestream.util.MovieCache
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ottapp.moviestream.data.model.Banner
import com.ottapp.moviestream.data.repository.BannerRepository
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.data.repository.UserRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val watchHistoryManager = WatchHistoryManager(app.applicationContext)
    private val ctx = app.applicationContext

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val movieRepo = MovieRepository()
    private val userRepo = UserRepository()
    private val bannerRepo = BannerRepository()

    private val _continueWatching = MutableLiveData<List<WatchHistoryEntry>>(emptyList())
    val continueWatching: LiveData<List<WatchHistoryEntry>> = _continueWatching

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _banners = MutableLiveData<List<Banner>>(emptyList())
    val banners: LiveData<List<Banner>> = _banners

    private val _trendingMovies = MutableLiveData<List<Movie>>(emptyList())
    val trendingMovies: LiveData<List<Movie>> = _trendingMovies

    private val _banglaMovies = MutableLiveData<List<Movie>>(emptyList())
    val banglaMovies: LiveData<List<Movie>> = _banglaMovies

    private val _hindiMovies = MutableLiveData<List<Movie>>(emptyList())
    val hindiMovies: LiveData<List<Movie>> = _hindiMovies

    private val _allMovies = MutableLiveData<List<Movie>>(emptyList())
    val allMovies: LiveData<List<Movie>> = _allMovies

    private val _currentUser = MutableLiveData<User?>(null)
    val currentUser: LiveData<User?> = _currentUser

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _loading.value = true

            // 1. Serve cache immediately if available (offline-first)
            val cachedMovies = MovieCache.loadMovies(ctx)
            if (cachedMovies != null) {
                applyMovieLists(cachedMovies)
            }

            try {
                // Always fetch continue watching
                _continueWatching.value = watchHistoryManager.getContinueWatching()

                // Fetch movies and update cache
                val all = safeGetMovies()
                if (all.isNotEmpty()) {
                    MovieCache.saveMovies(ctx, all)
                    applyMovieLists(all)
                    
                    // After loading full movie list, refresh continue watching to ensure banners are present
                    val history = watchHistoryManager.getContinueWatching()
                    val updatedHistory = history.map { entry ->
                        if (entry.bannerUrl.isEmpty()) {
                            val movie = all.find { it.id == entry.movieId }
                            if (movie != null) {
                                entry.copy(bannerUrl = movie.bannerImageUrl)
                            } else entry
                        } else entry
                    }
                    _continueWatching.value = updatedHistory
                }

                // Always fetch banners from database (no caching as requested)
                val banners = bannerRepo.getAllBanners()
                _banners.value = banners

                safeGetUser()

            } catch (e: Exception) {
                Log.e(TAG, "loadData error: ${e.message}", e)
            } finally {
                _loading.value = false
            }
        }
    }

    private fun applyMovieLists(all: List<Movie>) {
        _allMovies.value = all
        _trendingMovies.value = all.filter { it.trending }
        _banglaMovies.value = all.filter { m ->
            m.category.lowercase().let { it.contains("bangla") || it.contains("বাংলা") }
        }
        _hindiMovies.value = all.filter { m ->
            m.category.lowercase().let { it.contains("hindi") || it.contains("হিন্দি") }
        }
    }

    private suspend fun safeGetMovies(): List<Movie> {
        return try {
            withTimeoutOrNull(12000) { movieRepo.getAllMovies() } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getAllMovies error: ${e.message}", e)
            emptyList()
        }
    }

    private suspend fun safeGetUser() {
        try {
            val user = userRepo.getCurrentUser()
            _currentUser.value = user
        } catch (e: Exception) {
            Log.e(TAG, "getUser error: ${e.message}", e)
            _currentUser.value = null
        }
    }
}
