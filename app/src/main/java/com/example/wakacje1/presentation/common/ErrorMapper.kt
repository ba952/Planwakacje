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
 * Mapuje Throwable -> AppError bez surowych tekstów w kodzie.
 * Każdy komunikat użytkownika pochodzi z strings.xml (R.string.*).
 */
object ErrorMapper {

    fun map(t: Throwable, fallbackResId: Int = R.string.error_generic): AppError {
        val tech = t.message

        return when (t) {
            // --- WYJĄTKI DOMENOWE (Weather) ---
            is WeatherException.NetworkError -> AppError.Recoverable(
                UiText.StringResource(R.string.error_weather_network),
                tech
            )
            is WeatherException.CityNotFound -> AppError.Recoverable(
                UiText.StringResource(R.string.error_weather_city_not_found),
                tech
            )
            is WeatherException.InvalidApiKey -> AppError.Recoverable(
                UiText.StringResource(R.string.error_weather_api_key),
                tech
            )
            is WeatherException.ApiError -> AppError.Recoverable(
                UiText.StringResource(R.string.error_weather_general, t.code),
                tech
            )
            is WeatherException.Unknown -> AppError.Unknown(
                fallback = UiText.StringResource(R.string.error_unknown),
                tech = t.cause?.message
            )

            // --- Firebase ---
            is FirebaseFirestoreException -> when (t.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> AppError.Permission(tech)
                FirebaseFirestoreException.Code.NOT_FOUND -> AppError.NotFound(tech)
                FirebaseFirestoreException.Code.UNAVAILABLE -> AppError.Network(tech)
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.Timeout(tech)
                else -> AppError.Unknown(UiText.StringResource(fallbackResId), tech)
            }

            is FirebaseAuthException -> AppError.Auth(tech)

            // --- Standardowe wyjątki systemowe ---
            is TimeoutCancellationException,
            is SocketTimeoutException -> AppError.Timeout(tech)

            is UnknownHostException -> AppError.Network(tech)

            is SecurityException -> AppError.Permission(tech)

            // Uwaga: IOException bywa też siecią, ale u Ciebie jest mapowane na Storage — zostawiam jak było
            is IOException -> AppError.Storage(tech)

            else -> AppError.Unknown(UiText.StringResource(fallbackResId), tech)
        }
    }

    fun mapToUiText(t: Throwable, fallbackResId: Int = R.string.error_generic): UiText =
        map(t, fallbackResId).uiText

    /**
     * Specjalny mapper dla scenariusza: cloud save failed, local save ok.
     * Zero tekstów w kodzie -> wszystko z R.string.
     */
    fun mapCloudSaveFailedButLocalOk(t: Throwable): AppError {
        val tech = t.message

        return when (t) {
            is TimeoutCancellationException,
            is SocketTimeoutException,
            is UnknownHostException -> AppError.SavedLocallyNoInternet(tech)

            is IOException -> AppError.SavedLocallyNoInternet(tech)

            is FirebaseFirestoreException -> when (t.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> AppError.SavedLocallyNoInternet(tech)
                else -> AppError.Recoverable(
                    UiText.StringResource(R.string.error_cloud_sync_failed_local_saved),
                    tech
                )
            }

            else -> AppError.Recoverable(
                UiText.StringResource(R.string.error_cloud_sync_failed_local_saved),
                tech
            )
        }
    }
}
