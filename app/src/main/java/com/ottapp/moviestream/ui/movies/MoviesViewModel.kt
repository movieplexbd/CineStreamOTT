package com.ottapp.moviestream.ui.movies

import androidx.lifecycle.*
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.util.Constants
import kotlinx.coroutines.launch

class MoviesViewModel : ViewModel() {

    private val repo = MovieRepository()

    private val _allMovies = MutableLiveData<List<Movie>>(emptyList())
    private val _filteredMovies = MutableLiveData<List<Movie>>(emptyList())
    val filteredMovies: LiveData<List<Movie>> = _filteredMovies

    private val _loading = MutableLiveData(true)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var selectedCategory = Constants.CAT_ALL

    init { loadMovies() }

    fun loadMovies() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val movies = repo.getAllMovies()
                _allMovies.value = movies
                applyFilter(movies)
            } catch (e: Exception) {
                _error.value = e.message
                _filteredMovies.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun setCategory(cat: String) {
        selectedCategory = cat
        applyFilter(_allMovies.value ?: emptyList())
    }

    private fun applyFilter(all: List<Movie>) {
        _filteredMovies.value = when (selectedCategory) {
            Constants.CAT_ALL      -> all
            Constants.CAT_TRENDING -> all.filter { it.trending }
            else                   -> all.filter { movie ->
                val cat = movie.category.lowercase().trim()
                val sel = selectedCategory.lowercase().trim()
                cat == sel || cat.contains(sel) || sel.contains(cat)
            }
        }
    }
}
