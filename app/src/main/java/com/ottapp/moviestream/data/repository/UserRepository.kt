package com.ottapp.moviestream.data.repository

  import com.google.firebase.auth.FirebaseAuth
  import com.google.firebase.database.DataSnapshot
  import com.google.firebase.database.DatabaseError
  import com.google.firebase.database.FirebaseDatabase
  import com.google.firebase.database.ValueEventListener
  import com.ottapp.moviestream.data.model.User
  import kotlinx.coroutines.channels.awaitClose
  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.callbackFlow
  import kotlinx.coroutines.tasks.await

  class UserRepository {

      private val auth = FirebaseAuth.getInstance()
      private val db = FirebaseDatabase.getInstance("https://movies-bee24-default-rtdb.firebaseio.com").reference

      @Suppress("UNCHECKED_CAST")
      private fun snapshotToUser(snapshot: DataSnapshot): User? {
          return try {
              val data = snapshot.value as? Map<*, *> ?: return null
              User(
                  uid                = data["uid"]?.toString() ?: snapshot.key ?: "",
                  email              = data["email"]?.toString() ?: "",
                  displayName        = data["displayName"]?.toString() ?: "",
                  photoUrl           = data["photoUrl"]?.toString() ?: "",
                  subscriptionStatus = data["subscriptionStatus"]?.toString() ?: User.PLAN_FREE,
                  subscriptionExpiry = data["subscriptionExpiry"]?.toString()?.toLongOrNull()
                      ?: (data["subscriptionExpiry"] as? Long) ?: 0L
              )
          } catch (e: Exception) {
              null
          }
      }

      fun getCurrentUserFlow(): Flow<User?> = callbackFlow {
          val uid = auth.currentUser?.uid ?: run {
              trySend(null)
              close()
              return@callbackFlow
          }

          val userRef = db.child("users").child(uid)
          val listener = object : ValueEventListener {
              override fun onDataChange(snapshot: DataSnapshot) {
                  trySend(snapshotToUser(snapshot))
              }
              override fun onCancelled(error: DatabaseError) {
                  trySend(null)
              }
          }
          userRef.addValueEventListener(listener)
          awaitClose { userRef.removeEventListener(listener) }
      }

      suspend fun updateSubscription(uid: String, status: String, expiry: Long) {
          db.child("users").child(uid).updateChildren(
              mapOf("subscriptionStatus" to status, "subscriptionExpiry" to expiry)
          ).await()
      }
  }
  