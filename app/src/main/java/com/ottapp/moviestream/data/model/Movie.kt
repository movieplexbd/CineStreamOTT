package com.ottapp.moviestream.data.model

data class Movie(
    var id: String = "",
    var title: String = "",
    var description: String = "",
    var bannerImageUrl: String = "",
    var detailThumbnailUrl: String = "",
    var videoStreamUrl: String = "",
    var downloadUrl: String = "",
    var category: String = "",
    var imdbRating: Double = 0.0,
    var trending: Boolean = false,
    var testMovie: Boolean = false,
    var year: Int = 0,
    var duration: String = "",
    var actorIds: List<String> = emptyList(),
    var downloads: List<DownloadQuality> = emptyList()
) {
    constructor() : this("", "", "", "", "", "", "", "", 0.0, false, false, 0, "", emptyList(), emptyList())

    companion object {
        const val CATEGORY_BANGLA  = "Bangla Dubbed"
        const val CATEGORY_HINDI   = "Hindi Dubbed"
        const val CATEGORY_ENGLISH = "English"
    }
}

data class DownloadQuality(
    var quality: String = "", // e.g., "360p", "480p", "1080p"
    var url: String = "",
    var size: String = "" // e.g., "500MB"
)
