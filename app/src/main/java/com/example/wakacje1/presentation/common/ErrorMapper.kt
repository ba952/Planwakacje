package com.example.wakacje1.presentation.common

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
            is FirebaseFirestoreException -> when (t.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.Permission(tech)
                FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound(tech)
                FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.Network(tech)
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Timeout(tech)
                // Teraz to zadziała, bo Unknown przyjmuje UiText
                else -> AppError.Unknown(UiText.DynamicString(fallback), tech)
            }

            is FirebaseAuthException -> AppError.Auth(tech)

            is TimeoutCancellationException,
            is SocketTimeoutException -> AppError.Timeout(tech)

            is UnknownHostException -> AppError.Network(tech)

            is SecurityException -> AppError.Permission(tech)

            is IOException -> AppError.Storage(tech)

            // Tutaj też zadziała
            else -> AppError.Unknown(UiText.DynamicString(fallback), tech)
        }
    }

    // Funkcja pomocnicza zwracająca UiText
    fun mapToUiText(t: Throwable, fallback: String = "Coś poszło nie tak."): UiText =
        map(t, fallback).uiText
}