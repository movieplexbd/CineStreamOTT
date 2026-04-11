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
    private val userRepo = UserRepository()

    private val _bannerMovies = MutableLiveData<List<Movie>>()
    val bannerMovies: LiveData<List<Movie>> = _bannerMovies

    private val _trendingMovies = MutableLiveData<List<Movie>>()
    val trendingMovies: LiveData<List<Movie>> = _trendingMovies

    private val _banglaMovies = MutableLiveData<List<Movie>>()
    val banglaMovies: LiveData<List<Movie>> = _banglaMovies

    private val _hindiMovies = MutableLiveData<List<Movie>>()
    val hindiMovies: LiveData<List<Movie>> = _hindiMovies

    private val _currentUser = MutableLiveData<User?>()
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
            try {
                val all = movieRepo.getAllMovies()
                val trending = all.filter { it.trending }
                _bannerMovies.value  = trending.take(5).ifEmpty { all.take(5) }
                _trendingMovies.value = trending
                _banglaMovies.value  = all.filter { it.category.orEmpty() == Movie.CATEGORY_BANGLA }
                _hindiMovies.value   = all.filter { it.category.orEmpty() == Movie.CATEGORY_HINDI }
            } catch (e: Exception) {
                _error.value = e.message
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
