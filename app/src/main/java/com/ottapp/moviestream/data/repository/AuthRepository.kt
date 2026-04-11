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
      // সঠিক Firebase DB URL (MovieRepository/UserRepository-এর মতো একই)
      private val db = FirebaseDatabase.getInstance("https://movies-bee24-default-rtdb.firebaseio.com").reference

      fun getGoogleSignInClient(webClientId: String): GoogleSignInClient {
          val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
              .requestIdToken(webClientId)
              .requestEmail()
              .build()
          return GoogleSignIn.getClient(context, gso)
      }

      val currentFirebaseUser: FirebaseUser? get() = auth.currentUser
      val isLoggedIn: Boolean get() = auth.currentUser != null

      suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
          return try {
              val credential = GoogleAuthProvider.getCredential(idToken, null)
              val result = auth.signInWithCredential(credential).await()
              val firebaseUser = result.user!!
              saveUserToDatabase(firebaseUser)
              Result.success(firebaseUser)
          } catch (e: Exception) {
              Result.failure(e)
          }
      }

      // BUG FIX: signInWithEmail-এও user save করা হচ্ছে
      suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
          return try {
              val result = auth.signInWithEmailAndPassword(email, password).await()
              val firebaseUser = result.user!!
              saveUserToDatabase(firebaseUser) // user যদি DB-তে না থাকে, এখন সেভ হবে
              Result.success(firebaseUser)
          } catch (e: Exception) {
              Result.failure(e)
          }
      }

      suspend fun signUpWithEmail(email: String, password: String, displayName: String): Result<FirebaseUser> {
          return try {
              val result = auth.createUserWithEmailAndPassword(email, password).await()
              val firebaseUser = result.user!!
              saveUserToDatabase(firebaseUser, displayName)
              Result.success(firebaseUser)
          } catch (e: Exception) {
              Result.failure(e)
          }
      }

      private suspend fun saveUserToDatabase(firebaseUser: FirebaseUser, overrideName: String? = null) {
          val uid = firebaseUser.uid
          val userRef = db.child("users").child(uid)
          val snapshot = userRef.get().await()
          if (!snapshot.exists()) {
              val newUser = User(
                  uid               = uid,
                  email             = firebaseUser.email ?: "",
                  displayName       = overrideName ?: firebaseUser.displayName ?: "",
                  photoUrl          = firebaseUser.photoUrl?.toString() ?: "",
                  subscriptionStatus = User.PLAN_FREE,
                  subscriptionExpiry = 0L
              )
              userRef.setValue(newUser).await()
          } else {
              // ইমেইল ও নাম আপডেট করো
              val updates = mutableMapOf<String, Any>()
              firebaseUser.email?.let { updates["email"] = it }
              if (overrideName != null) updates["displayName"] = overrideName
              if (updates.isNotEmpty()) userRef.updateChildren(updates).await()
          }
      }

      suspend fun signOut(googleSignInClient: GoogleSignInClient) {
          auth.signOut()
          googleSignInClient.signOut().await()
      }
  }
  