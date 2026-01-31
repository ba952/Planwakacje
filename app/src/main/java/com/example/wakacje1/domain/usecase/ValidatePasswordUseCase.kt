package com.example.wakacje1.domain.usecase

class ValidatePasswordUseCase {

    fun execute(password: String): PasswordValidationResult {
        if (password.length < 8) {
            return PasswordValidationResult.Error("Hasło musi mieć co najmniej 8 znaków.")
        }
        if (!password.any { it.isDigit() }) {
            return PasswordValidationResult.Error("Hasło musi zawierać cyfrę.")
        }
        if (!password.any { it.isLowerCase() }) {
            return PasswordValidationResult.Error("Hasło musi zawierać małą literę.")
        }
        if (!password.any { it.isUpperCase() }) {
            return PasswordValidationResult.Error("Hasło musi zawierać wielką literę.")
        }
        // Opcjonalnie znak specjalny
        if (password.none { !it.isLetterOrDigit() }) {
            return PasswordValidationResult.Error("Hasło musi zawierać znak specjalny.")
        }

        return PasswordValidationResult.Success
    }
}

sealed interface PasswordValidationResult {
    object Success : PasswordValidationResult
    data class Error(val message: String) : PasswordValidationResult
}