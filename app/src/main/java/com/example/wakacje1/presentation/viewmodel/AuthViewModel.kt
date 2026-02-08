package com.example.wakacje1.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.R
import com.example.wakacje1.data.remote.AuthRepository
import com.example.wakacje1.domain.usecase.PasswordValidationResult
import com.example.wakacje1.domain.usecase.ValidatePasswordUseCase
import com.example.wakacje1.presentation.common.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val uid: String? = null,
    val loading: Boolean = false,
    val error: UiText? = null,
    val info: UiText? = null
)

sealed class AuthEvent {
    data object NavigateAfterAuth : AuthEvent()
    data object NavigateAfterRegister : AuthEvent()
}

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val validatePasswordUseCase: ValidatePasswordUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(
        AuthUiState(
            uid = authRepository.currentUser?.takeIf { it.isEmailVerified }?.uid
        )
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    fun clearMessages() {
        _state.update { it.copy(error = null, info = null) }
    }

    fun signIn(email: String, pass: String) {
        clearMessages()

        if (email.isBlank() || pass.isBlank()) {
            _state.update { it.copy(error = UiText.StringResource(R.string.auth_error_empty_credentials)) }
            return
        }

        _state.update { it.copy(loading = true) }

        viewModelScope.launch {
            val result = authRepository.signIn(email, pass)

            if (result.isSuccess) {
                val user = authRepository.currentUser

                if (user != null && user.isEmailVerified) {
                    _state.update { it.copy(uid = user.uid, loading = false) }
                    _events.emit(AuthEvent.NavigateAfterAuth)
                } else {
                    authRepository.signOut()
                    _state.update {
                        it.copy(
                            uid = null,
                            loading = false,
                            error = UiText.StringResource(R.string.auth_error_email_not_verified)
                        )
                    }
                }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        error = mapAuthFailureToUiText(result.exceptionOrNull())
                    )
                }
            }
        }
    }

    fun register(email: String, pass: String) {
        clearMessages()

        val validation = validatePasswordUseCase.execute(pass)
        if (validation is PasswordValidationResult.Error) {
            // To jest komunikat dynamiczny z walidatora (OK, bo nie jest stałym tekstem UI)
            _state.update { it.copy(error = UiText.DynamicString(validation.message)) }
            return
        }

        _state.update { it.copy(loading = true) }

        viewModelScope.launch {
            val result = authRepository.register(email, pass)

            if (result.isSuccess) {
                authRepository.currentUser?.sendEmailVerification()
                authRepository.signOut()

                _state.update {
                    it.copy(
                        uid = null,
                        loading = false,
                        info = UiText.StringResource(R.string.auth_info_verify_email_sent)
                    )
                }
                _events.emit(AuthEvent.NavigateAfterRegister)
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        error = mapAuthFailureToUiText(result.exceptionOrNull(), isRegister = true)
                    )
                }
            }
        }
    }

    fun sendPasswordReset(email: String) {
        clearMessages()

        if (email.isBlank()) {
            _state.update { it.copy(error = UiText.StringResource(R.string.auth_error_email_empty)) }
            return
        }

        _state.update { it.copy(loading = true) }

        viewModelScope.launch {
            val result = authRepository.sendPasswordReset(email)

            if (result.isSuccess) {
                _state.update {
                    it.copy(
                        loading = false,
                        info = UiText.StringResource(R.string.auth_info_reset_sent)
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        loading = false,
                        error = UiText.StringResource(R.string.auth_error_reset_generic)
                    )
                }
            }
        }
    }

    fun signOut() {
        authRepository.signOut()
        _state.value = AuthUiState(uid = null)
    }

    private fun mapAuthFailureToUiText(t: Throwable?, isRegister: Boolean = false): UiText {
        return if (isRegister) {
            UiText.StringResource(R.string.auth_error_register_generic)
        } else {
            UiText.StringResource(R.string.auth_error_login_generic)
        }
    }
}
