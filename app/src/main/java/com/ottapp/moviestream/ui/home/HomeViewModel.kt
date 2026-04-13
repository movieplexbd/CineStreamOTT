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
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.data.repository.UserRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val watchHistoryManager = WatchHistoryManager(app.applicationContext)

    companion object {
        private const val TAG = "HomeViewModel"
    }

    private val movieRepo = MovieRepository()
    private val userRepo = UserRepository()

    // loading starts TRUE so shimmer shows immediately
    private val _continueWatching = MutableLiveData<List<WatchHistoryEntry>>(emptyList())
    val continueWatching: LiveData<List<WatchHistoryEntry>> = _continueWatching

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _bannerMovies = MutableLiveData<List<Movie>>(emptyList())
    val bannerMovies: LiveData<List<Movie>> = _bannerMovies

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
            try {
                // Load continue watching from local history
            _continueWatching.value = watchHistoryManager.getContinueWatching()

            // Load movies
                val all = safeGetMovies()
                _allMovies.value = all

                val trending = all.filter { it.trending }
                _trendingMovies.value = trending
                _bannerMovies.value = if (trending.isNotEmpty()) trending.take(5) else all.take(5)

                _banglaMovies.value = all.filter { m ->
                    m.category.lowercase().let { it.contains("bangla") || it.contains("বাংলা") }
                }
                _hindiMovies.value = all.filter { m ->
                    m.category.lowercase().let { it.contains("hindi") || it.contains("হিন্দি") }
                }

                // Load user info (one-shot, no persistent listener)
                safeGetUser()

            } catch (e: Exception) {
                Log.e(TAG, "loadData error: ${e.message}", e)
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun safeGetMovies(): List<Movie> {
        return try {
            withTimeoutOrNull(12000) {
                movieRepo.getAllMovies()
            } ?: emptyList()
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
