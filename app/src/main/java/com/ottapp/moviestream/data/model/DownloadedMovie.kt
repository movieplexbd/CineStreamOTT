package com.ottapp.moviestream.data.model

data class DownloadedMovie(
    val movieId: String = "",
    val title: String = "",
    val bannerImageUrl: String = "",
    val localFilePath: String = "",
    val fileSize: Long = 0L,       // bytes
    val downloadedAt: Long = 0L,   // Unix timestamp
    val imdbRating: Float = 0f,
    val category: String = "",
    val duration: String = ""
)
