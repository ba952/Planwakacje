package com.example.wakacje1.presentation.common

import com.example.wakacje1.R
import com.example.wakacje1.data.remote.WeatherException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object ErrorMapper {

    fun map(t: Throwable, fallback: String = "Coś poszło nie tak."): AppError {
        val tech = t.message

        return when (t) {
            // --- NOWA OBSŁUGA POGODY (Z zasobów strings.xml) ---
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

            // --- DOTYCHCZASOWA OBSŁUGA FIREBASE / SYSTEM ---
            is FirebaseFirestoreException -> when (t.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.Permission(tech)
                FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound(tech)
                FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.Network(tech)
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Timeout(tech)
                else -> AppError.Unknown(UiText.DynamicString(fallback), tech)
            }

            is FirebaseAuthException -> AppError.Auth(tech)

            is TimeoutCancellationException,
            is SocketTimeoutException -> AppError.Timeout(tech)

            is UnknownHostException -> AppError.Network(tech)

            is SecurityException -> AppError.Permission(tech)

            is IOException -> AppError.Storage(tech)

            else -> AppError.Unknown(UiText.DynamicString(fallback), tech)
        }
    }

    fun mapToUiText(t: Throwable, fallback: String = "Coś poszło nie tak."): UiText =
        map(t, fallback).uiText
}