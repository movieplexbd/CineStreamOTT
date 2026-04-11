package com.ottapp.moviestream.ui.home

import androidx.lifecycle.*
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.model.User
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.data.repository.UserRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val movieRepo = MovieRepository()
    private val userRepo  = UserRepository()

    private val _bannerMovies   = MutableLiveData<List<Movie>>(emptyList())
    val bannerMovies: LiveData<List<Movie>> = _bannerMovies

    private val _trendingMovies = MutableLiveData<List<Movie>>(emptyList())
    val trendingMovies: LiveData<List<Movie>> = _trendingMovies

    private val _banglaMovies   = MutableLiveData<List<Movie>>(emptyList())
    val banglaMovies: LiveData<List<Movie>> = _banglaMovies

    private val _hindiMovies    = MutableLiveData<List<Movie>>(emptyList())
    val hindiMovies: LiveData<List<Movie>> = _hindiMovies

    private val _allMovies      = MutableLiveData<List<Movie>>(emptyList())
    val allMovies: LiveData<List<Movie>> = _allMovies

    private val _currentUser    = MutableLiveData<User?>()
    val currentUser: LiveData<User?> = _currentUser

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    init {
        loadData()
        observeUser()
    }

    fun loadData() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val all = movieRepo.getAllMovies()

                // All movies
                _allMovies.value = all

                // Trending
                val trending = all.filter { it.trending }
                _trendingMovies.value = trending

                // Banner — trending first, fallback all
                _bannerMovies.value = trending.take(5).ifEmpty { all.take(5) }

                // Bangla Dubbed — flexible matching
                _banglaMovies.value = all.filter { movie ->
                    val cat = movie.category.lowercase().trim()
                    cat.contains("bangla") || cat.contains("বাংলা")
                }

                // Hindi Dubbed — flexible matching
                _hindiMovies.value = all.filter { movie ->
                    val cat = movie.category.lowercase().trim()
                    cat.contains("hindi") || cat.contains("হিন্দি")
                }

            } catch (e: Exception) {
                _error.value = e.message
                // Set empty lists on error so UI shows empty state
                _allMovies.value = emptyList()
                _bannerMovies.value = emptyList()
                _trendingMovies.value = emptyList()
                _banglaMovies.value = emptyList()
                _hindiMovies.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    private fun observeUser() {
        viewModelScope.launch {
            userRepo.getCurrentUserFlow()
                .catch { _error.value = it.message }
                .collect { _currentUser.value = it }
        }
    }
}
