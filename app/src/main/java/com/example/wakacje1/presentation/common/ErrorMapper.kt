package com.example.wakacje1.presentation.common

import com.example.wakacje1.R
import com.example.wakacje1.data.remote.WeatherException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Narzędzie mapujące surowe wyjątki systemowe (Throwable) na stany błędów UI (AppError).
 * Centralizuje logikę obsługi błędów, zapobiegając powielaniu bloków try-catch w ViewModelach.
 */
object ErrorMapper {

    fun map(t: Throwable, fallback: String = "Coś poszło nie tak."): AppError {
        val tech = t.message

        return when (t) {
            // --- WYJĄTKI DOMENOWE (Weather) ---
            // Precyzyjne mapowanie błędów logicznych z warstwy Data na konkretne komunikaty UI.
            is WeatherException.NetworkError -> AppError.Recoverable(
                UiText.StringResource(R.string.error_weather_network)
            )
            is WeatherException.CityNotFound -> AppError.Recoverable(
                UiText.StringResource(R.string.error_weather_city_not_found)
            )
            is WeatherException.InvalidApiKey -> AppError.Recoverable(
                UiText.StringResource(R.string.error_weather_api_key)
            )
            is WeatherException.ApiError -> AppError.Recoverable(
                UiText.StringResource(R.string.error_weather_general, t.code)
            )
            is WeatherException.Unknown -> AppError.Unknown(
                fallback = UiText.StringResource(R.string.error_unknown),
                tech = t.cause?.message
            )

            // --- WYJĄTKI INFRASTRUKTURY (Firebase) ---
            // Mapowanie kodów błędów SDK Firebase na ogólne typy błędów aplikacji.
            is FirebaseFirestoreException -> when (t.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.Permission(tech)
                FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound(tech)
                FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.Network(tech)
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Timeout(tech)
                else -> AppError.Unknown(UiText.DynamicString(fallback), tech)
            }

            is FirebaseAuthException -> AppError.Auth(tech)

            // --- STANDARDOWE WYJĄTKI SYSTEMOWE (Java/Kotlin) ---
            is TimeoutCancellationException,
            is SocketTimeoutException -> AppError.Timeout(tech)

            is UnknownHostException -> AppError.Network(tech)

            is SecurityException -> AppError.Permission(tech)

            is IOException -> AppError.Storage(tech)

            // Fallback: Każdy inny, nieprzewidziany błąd (np. NullPointerException)
            else -> AppError.Unknown(UiText.DynamicString(fallback), tech)
        }
    }

    // Helper skracający wywołanie w ViewModelu, gdy potrzebujemy tylko tekstu
    fun mapToUiText(t: Throwable, fallback: String = "Coś poszło nie tak."): UiText =
        map(t, fallback).uiText
}