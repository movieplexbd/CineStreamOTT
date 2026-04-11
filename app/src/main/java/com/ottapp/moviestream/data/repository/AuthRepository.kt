package com.ottapp.moviestream.data.repository

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.ottapp.moviestream.data.model.User
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    // ── Google Sign-In client ──────────────────────────────────────────────
    fun getGoogleSignInClient(webClientId: String): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    // ── Current user ────────────────────────────────────────────────────────
    val currentFirebaseUser: FirebaseUser? get() = auth.currentUser
    val isLoggedIn: Boolean get() = auth.currentUser != null

    // ── Sign-in with Google credential ──────────────────────────────────────
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val firebaseUser = result.user!!

            // Save / update user in Realtime DB
            saveUserToDatabase(firebaseUser)

            Result.success(firebaseUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Save user profile to Firebase DB ────────────────────────────────────
    private suspend fun saveUserToDatabase(firebaseUser: FirebaseUser) {
        val uid = firebaseUser.uid
        val userRef = db.child("users").child(uid)

        // Only write email/name; don't overwrite subscription fields
        val snapshot = userRef.get().await()
        if (!snapshot.exists()) {
            val newUser = User(
                uid = uid,
                email = firebaseUser.email ?: "",
                displayName = firebaseUser.displayName ?: "",
                photoUrl = firebaseUser.photoUrl?.toString() ?: "",
                subscriptionStatus = User.PLAN_FREE,
                subscriptionExpiry = 0L
            )
            userRef.setValue(newUser).await()
        } else {
            // Update display info only
            userRef.child("email").setValue(firebaseUser.email).await()
            userRef.child("displayName").setValue(firebaseUser.displayName).await()
            userRef.child("photoUrl").setValue(firebaseUser.photoUrl?.toString()).await()
        }
    }

    // ── Sign out ─────────────────────────────────────────────────────────────
    suspend fun signOut(googleSignInClient: GoogleSignInClient) {
        auth.signOut()
        googleSignInClient.signOut().await()
    }
}
