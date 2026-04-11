package com.ottapp.moviestream.data.model

  data class User(
      var uid: String = "",
      var email: String = "",
      var displayName: String = "",
      var photoUrl: String = "",
      var subscriptionStatus: String = PLAN_FREE,
      var subscriptionExpiry: Long = 0L
  ) {
      constructor() : this("", "", "", "", PLAN_FREE, 0L)

      val isPremium: Boolean
          get() = subscriptionStatus == PLAN_PREMIUM &&
                  (subscriptionExpiry == 0L || subscriptionExpiry > System.currentTimeMillis())

      companion object {
          const val PLAN_FREE    = "free"
          const val PLAN_PREMIUM = "premium"
      }
  }
  