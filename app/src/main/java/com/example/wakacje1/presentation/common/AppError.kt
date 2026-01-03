package com.example.wakacje1.presentation.common

sealed class AppError(
    val userMessage: String,
    val technicalMessage: String? = null
) {
    class Network(tech: String? = null) : AppError(
        userMessage = "Brak połączenia z internetem lub problem z serwerem.",
        technicalMessage = tech
    )

    class Timeout(tech: String? = null) : AppError(
        userMessage = "Operacja trwa zbyt długo. Spróbuj ponownie.",
        technicalMessage = tech
    )

    class Permission(tech: String? = null) : AppError(
        userMessage = "Brak uprawnień do wykonania tej operacji.",
        technicalMessage = tech
    )

    class Auth(tech: String? = null) : AppError(
        userMessage = "Problem z logowaniem. Zaloguj się ponownie.",
        technicalMessage = tech
    )

    class NotFound(tech: String? = null) : AppError(
        userMessage = "Nie znaleziono danych.",
        technicalMessage = tech
    )

    class Validation(message: String, tech: String? = null) : AppError(
        userMessage = message,
        technicalMessage = tech
    )

    class Storage(tech: String? = null) : AppError(
        userMessage = "Błąd zapisu/odczytu danych na urządzeniu.",
        technicalMessage = tech
    )

    class Unknown(fallback: String = "Coś poszło nie tak.", tech: String? = null) : AppError(
        userMessage = fallback,
        technicalMessage = tech
    )
}
