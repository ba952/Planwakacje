package com.example.wakacje1.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

/**
 * Repozytorium obsługujące uwierzytelnianie użytkownika (Firebase Auth).
 * Pełni rolę warstwy abstrakcji nad zewnętrznym SDK, udostępniając
 * metody oparte o Coroutines i typ Result<T>.
 */
class AuthRepository(private val firebaseAuth: FirebaseAuth) {

    /**
     * Zwraca aktualnie zalogowanego użytkownika lub null, jeśli brak sesji.
     */
    val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    /**
     * Logowanie przy użyciu adresu email i hasła.
     * Zamienia asynchroniczny Task Firebase na funkcję zawieszającą (suspend).
     */
    suspend fun signIn(email: String, pass: String): Result<Unit> {
        return try {
            // await() czeka na zakończenie Taska bez blokowania wątku
            firebaseAuth.signInWithEmailAndPassword(email, pass).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rejestracja nowego konta.
     * Po udanym utworzeniu użytkownik jest automatycznie logowany przez SDK.
     */
    suspend fun register(email: String, pass: String): Result<Unit> {
        return try {
            firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Wysyła wiadomość email z linkiem do resetowania hasła.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Wylogowuje użytkownika i czyści sesję lokalną.
     */
    fun signOut() {
        firebaseAuth.signOut()
    }
}