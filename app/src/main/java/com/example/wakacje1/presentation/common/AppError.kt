package com.example.wakacje1.presentation.common

import com.example.wakacje1.R

sealed class AppError(
    val uiText: UiText, // To pole przechowuje tekst dla UI
    val technicalMessage: String? = null
) {

    // --- DODANA KLASA ---
    // Służy do przekazywania błędów, które zostały już zmapowane na konkretny UiText
    // (np. w ErrorMapperze dla pogody).
    class Recoverable(
        text: UiText,
        tech: String? = null
    ) : AppError(
        uiText = text,
        technicalMessage = tech
    )
    // --------------------

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