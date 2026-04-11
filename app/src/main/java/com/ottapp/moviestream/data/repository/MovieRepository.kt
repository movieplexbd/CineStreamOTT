package com.ottapp.moviestream.data.repository

  import com.google.firebase.database.FirebaseDatabase
  import com.ottapp.moviestream.data.model.Movie
  import kotlinx.coroutines.tasks.await

  class MovieRepository {

      private val db = FirebaseDatabase.getInstance("https://movies-bee24-default-rtdb.firebaseio.com").reference
      private val moviesRef = db.child("movies")

      // Firebase snapshot থেকে Movie ম্যানুয়ালি parse করা
      // কারণ: Firebase-এ year String হিসেবে সেভ হলে automatic mapping crash করে
      @Suppress("UNCHECKED_CAST")
      private fun snapshotToMovie(snapshot: com.google.firebase.database.DataSnapshot): Movie? {
          return try {
              val data = snapshot.value as? Map<*, *> ?: return null
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
                  imdbRating     = data["imdbRating"]?.toString()?.toDoubleOrNull()
                      ?: data["rating"]?.toString()?.toDoubleOrNull() ?: 0.0,
                  year           = data["year"]?.toString()?.toIntOrNull() ?: 0,
                  duration       = data["duration"]?.toString() ?: "",
                  trending       = data["trending"]?.toString()?.toBoolean()
                      ?: (data["trending"] as? Boolean) ?: false,
                  testMovie      = data["testMovie"]?.toString()?.toBoolean()
                      ?: (data["testMovie"] as? Boolean)
                      ?: data["isFree"]?.toString()?.toBoolean() ?: false
              )
          } catch (e: Exception) {
              null // parse হলে null return করো, পুরো list crash করবে না
          }
      }

      suspend fun getAllMovies(): List<Movie> {
          val snapshot = moviesRef.get().await()
          return snapshot.children.mapNotNull { child -> snapshotToMovie(child) }
      }

      suspend fun getMovieById(id: String): Movie? {
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

      // Movie object → Firebase-compatible Map
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
  