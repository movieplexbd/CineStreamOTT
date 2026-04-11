package com.ottapp.moviestream.data.repository

import com.google.firebase.database.FirebaseDatabase
import com.ottapp.moviestream.data.model.Movie
import kotlinx.coroutines.tasks.await

class MovieRepository {

    private val db = FirebaseDatabase.getInstance("https://movies-bee24-default-rtdb.firebaseio.com").reference
    private val moviesRef = db.child("movies")

    // Manually parse Firebase snapshot to Movie
    // This avoids crash when Firebase stores year as String instead of Int
    @Suppress("UNCHECKED_CAST")
    private fun snapshotToMovie(snapshot: com.google.firebase.database.DataSnapshot): Movie? {
        return try {
            // Support both Map<String,*> and direct Movie mapping
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
            null // Return null on parse error so one bad entry doesn't crash the whole list
        }
    }

    suspend fun getAllMovies(): List<Movie> {
        val snapshot = moviesRef.get().await()
        return snapshot.children.mapNotNull { child -> snapshotToMovie(child) }
    }

    suspend fun getMovieById(id: String): Movie? {
        if (id.isEmpty()) return null
        val snapshot = moviesRef.child(id).get().await()
        return snapshotToMovie(snapshot)
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

    // Convert Movie object to Firebase-compatible Map
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
