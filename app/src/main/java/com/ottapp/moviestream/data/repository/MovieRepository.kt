package com.ottapp.moviestream.data.repository

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.ottapp.moviestream.data.model.Movie
import kotlinx.coroutines.tasks.await

class MovieRepository {

    companion object {
        private const val TAG = "MovieRepository"
        private const val DB_URL = "https://movies-bee24-default-rtdb.firebaseio.com"
    }

    private val db by lazy {
        FirebaseDatabase.getInstance(DB_URL).reference
    }

    private val moviesRef by lazy {
        db.child("movies")
    }

    @Suppress("UNCHECKED_CAST")
    private fun snapshotToMovie(snapshot: com.google.firebase.database.DataSnapshot): Movie? {
        return try {
            val rawValue = snapshot.value ?: return null

            val data: Map<*, *> = when (rawValue) {
                is Map<*, *> -> rawValue
                else -> return null
            }

            Movie(
                id             = snapshot.key ?: "",
                title          = data["title"]?.toString() ?: "",
                description    = data["description"]?.toString() ?: "",
                bannerImageUrl = data["bannerImageUrl"]?.toString()
                    ?: data["imageUrl"]?.toString()
                    ?: data["banner"]?.toString() ?: "",
                detailThumbnailUrl = data["detailThumbnailUrl"]?.toString() ?: "",
                videoStreamUrl = data["videoStreamUrl"]?.toString()
                    ?: data["streamUrl"]?.toString()
                    ?: data["videoUrl"]?.toString() ?: "",
                downloadUrl    = data["downloadUrl"]?.toString() ?: "",
                category       = data["category"]?.toString() ?: "",
                imdbRating     = when (val r = data["imdbRating"] ?: data["rating"]) {
                    is Double -> r
                    is Long   -> r.toDouble()
                    is Int    -> r.toDouble()
                    is String -> r.toDoubleOrNull() ?: 0.0
                    else      -> 0.0
                },
                year           = when (val y = data["year"]) {
                    is Long   -> y.toInt()
                    is Int    -> y
                    is Double -> y.toInt()
                    is String -> y.toIntOrNull() ?: 0
                    else      -> 0
                },
                duration       = data["duration"]?.toString() ?: "",
                trending       = when (val t = data["trending"]) {
                    is Boolean -> t
                    is String  -> t.equals("true", ignoreCase = true)
                    else       -> false
                },
                testMovie      = when (val f = data["testMovie"] ?: data["isFree"]) {
                    is Boolean -> f
                    is String  -> f.equals("true", ignoreCase = true)
                    else       -> false
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse movie error: ${e.message}")
            null
        }
    }

    suspend fun getAllMovies(): List<Movie> {
        return try {
            val snapshot = moviesRef.get().await()
            snapshot.children.mapNotNull { child -> snapshotToMovie(child) }
        } catch (e: Exception) {
            Log.e(TAG, "getAllMovies error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getMovieById(id: String): Movie? {
        if (id.isEmpty()) return null
        return try {
            val snapshot = moviesRef.child(id).get().await()
            snapshotToMovie(snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "getMovieById error: ${e.message}", e)
            null
        }
    }

    suspend fun addMovie(movie: Movie): String {
        val newRef = moviesRef.push()
        val id = newRef.key ?: throw Exception("ID তৈরি হয়নি")
        val movieWithId = movie.copy(id = id)
        newRef.setValue(movieToMap(movieWithId)).await()
        return id
    }

    suspend fun updateMovie(movie: Movie) {
        if (movie.id.isEmpty()) throw Exception("Movie ID নেই")
        moviesRef.child(movie.id).updateChildren(movieToMap(movie)).await()
    }

    suspend fun deleteMovie(id: String) {
        if (id.isEmpty()) throw Exception("Movie ID নেই")
        moviesRef.child(id).removeValue().await()
    }

    private fun movieToMap(movie: Movie): Map<String, Any?> = mapOf(
        "title"          to movie.title,
        "description"    to movie.description,
        "bannerImageUrl" to movie.bannerImageUrl,
        "detailThumbnailUrl" to movie.detailThumbnailUrl,
        "videoStreamUrl" to movie.videoStreamUrl,
        "downloadUrl"    to movie.downloadUrl,
        "category"       to movie.category,
        "imdbRating"     to movie.imdbRating,
        "year"           to movie.year,
        "duration"       to movie.duration,
        "trending"       to movie.trending,
        "testMovie"      to movie.testMovie
    )
}
