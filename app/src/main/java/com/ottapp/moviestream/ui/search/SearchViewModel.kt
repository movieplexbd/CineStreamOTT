package com.ottapp.moviestream.ui.search

import androidx.lifecycle.*
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val repo = MovieRepository()

    private val _results = MutableLiveData<List<Movie>>()
    val results: LiveData<List<Movie>> = _results

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _activeFilter = MutableLiveData(Constants.CAT_ALL)
    val activeFilter: LiveData<String> = _activeFilter

    private var allMovies: List<Movie> = emptyList()
    private var currentQuery = ""
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            allMovies = repo.getAllMovies()
        }
    }

    fun search(query: String) {
        currentQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            _loading.value = true
            delay(300) // debounce
            val filtered = allMovies.filter {
                it.title.lowercase().contains(query.lowercase()) ||
                it.description.lowercase().contains(query.lowercase())
            }
            applyFilter(filtered)
            _loading.value = false
        }
    }

    fun setFilter(cat: String) {
        _activeFilter.value = cat
        val base = if (currentQuery.isBlank()) allMovies else
            allMovies.filter {
                it.title.lowercase().contains(currentQuery.lowercase())
            }
        applyFilter(base)
    }

    private fun applyFilter(list: List<Movie>) {
        val cat = _activeFilter.value ?: Constants.CAT_ALL
        _results.value = when (cat) {
            Constants.CAT_ALL      -> list
            Constants.CAT_TRENDING -> list.filter { it.trending }
            else                   -> list.filter { it.category == cat }
        }
    }
}
