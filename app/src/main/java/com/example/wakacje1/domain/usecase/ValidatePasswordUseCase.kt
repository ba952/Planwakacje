package com.example.wakacje1.domain.usecase

/**
 * UseCase walidujący siłę hasła zgodnie z regułami bezpieczeństwa.
 * Czysta logika biznesowa, niezależna od frameworka UI.
 */
class ValidatePasswordUseCase {

    fun execute(password: String): PasswordValidationResult {
        // Strategia "Fail-fast": przerywamy walidację przy pierwszym niespełnionym warunku.

        if (password.length < 8) {
            return PasswordValidationResult.Error("Hasło musi mieć co najmniej 8 znaków.")
        }

        // Predykaty sprawdzające złożoność znakową (cyfry, wielkość liter)
        if (!password.any { it.isDigit() }) {
            return PasswordValidationResult.Error("Hasło musi zawierać cyfrę.")
        }
        if (!password.any { it.isLowerCase() }) {
            return PasswordValidationResult.Error("Hasło musi zawierać małą literę.")
        }
        if (!password.any { it.isUpperCase() }) {
            return PasswordValidationResult.Error("Hasło musi zawierać wielką literę.")
        }

        // Sprawdzenie obecności znaków specjalnych (wszystko co nie jest literą ani cyfrą)
        if (password.none { !it.isLetterOrDigit() }) {
            return PasswordValidationResult.Error("Hasło musi zawierać znak specjalny.")
        }

        return PasswordValidationResult.Success
    }
}

/**
 * Wynik walidacji jako Sealed Interface (ADT - Algebraic Data Type).
 * Wymusza na warstwie UI (ViewModel/View) obsłużenie obu stanów (Success/Error).
 */
sealed interface PasswordValidationResult {
    object Success : PasswordValidationResult
    data class Error(val message: String) : PasswordValidationResult
}