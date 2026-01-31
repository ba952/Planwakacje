package com.example.wakacje1.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatePasswordUseCaseTest {

    private val useCase = ValidatePasswordUseCase()

    // --- TESTY POZYTYWNE (Hasło poprawne) ---

    @Test
    fun `execute returns Success for strong password`() {
        // Hasło ma: 8 znaków, Dużą, małą, cyfrę i znak specjalny (!)
        val result = useCase.execute("Haslo123!")

        assertTrue("Oczekiwano sukcesu dla poprawnego hasła", result is PasswordValidationResult.Success)
    }

    // --- TESTY NEGATYWNE (Błędy) ---

    @Test
    fun `execute returns Error when password is too short`() {
        val result = useCase.execute("Krot1!")

        assertTrue(result is PasswordValidationResult.Error)
        assertEquals("Hasło musi mieć co najmniej 8 znaków.", (result as PasswordValidationResult.Error).message)
    }

    @Test
    fun `execute returns Error when no digit`() {
        val result = useCase.execute("BezCyfry!")

        assertTrue(result is PasswordValidationResult.Error)
        assertEquals("Hasło musi zawierać cyfrę.", (result as PasswordValidationResult.Error).message)
    }

    @Test
    fun `execute returns Error when no lowercase letter`() {
        val result = useCase.execute("DUZEHASLO1!")

        assertTrue(result is PasswordValidationResult.Error)
        assertEquals("Hasło musi zawierać małą literę.", (result as PasswordValidationResult.Error).message)
    }

    @Test
    fun `execute returns Error when no uppercase letter`() {
        val result = useCase.execute("malehaslo1!")

        assertTrue(result is PasswordValidationResult.Error)
        assertEquals("Hasło musi zawierać wielką literę.", (result as PasswordValidationResult.Error).message)
    }

    @Test
    fun `execute returns Error when no special character`() {
        val result = useCase.execute("BezZnaku123")

        assertTrue(result is PasswordValidationResult.Error)
        assertEquals("Hasło musi zawierać znak specjalny.", (result as PasswordValidationResult.Error).message)
    }
}