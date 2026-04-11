package com.ottapp.moviestream.data.model

data class User(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val subscriptionStatus: String = PLAN_FREE,  // "free" or "premium"
    val subscriptionExpiry: Long = 0L             // Unix timestamp millis
) {
    constructor() : this("", "", "", "", PLAN_FREE, 0L)

    val isPremium: Boolean
        get() = subscriptionStatus == PLAN_PREMIUM &&
                (subscriptionExpiry == 0L || subscriptionExpiry > System.currentTimeMillis())

    companion object {
        const val PLAN_FREE = "free"
        const val PLAN_PREMIUM = "premium"
    }
}
