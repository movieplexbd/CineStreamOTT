package com.ottapp.moviestream.data.model

data class Movie(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val bannerImageUrl: String = "",
    val videoStreamUrl: String = "",
    val downloadUrl: String = "",
    val category: String = "",
    val imdbRating: Double = 0.0,
    val trending: Boolean = false,
    val testMovie: Boolean = false,
    val year: Int = 0,
    val duration: String = ""
) {
    constructor() : this("", "", "", "", "", "", "", 0.0, false, false, 0, "")

    companion object {
        const val CATEGORY_BANGLA = "Bangla Dubbed"
        const val CATEGORY_HINDI = "Hindi Dubbed"
    }
}
