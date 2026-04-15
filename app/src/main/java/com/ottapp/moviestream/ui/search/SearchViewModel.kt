package com.ottapp.moviestream.ui.search

import androidx.lifecycle.*
import com.ottapp.moviestream.data.model.Movie
import com.ottapp.moviestream.data.model.MovieRequest
import com.ottapp.moviestream.data.repository.MovieRepository
import com.ottapp.moviestream.data.repository.RequestRepository
import com.ottapp.moviestream.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {

    private val repo = MovieRepository()
    private val requestRepo = RequestRepository()

    private val _results = MutableLiveData<List<Movie>>(emptyList())
    val results: LiveData<List<Movie>> = _results

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _activeFilter = MutableLiveData(Constants.CAT_ALL)
    val activeFilter: LiveData<String> = _activeFilter

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _requestStatus = MutableLiveData<Boolean?>()
    val requestStatus: LiveData<Boolean?> = _requestStatus

    private val _trendingRequests = MutableLiveData<List<MovieRequest>>(emptyList())
    val trendingRequests: LiveData<List<MovieRequest>> = _trendingRequests

    private var allMovies: List<Movie> = emptyList()
    private var currentQuery = ""
    private var searchJob: Job? = null
    private var moviesLoaded = false

    init {
        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            try {
                _loading.value = true
                allMovies = repo.getAllMovies()
                moviesLoaded = allMovies.isNotEmpty()
                loadTrendingRequests()
                if (currentQuery.isNotBlank()) {
                    search(currentQuery)
                }
            } catch (e: Exception) {
                _error.value = e.message
                allMovies = emptyList()
                moviesLoaded = false
            } finally {
                _loading.value = false
            }
        }
    }

    fun retryLoadMovies() {
        if (!moviesLoaded) {
            loadMovies()
        }
    }

    private fun loadTrendingRequests() {
        viewModelScope.launch {
            val requests = requestRepo.getAllRequests()
            _trendingRequests.value = requests.filter { it.count >= 5 }.sortedByDescending { it.count }
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

        if (allMovies.isEmpty() && !moviesLoaded) {
            loadMovies()
            return
        }

        searchJob = viewModelScope.launch {
            _loading.value = true
            try {
                delay(300)
                val q = query.lowercase().trim()

                val filtered = allMovies.filter { movie ->
                    val title = movie.title.orEmpty().lowercase()
                    val desc = movie.description.orEmpty().lowercase()
                    val cat = movie.category.orEmpty().lowercase()

                    if (title.isEmpty() && desc.isEmpty() && cat.isEmpty()) return@filter false

                    val words = q.split("\\s+".toRegex()).filter { it.length >= 2 }
                    val titleMatch = title.contains(q)
                    val wordMatch = words.isNotEmpty() && words.all { w ->
                        title.contains(w) || desc.contains(w) || cat.contains(w)
                    }
                    val descMatch = desc.contains(q)
                    val catMatch = cat.contains(q)

                    titleMatch || wordMatch || descMatch || catMatch
                }
                applyFilter(filtered)
            } catch (e: Exception) {
                _results.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    fun submitRequest(movieTitle: String) {
        viewModelScope.launch {
            _loading.value = true
            val success = requestRepo.submitRequest(movieTitle)
            _requestStatus.value = success
            _loading.value = false
            if (success) loadTrendingRequests()
        }
    }

    fun resetRequestStatus() {
        _requestStatus.value = null
    }

    fun setFilter(cat: String) {
        _activeFilter.value = cat
        val q = currentQuery.lowercase()
        val base = if (currentQuery.isBlank()) allMovies else
            allMovies.filter {
                it.title.orEmpty().lowercase().contains(q) ||
                it.description.orEmpty().lowercase().contains(q) ||
                it.category.orEmpty().lowercase().contains(q)
            }
        applyFilter(base)
    }

    private fun applyFilter(list: List<Movie>) {
        val cat = _activeFilter.value ?: Constants.CAT_ALL
        _results.value = when (cat) {
            Constants.CAT_ALL      -> list
            Constants.CAT_TRENDING -> list.filter { it.trending }
            else                   -> list.filter { movie ->
                val c = movie.category.orEmpty().lowercase().trim()
                val s = cat.lowercase().trim()
                c == s || c.contains(s) || s.contains(c)
            }
        }
    }
}
