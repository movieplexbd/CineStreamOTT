package com.ottapp.moviestream.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.ottapp.moviestream.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    val currentUid: String? get() = auth.currentUser?.uid

    // ── Realtime user flow ───────────────────────────────────────────────────
    fun getCurrentUserFlow(): Flow<User?> = callbackFlow {
        val uid = currentUid ?: run {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val user = snapshot.getValue(User::class.java)
                trySend(user)
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        val ref = db.child("users").child(uid)
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Get user once ─────────────────────────────────────────────────────────
    suspend fun getCurrentUser(): User? {
        val uid = currentUid ?: return null
        val snapshot = db.child("users").child(uid).get().await()
        return snapshot.getValue(User::class.java)
    }

    // ── Check if movie is accessible ──────────────────────────────────────────
    suspend fun canAccessMovie(movieId: String, isTestMovie: Boolean): Boolean {
        val user = getCurrentUser() ?: return false
        if (user.isPremium) return true        // premium = access all
        if (isTestMovie) return true           // test movie = free access
        return false
    }
}
