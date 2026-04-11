package com.ottapp.moviestream.ui.movies

import androidx.lifecycle.*
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.util.Constants
import kotlinx.coroutines.launch

class MoviesViewModel : ViewModel() {

    private val repo = MovieRepository()

    private val _allMovies = MutableLiveData<List<Movie>>()
    private val _filteredMovies = MutableLiveData<List<Movie>>()
    val filteredMovies: LiveData<List<Movie>> = _filteredMovies

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private var selectedCategory = Constants.CAT_ALL

    init { loadMovies() }

    fun loadMovies() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val movies = repo.getAllMovies()
                _allMovies.value = movies
                applyFilter()
            } finally {
                _loading.value = false
            }
        }
    }

    fun setCategory(cat: String) {
        selectedCategory = cat
        applyFilter()
    }

    private fun applyFilter() {
        val all = _allMovies.value ?: return
        _filteredMovies.value = when (selectedCategory) {
            Constants.CAT_ALL      -> all
            Constants.CAT_TRENDING -> all.filter { it.trending }
            else                   -> all.filter { it.category == selectedCategory }
        }
    }
}
