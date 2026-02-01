package com.example.wakacje1.presentation.common

import com.example.wakacje1.R

/**
 * Scentralizowana hierarchia błędów aplikacji (Sealed Class).
 *
 * Cel architektoniczny:
 * Oddzielenie warstwy prezentacji od technicznych wyjątków.
 * ViewModel mapuje Exception -> AppError, dzięki czemu Widok (Compose)
 * otrzymuje gotowy, zlokalizowany komunikat (uiText) i nie musi zajmować się logiką błędów.
 *
 * @property uiText Tekst przeznaczony dla użytkownika (bezpieczny, zlokalizowany).
 * @property technicalMessage Surowy komunikat błędu (dla Logcat/Crashlytics).
 */
sealed class AppError(
    val uiText: UiText,
    val technicalMessage: String? = null
) {

    /**
     * Błąd "odzyskiwalny" lub specyficzny, który został już przetworzony
     * na konkretny komunikat w warstwie UseCase (np. "Nie znaleziono miasta X").
     * Pozwala na elastyczne przekazywanie komunikatów spoza standardowej listy.
     */
    class Recoverable(
        text: UiText,
        tech: String? = null
    ) : AppError(
        uiText = text,
        technicalMessage = tech
    )

    // --- Standardowe błędy infrastrukturalne (mapowane na R.string) ---

    class Network(tech: String? = null) : AppError(
        uiText = UiText.StringResource(R.string.error_network),
        technicalMessage = tech
    )

    class Timeout(tech: String? = null) : AppError(
        uiText = UiText.StringResource(R.string.error_timeout),
        technicalMessage = tech
    )

    class Permission(tech: String? = null) : AppError(
        uiText = UiText.StringResource(R.string.error_permission),
        technicalMessage = tech
    )

    class Auth(tech: String? = null) : AppError(
        uiText = UiText.StringResource(R.string.error_auth),
        technicalMessage = tech
    )

    class NotFound(tech: String? = null) : AppError(
        uiText = UiText.StringResource(R.string.error_not_found),
        technicalMessage = tech
    )

    // Błąd walidacji formularza (dynamiczny tekst, np. "Hasło za krótkie")
    class Validation(message: String, tech: String? = null) : AppError(
        uiText = UiText.DynamicString(message),
        technicalMessage = tech
    )

    class Storage(tech: String? = null) : AppError(
        uiText = UiText.StringResource(R.string.error_storage),
        technicalMessage = tech
    )

    class Unknown(
        fallback: UiText = UiText.StringResource(R.string.error_unknown),
        tech: String? = null
    ) : AppError(
        uiText = fallback,
        technicalMessage = tech
    )
}