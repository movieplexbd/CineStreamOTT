package com.ottapp.moviestream.data.repository

import com.google.firebase.database.*
import com.ottapp.moviestream.data.model.Movie
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MovieRepository {

    private val db = FirebaseDatabase.getInstance().reference
    private val moviesRef = db.child("movies")

    // ── Realtime flow of ALL movies ──────────────────────────────────────────
    fun getMoviesFlow(): Flow<List<Movie>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<Movie>()
                snapshot.children.forEach { child ->
                    val movie = child.getValue(Movie::class.java)
                    if (movie != null) {
                        list.add(movie.copy(id = child.key ?: ""))
                    }
                }
                trySend(list)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        moviesRef.addValueEventListener(listener)
        awaitClose { moviesRef.removeEventListener(listener) }
    }

    // ── Single fetch ─────────────────────────────────────────────────────────
    suspend fun getAllMovies(): List<Movie> {
        val snapshot = moviesRef.get().await()
        return snapshot.children.mapNotNull { child ->
            child.getValue(Movie::class.java)?.copy(id = child.key ?: "")
        }
    }

    // ── Trending movies ──────────────────────────────────────────────────────
    suspend fun getTrendingMovies(): List<Movie> {
        val snapshot = moviesRef.orderByChild("trending").equalTo(true).get().await()
        return snapshot.children.mapNotNull { child ->
            child.getValue(Movie::class.java)?.copy(id = child.key ?: "")
        }
    }

    // ── Test movies (Free users can watch) ──────────────────────────────────
    suspend fun getTestMovies(): List<Movie> {
        val snapshot = moviesRef.orderByChild("testMovie").equalTo(true).get().await()
        return snapshot.children.mapNotNull { child ->
            child.getValue(Movie::class.java)?.copy(id = child.key ?: "")
        }
    }

    // ── Movies by category ────────────────────────────────────────────────────
    suspend fun getMoviesByCategory(category: String): List<Movie> {
        val snapshot = moviesRef.orderByChild("category").equalTo(category).get().await()
        return snapshot.children.mapNotNull { child ->
            child.getValue(Movie::class.java)?.copy(id = child.key ?: "")
        }
    }

    // ── Search movies ─────────────────────────────────────────────────────────
    suspend fun searchMovies(query: String): List<Movie> {
        val all = getAllMovies()
        val q = query.lowercase().trim()
        return all.filter { movie ->
            movie.title.lowercase().contains(q) ||
            movie.description.lowercase().contains(q) ||
            movie.category.lowercase().contains(q)
        }
    }

    // ── Get single movie ──────────────────────────────────────────────────────
    suspend fun getMovieById(movieId: String): Movie? {
        val snapshot = moviesRef.child(movieId).get().await()
        return snapshot.getValue(Movie::class.java)?.copy(id = movieId)
    }

    // ── Admin: Add movie ──────────────────────────────────────────────────────
    suspend fun addMovie(movie: Movie): String {
        val newRef = moviesRef.push()
        val movieWithId = movie.copy(id = newRef.key ?: "")
        newRef.setValue(movieWithId).await()
        return newRef.key ?: ""
    }

    // ── Admin: Update movie ───────────────────────────────────────────────────
    suspend fun updateMovie(movie: Movie) {
        moviesRef.child(movie.id).setValue(movie).await()
    }

    // ── Admin: Delete movie ───────────────────────────────────────────────────
    suspend fun deleteMovie(movieId: String) {
        moviesRef.child(movieId).removeValue().await()
    }
}
