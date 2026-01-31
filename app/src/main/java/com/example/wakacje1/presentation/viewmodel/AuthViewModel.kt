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

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val validatePasswordUseCase: ValidatePasswordUseCase // <--- Wstrzykujemy walidator
) : ViewModel() {

    var user: FirebaseUser? by mutableStateOf(authRepository.currentUser)
        private set

    var loading by mutableStateOf(false)
        private set

    // Zmiana: Teraz error to UiText, a nie String
    var error: UiText? by mutableStateOf(null)
        private set

    var info: UiText? by mutableStateOf(null)
        private set

    fun clearMessages() {
        error = null
        info = null
    }

    fun signIn(email: String, pass: String, onSuccess: () -> Unit) {
        clearMessages()
        if (email.isBlank() || pass.isBlank()) {
            // Używamy zasobu stringa
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
                // Błąd z Firebase jest dynamiczny, błąd ogólny z zasobów
                val msg = result.exceptionOrNull()?.message
                error = if (msg != null) UiText.DynamicString(msg)
                else UiText.StringResource(R.string.auth_error_login_generic)
            }
        }
    }

    fun register(email: String, pass: String, onSuccess: () -> Unit) {
        clearMessages()

        // 1. Używamy Twojego UseCase do walidacji hasła
        val validation = validatePasswordUseCase.execute(pass)
        if (validation is PasswordValidationResult.Error) {
            error = UiText.DynamicString(validation.message)
            return
        }

        // 2. Jeśli hasło OK, próbujemy rejestracji
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

    fun signOut() {
        authRepository.signOut()
        user = null
    }
}