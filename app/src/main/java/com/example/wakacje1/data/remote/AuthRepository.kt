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

    suspend fun signIn(email: String, pass: String): Result<Unit> {
        return try {
            firebaseAuth.signInWithEmailAndPassword(email, pass).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, pass: String): Result<Unit> {
        return try {
            firebaseAuth.createUserWithEmailAndPassword(email, pass).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Odświeża dane bieżącego użytkownika (ważne dla isEmailVerified).
     */
    suspend fun reloadCurrentUser(): Result<Unit> {
        val u = currentUser ?: return Result.failure(IllegalStateException("No user"))
        return try {
            u.reload().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isEmailVerified(): Boolean = currentUser?.isEmailVerified == true

    /**
     * Wysyła email weryfikacyjny na adres aktualnego użytkownika.
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        val u = currentUser ?: return Result.failure(IllegalStateException("No user"))
        return try {
            u.sendEmailVerification().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            firebaseAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        firebaseAuth.signOut()
    }
}
