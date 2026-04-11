package com.ottapp.moviestream.util

object Constants {
    // Firebase DB paths
    const val DB_MOVIES = "movies"
    const val DB_USERS = "users"

    // Intent extras
    const val EXTRA_MOVIE_ID = "extra_movie_id"
    const val EXTRA_MOVIE_TITLE = "extra_movie_title"
    const val EXTRA_VIDEO_URL = "extra_video_url"
    const val EXTRA_BANNER_URL = "extra_banner_url"
    const val EXTRA_IS_LOCAL = "extra_is_local"

    // Shared prefs
    const val PREF_PLAYBACK_POSITION = "pref_playback_position_"

    // Download
    const val DOWNLOAD_NOTIFICATION_ID = 1001
    const val DOWNLOAD_WORK_TAG = "movie_download"

    // Categories
    const val CAT_ALL = "All"
    const val CAT_BANGLA = "Bangla Dubbed"
    const val CAT_HINDI = "Hindi Dubbed"
    const val CAT_TRENDING = "Trending"

    // Subscription
    const val SUB_FREE = "free"
    const val SUB_PREMIUM = "premium"

    // Max test movies allowed
    const val MAX_TEST_MOVIES = 2

    // Web client ID from Firebase Console (OAuth 2.0 Web Client)
    const val WEB_CLIENT_ID = "1080659811750-iedo8km8k81nv4npm71dcec7abn9kuvn.apps.googleusercontent.com"
}
