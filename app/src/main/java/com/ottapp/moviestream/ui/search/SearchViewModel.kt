package com.ottapp.moviestream.ui.search

import android.util.Log
import androidx.lifecycle.*
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.util.Constants
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    companion object {
        private const val TAG = "SearchViewModel"
    }

    private val repo = MovieRepository()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Coroutine exception: ${throwable.message}", throwable)
        _loading.postValue(false)
    }

    private val _results = MutableLiveData<List<Movie>>(emptyList())
    val results: LiveData<List<Movie>> = _results

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _activeFilter = MutableLiveData(Constants.CAT_ALL)
    val activeFilter: LiveData<String> = _activeFilter

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private var allMovies: List<Movie> = emptyList()
    private var currentQuery = ""
    private var searchJob: Job? = null

    init {
        viewModelScope.launch(exceptionHandler) {
            try {
                allMovies = repo.getAllMovies()
            } catch (e: Exception) {
                Log.e(TAG, "Preload error: ${e.message}")
                _error.value = e.message
                allMovies = emptyList()
            }
        }
    }

    fun search(query: String) {
        currentQuery = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _results.value = emptyList()
            _loading.value = false
            return
        }
        searchJob = viewModelScope.launch(exceptionHandler) {
            _loading.value = true
            try {
                delay(300)
                val q = query.lowercase()
                val filtered = allMovies.filter {
                    it.title.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.category.lowercase().contains(q)
                }
                applyFilter(filtered)
            } catch (e: Exception) {
                Log.e(TAG, "search error: ${e.message}")
                _results.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun setFilter(cat: String) {
        _activeFilter.value = cat
        val q = currentQuery.lowercase()
        val base = if (currentQuery.isBlank()) allMovies else
            allMovies.filter {
                it.title.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
            }
        applyFilter(base)
    }

    private fun applyFilter(list: List<Movie>) {
        val cat = _activeFilter.value ?: Constants.CAT_ALL
        _results.value = try {
            when (cat) {
                Constants.CAT_ALL      -> list
                Constants.CAT_TRENDING -> list.filter { it.trending }
                else                   -> list.filter { movie ->
                    val c = movie.category.lowercase().trim()
                    val s = cat.lowercase().trim()
                    c == s || c.contains(s) || s.contains(c)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "applyFilter error: ${e.message}")
            list
        }
    }
}
