package com.ottapp.moviestream.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.ottapp.moviestream.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

class UserRepository {

    companion object {
        private const val TAG = "UserRepository"
        private const val DB_URL = "https://movies-bee24-default-rtdb.firebaseio.com"

        @Volatile
        private var dbInstance: FirebaseDatabase? = null

        private fun getDatabase(): FirebaseDatabase {
            return dbInstance ?: synchronized(this) {
                dbInstance ?: FirebaseDatabase.getInstance(DB_URL).also {
                    dbInstance = it
                }
            }
        }
    }

    private val auth: FirebaseAuth by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth init error: ${e.message}")
            throw e
        }
    }

    private val db by lazy {
        try {
            getDatabase().reference
        } catch (e: Exception) {
            Log.e(TAG, "Database ref error: ${e.message}")
            null
        }
    }

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
                subscriptionExpiry = try {
                    data["subscriptionExpiry"]?.toString()?.toLongOrNull()
                        ?: (data["subscriptionExpiry"] as? Long) ?: 0L
                } catch (e: Exception) { 0L }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse user error: ${e.message}")
            null
        }
    }

    suspend fun getCurrentUser(): User? {
        val uid = try { auth.currentUser?.uid } catch (e: Exception) {
            Log.e(TAG, "Get current user uid error: ${e.message}")
            null
        } ?: return null
        val dbRef = db ?: return null
        return try {
            val snapshot = dbRef.child("users").child(uid).get().await()
            snapshotToUser(snapshot)
        } catch (e: Exception) {
            Log.e(TAG, "getCurrentUser error: ${e.message}", e)
            null
        }
    }

    fun getCurrentUserFlow(): Flow<User?> {
        val uid = try { auth.currentUser?.uid } catch (e: Exception) {
            Log.e(TAG, "Get uid for flow error: ${e.message}")
            null
        }
        if (uid == null) {
            return flowOf(null)
        }

        val dbRef = db ?: return flowOf(null)

        return callbackFlow {
            val userRef = dbRef.child("users").child(uid)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        trySend(snapshotToUser(snapshot))
                    } catch (e: Exception) {
                        Log.e(TAG, "User flow send error: ${e.message}")
                        trySend(null)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "User flow cancelled: ${error.message}")
                    trySend(null)
                }
            }
            userRef.addValueEventListener(listener)
            awaitClose {
                try {
                    userRef.removeEventListener(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Remove listener error: ${e.message}")
                }
            }
        }.catch { e ->
            Log.e(TAG, "User flow exception: ${e.message}")
            emit(null)
        }
    }

    suspend fun updateSubscription(uid: String, status: String, expiry: Long) {
        val dbRef = db ?: throw Exception("Database not available")
        dbRef.child("users").child(uid).updateChildren(
            mapOf("subscriptionStatus" to status, "subscriptionExpiry" to expiry)
        ).await()
    }
}
