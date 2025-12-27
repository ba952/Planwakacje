package com.example.wakacje1.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser


class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    var user: FirebaseUser? by mutableStateOf(auth.currentUser)
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

    fun signIn(email: String, password: String, onSuccess: () -> Unit) {
        clearMessages()
        val e = email.trim()
        if (e.isBlank() || password.isBlank()) {
            error = "Podaj email i hasło."
            return
        }

        loading = true
        auth.signInWithEmailAndPassword(e, password)
            .addOnSuccessListener {
                user = auth.currentUser
                loading = false
                onSuccess()
            }
            .addOnFailureListener { ex ->
                loading = false
                error = ex.message ?: "Nie udało się zalogować."
            }
    }

    fun register(email: String, password: String, onSuccess: () -> Unit) {
        clearMessages()
        val e = email.trim()
        if (e.isBlank() || password.isBlank()) {
            error = "Podaj email i hasło."
            return
        }
        if (password.length < 6) {
            error = "Hasło musi mieć co najmniej 6 znaków."
            return
        }

        loading = true
        auth.createUserWithEmailAndPassword(e, password)
            .addOnSuccessListener {
                user = auth.currentUser
                loading = false
                onSuccess()
            }
            .addOnFailureListener { ex ->
                loading = false
                error = ex.message ?: "Nie udało się zarejestrować."
            }
    }

    fun sendPasswordReset(email: String) {
        clearMessages()
        val e = email.trim()
        if (e.isBlank()) {
            error = "Podaj email."
            return
        }

        loading = true
        auth.sendPasswordResetEmail(e)
            .addOnSuccessListener {
                loading = false
                info = "Wysłano link do resetu hasła na: $e"
            }
            .addOnFailureListener { ex ->
                loading = false
                error = ex.message ?: "Nie udało się wysłać resetu hasła."
            }
    }

    fun signOut() {
        auth.signOut()
        user = null
    }
}