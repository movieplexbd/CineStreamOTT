package com.ottapp.moviestream.data.repository

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.ottapp.moviestream.data.model.Movie
import kotlinx.coroutines.tasks.await

class MovieRepository {

    companion object {
        private const val TAG = "MovieRepository"
        private const val DB_URL = "https://movies-bee24-default-rtdb.firebaseio.com"

        @Volatile
        private var dbInstance: FirebaseDatabase? = null

        private fun getDatabase(): FirebaseDatabase {
            return dbInstance ?: synchronized(this) {
                dbInstance ?: FirebaseDatabase.getInstance(DB_URL).also {
                    dbInstance = it
                }
            }
        }
    }

    private val moviesRef by lazy {
        try {
            getDatabase().reference.child("movies")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get movies ref: ${e.message}", e)
            null
        }
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
                videoStreamUrl = data["videoStreamUrl"]?.toString()
                    ?: data["streamUrl"]?.toString()
                    ?: data["videoUrl"]?.toString() ?: "",
                downloadUrl    = data["downloadUrl"]?.toString() ?: "",
                category       = data["category"]?.toString() ?: "",
                imdbRating     = try {
                    when (val r = data["imdbRating"] ?: data["rating"]) {
                        is Double -> r
                        is Long   -> r.toDouble()
                        is Int    -> r.toDouble()
                        is String -> r.toDoubleOrNull() ?: 0.0
                        else      -> 0.0
                    }
                } catch (e: Exception) { 0.0 },
                year           = try {
                    when (val y = data["year"]) {
                        is Long   -> y.toInt()
                        is Int    -> y
                        is Double -> y.toInt()
                        is String -> y.toIntOrNull() ?: 0
                        else      -> 0
                    }
                } catch (e: Exception) { 0 },
                duration       = data["duration"]?.toString() ?: "",
                trending       = try {
                    when (val t = data["trending"]) {
                        is Boolean -> t
                        is String  -> t.equals("true", ignoreCase = true)
                        else       -> false
                    }
                } catch (e: Exception) { false },
                testMovie      = try {
                    when (val f = data["testMovie"] ?: data["isFree"]) {
                        is Boolean -> f
                        is String  -> f.equals("true", ignoreCase = true)
                        else       -> false
                    }
                } catch (e: Exception) { false }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse movie error for key=${snapshot.key}: ${e.message}")
            null
        }
    }

    suspend fun getAllMovies(): List<Movie> {
        val ref = moviesRef ?: return emptyList()
        return try {
            val snapshot = ref.get().await()
            snapshot.children.mapNotNull { child ->
                try {
                    snapshotToMovie(child)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing movie child: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllMovies error: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getMovieById(id: String): Movie? {
        if (id.isEmpty()) return null
        val ref = moviesRef ?: return null
        return try {
            val snapshot = ref.child(id).get().await()
            snapshotToMovie(snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "getMovieById error: ${e.message}", e)
            null
        }
    }

    suspend fun addMovie(movie: Movie): String {
        val ref = moviesRef ?: throw Exception("Database not available")
        val newRef = ref.push()
        val id = newRef.key ?: throw Exception("ID তৈরি হয়নি")
        val movieWithId = movie.copy(id = id)
        newRef.setValue(movieToMap(movieWithId)).await()
        return id
    }

    suspend fun updateMovie(movie: Movie) {
        if (movie.id.isEmpty()) throw Exception("Movie ID নেই")
        val ref = moviesRef ?: throw Exception("Database not available")
        ref.child(movie.id).updateChildren(movieToMap(movie)).await()
    }

    suspend fun deleteMovie(id: String) {
        if (id.isEmpty()) throw Exception("Movie ID নেই")
        val ref = moviesRef ?: throw Exception("Database not available")
        ref.child(id).removeValue().await()
    }

    private fun movieToMap(movie: Movie): Map<String, Any?> = mapOf(
        "title"          to movie.title,
        "description"    to movie.description,
        "bannerImageUrl" to movie.bannerImageUrl,
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
