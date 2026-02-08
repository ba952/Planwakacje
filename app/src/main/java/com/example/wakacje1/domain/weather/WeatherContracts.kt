package com.example.wakacje1.domain.weather

/**
 * Kontrakty i modele domenowe dla modułu pogody.
 * Tu nie ma Androida, UI (UiText/WeatherUiState) ani zasobów R.
 */

sealed interface WeatherResult<out T> {
    data class Success<T>(val data: T) : WeatherResult<T>
    data class Failure(val failure: WeatherFailure) : WeatherResult<Nothing>
}

sealed class WeatherFailure {
    data object Network : WeatherFailure()
    data object NotFound : WeatherFailure()          // np. miasto nie istnieje / brak danych
    data object Unauthorized : WeatherFailure()      // np. 401 / zły API key
    data object RateLimited : WeatherFailure()       // np. 429
    data class ApiError(val code: Int? = null) : WeatherFailure()
    data class Unknown(val message: String? = null) : WeatherFailure()
}

/**
 * Aktualna pogoda w modelu domenowym.
 * temperature - w stopniach Celsjusza (API masz ustawione na units=metric).
 */
data class CurrentWeather(
    val city: String,
    val temperature: Double,
    val description: String,
    val conditionId: Int
)

/**
 * Prognoza dzienna w modelu domenowym.
 * dateMillis - klucz dnia (np. znormalizowany do lokalnej północy).
 */
data class ForecastDay(
    val dateMillis: Long,
    val tempMin: Double,
    val tempMax: Double,
    val description: String,
    val conditionId: Int,
    val isBadWeather: Boolean
)

/**
 * Wynik prognozy pod wyjazd (dla konkretnych dni).
 * daysByDate może zawierać null, jeśli brak prognozy dla danego dnia.
 */
data class TripForecast(
    val daysByDate: Map<Long, ForecastDay?>,
    val coveredDays: Int,
    val requestedDays: Int
)

/**
 * Kontrakt domeny: domain zależy od interfejsu, a nie od implementacji z data.
 */
interface WeatherRepository {
    suspend fun getCurrentWeather(
        cityQuery: String,
        forceRefresh: Boolean = false
    ): WeatherResult<CurrentWeather>

    suspend fun getForecast(
        cityQuery: String,
        forceRefresh: Boolean = false
    ): WeatherResult<List<ForecastDay>>
}
