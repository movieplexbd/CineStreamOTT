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

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _loading.value = true
            try {
                allMovies = repo.getAllMovies()
                loadTrendingRequests()
                // If there's a pending query (e.g. from Reels), search it now
                if (currentQuery.isNotEmpty()) {
                    search(currentQuery)
                }
            } catch (e: Exception) {
                _error.value = e.message
                allMovies = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    private fun loadTrendingRequests() {
        viewModelScope.launch {
            try {
                val requests = requestRepo.getAllRequests()
                _trendingRequests.value = requests.filter { it.count >= 2 }.sortedByDescending { it.count }
            } catch (e: Exception) { }
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

        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            _loading.value = true
            try {
                val q = query.lowercase().trim()
                
                // Search in title, description, and category
                val filtered = allMovies.filter { movie ->
                    val title = movie.title.lowercase()
                    val desc = movie.description.lowercase()
                    val cat = movie.category.lowercase()
                    
                    title.contains(q) || q.contains(title) || 
                    desc.contains(q) || cat.contains(q)
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
        if (movieTitle.isBlank()) return
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
        if (currentQuery.isEmpty()) {
            applyFilter(allMovies)
        } else {
            search(currentQuery)
        }
    }

    private fun applyFilter(list: List<Movie>) {
        val cat = _activeFilter.value ?: Constants.CAT_ALL
        _results.value = when (cat) {
            Constants.CAT_ALL -> list
            Constants.CAT_TRENDING -> list.filter { it.trending }
            Constants.CAT_BANGLA -> list.filter { 
                it.category.lowercase().contains("bangla") || it.category.contains("বাংলা") 
            }
            Constants.CAT_HINDI -> list.filter { 
                it.category.lowercase().contains("hindi") || it.category.contains("হিন্দি") 
            }
            else -> list.filter { it.category.equals(cat, ignoreCase = true) }
        }
    }
}
