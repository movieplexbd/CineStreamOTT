package com.ottapp.moviestream.data.model

import com.google.firebase.database.PropertyName

data class Movie(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val bannerImageUrl: String = "",
    val videoStreamUrl: String = "",
    val downloadUrl: String = "",
    val category: String = "",       // "Bangla Dubbed" / "Hindi Dubbed"
    val imdbRating: Float = 0f,
    val trending: Boolean = false,
    val testMovie: Boolean = false,
    val year: Int = 0,
    val duration: String = ""        // e.g. "2h 15m"
) {
    // No-arg constructor required by Firebase
    constructor() : this("", "", "", "", "", "", "", 0f, false, false, 0, "")

    companion object {
        const val CATEGORY_BANGLA = "Bangla Dubbed"
        const val CATEGORY_HINDI = "Hindi Dubbed"
    }
}
