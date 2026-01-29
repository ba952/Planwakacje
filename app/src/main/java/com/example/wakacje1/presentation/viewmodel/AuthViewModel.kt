package com.example.wakacje1.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Do uruchamiania korutyn (jeśli repo jest suspend)
import com.example.wakacje1.data.remote.AuthRepository
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

// ZMIANA: Zwykły ViewModel + wstrzyknięte Repozytorium
class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    // Stan UI (można też użyć StateFlow, ale Compose State jest OK dla prostych przypadków)
    var user: FirebaseUser? by mutableStateOf(authRepository.currentUser)
        private set

    var loading by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    var info: String? by mutableStateOf(null)
        private set

    fun clearMessages() {
        error = null
        info = null
    }

    fun signIn(email: String, pass: String, onSuccess: () -> Unit) {
        clearMessages()
        if (email.isBlank() || pass.isBlank()) {
            error = "Podaj email i hasło."
            return
        }

        loading = true
        // ZMIANA: Wywołanie repozytorium (asynchroniczne)
        viewModelScope.launch {
            val result = authRepository.signIn(email, pass)
            loading = false
            if (result.isSuccess) {
                user = authRepository.currentUser
                onSuccess()
            } else {
                error = result.exceptionOrNull()?.message ?: "Błąd logowania"
            }
        }
    }

    fun register(email: String, pass: String, onSuccess: () -> Unit) {
        clearMessages()
        if (pass.length < 6) {
            error = "Hasło za krótkie (min 6 znaków)."
            return
        }

        loading = true
        viewModelScope.launch {
            val result = authRepository.register(email, pass)
            loading = false
            if (result.isSuccess) {
                user = authRepository.currentUser
                onSuccess()
            } else {
                error = result.exceptionOrNull()?.message ?: "Błąd rejestracji"
            }
        }
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) { error = "Podaj email."; return }

        loading = true
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            loading = false
            if (result.isSuccess) {
                info = "Wysłano link resetujący."
            } else {
                error = result.exceptionOrNull()?.message ?: "Błąd wysyłania."
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        user = null
    }
}