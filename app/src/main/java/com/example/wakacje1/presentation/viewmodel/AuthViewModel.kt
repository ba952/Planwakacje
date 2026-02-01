package com.example.wakacje1.presentation.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.R
import com.example.wakacje1.data.remote.AuthRepository
import com.example.wakacje1.domain.usecase.PasswordValidationResult
import com.example.wakacje1.domain.usecase.ValidatePasswordUseCase
import com.example.wakacje1.presentation.common.UiText
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

/**
 * ViewModel odpowiedzialny za procesy uwierzytelniania (logowanie, rejestracja, reset hasła).
 * Wykorzystuje [AuthRepository] do komunikacji z Firebase oraz [ValidatePasswordUseCase] do walidacji danych.
 */
class AuthViewModel(
    private val authRepository: AuthRepository,
    private val validatePasswordUseCase: ValidatePasswordUseCase // <--- Wstrzykujemy walidator
) : ViewModel() {

    // Aktualnie zalogowany użytkownik pobrany z repozytorium
    var user: FirebaseUser? by mutableStateOf(authRepository.currentUser)
        private set

    // Stan ładowania używany do wyświetlania progress barów w UI
    var loading by mutableStateOf(false)
        private set

    // Komunikat błędu sformatowany jako UiText dla obsługi zasobów stringów
    var error: UiText? by mutableStateOf(null)
        private set

    // Komunikat informacyjny (np. o wysłaniu maila resetującego)
    var info: UiText? by mutableStateOf(null)
        private set

    // Czyści komunikaty błędów i informacji przed nową akcją
    fun clearMessages() {
        error = null
        info = null
    }

    /**
     * Logowanie użytkownika za pomocą emaila i hasła.
     */
    fun signIn(email: String, pass: String, onSuccess: () -> Unit) {
        clearMessages()

        // Walidacja pustych pól przed próbą połączenia z serwerem
        if (email.isBlank() || pass.isBlank()) {
            error = UiText.StringResource(R.string.auth_error_empty_credentials)
            return
        }

        loading = true
        viewModelScope.launch {
            val result = authRepository.signIn(email, pass)
            loading = false
            if (result.isSuccess) {
                user = authRepository.currentUser
                onSuccess()
            } else {
                // Obsługa błędu: dynamiczny komunikat z Firebase lub ogólny z zasobów
                val msg = result.exceptionOrNull()?.message
                error = if (msg != null) UiText.DynamicString(msg)
                else UiText.StringResource(R.string.auth_error_login_generic)
            }
        }
    }

    /**
     * Rejestracja nowego konta z wstępną walidacją siły hasła.
     */
    fun register(email: String, pass: String, onSuccess: () -> Unit) {
        clearMessages()

        // 1. Walidacja hasła za pomocą UseCase przed wywołaniem API
        val validation = validatePasswordUseCase.execute(pass)
        if (validation is PasswordValidationResult.Error) {
            error = UiText.DynamicString(validation.message)
            return
        }

        // 2. Próba rejestracji w Firebase
        loading = true
        viewModelScope.launch {
            val result = authRepository.register(email, pass)
            loading = false
            if (result.isSuccess) {
                user = authRepository.currentUser
                onSuccess()
            } else {
                val msg = result.exceptionOrNull()?.message
                error = if (msg != null) UiText.DynamicString(msg)
                else UiText.StringResource(R.string.auth_error_register_generic)
            }
        }
    }

    /**
     * Wysyła link do resetowania hasła na podany adres email.
     */
    fun sendPasswordReset(email: String) {
        clearMessages()
        if (email.isBlank()) {
            error = UiText.StringResource(R.string.auth_error_email_empty)
            return
        }

        loading = true
        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)
            loading = false
            if (result.isSuccess) {
                info = UiText.StringResource(R.string.auth_info_reset_sent)
            } else {
                val msg = result.exceptionOrNull()?.message
                error = if (msg != null) UiText.DynamicString(msg)
                else UiText.StringResource(R.string.auth_error_reset_generic)
            }
        }
    }

    /**
     * Wylogowanie użytkownika i wyczyszczenie lokalnego stanu.
     */
    fun signOut() {
        authRepository.signOut()
        user = null
    }
}