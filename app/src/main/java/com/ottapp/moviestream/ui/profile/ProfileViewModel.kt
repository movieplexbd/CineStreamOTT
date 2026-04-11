package com.ottapp.moviestream.ui.profile

import android.app.Application
import androidx.lifecycle.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.ottapp.moviestream.data.model.User
import com.ottapp.moviestream.data.repository.AuthRepository
import com.ottapp.moviestream.data.repository.DownloadRepository
import com.ottapp.moviestream.data.repository.UserRepository
import com.ottapp.moviestream.util.Constants
import com.ottapp.moviestream.util.toReadableSize
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ProfileViewModel(app: Application) : AndroidViewModel(app) {

    private val authRepo = AuthRepository(app)
    private val userRepo = UserRepository()
    private val dlRepo   = DownloadRepository(app)

    private val _user        = MutableLiveData<User?>()
    val user: LiveData<User?> = _user

    private val _storageUsed        = MutableLiveData<String>()
    val storageUsed: LiveData<String> = _storageUsed

    private val _signedOut        = MutableLiveData(false)
    val signedOut: LiveData<Boolean> = _signedOut

    init { observeUser(); loadStorage() }

    private fun observeUser() = viewModelScope.launch {
        userRepo.getCurrentUserFlow().catch { emit(null) }.collect { _user.value = it }
    }

    private fun loadStorage() {
        _storageUsed.value = dlRepo.getTotalStorageUsed().toReadableSize()
    }

    fun signOut() = viewModelScope.launch {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(Constants.WEB_CLIENT_ID).requestEmail().build()
        val client = GoogleSignIn.getClient(getApplication<Application>(), gso)
        authRepo.signOut(client)
        _signedOut.value = true
    }
}
