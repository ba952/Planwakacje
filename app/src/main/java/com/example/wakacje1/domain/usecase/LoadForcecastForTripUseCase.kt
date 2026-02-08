package com.example.wakacje1.domain.usecase

import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.weather.ForecastDay
import com.example.wakacje1.domain.weather.WeatherFailure
import com.example.wakacje1.domain.weather.WeatherRepository
import com.example.wakacje1.domain.weather.WeatherResult
import com.example.wakacje1.util.DateUtils

/**
 * Domena nie zwraca UiText ani typów UI.
 * "notice" jest ujęty jako enum/stan domenowy, a mapowanie na tekst robimy w presentation.
 */
enum class ForecastNotice {
    UNAVAILABLE,
    PARTIAL,
    FAILED,
    INVALID_API_KEY,
    CITY_NOT_FOUND,
    NETWORK,
    API_ERROR,
    UNKNOWN
}

data class ForecastResult(
    val byDate: Map<Long, ForecastDay?>,
    val notice: ForecastNotice?,
    val coveredDays: Int,
    val requestedDays: Int
)

class LoadForecastForTripUseCase(
    private val weatherRepository: WeatherRepository
) {
    suspend fun execute(prefs: Preferences, dest: Destination, force: Boolean): ForecastResult {
        val startMillis = prefs.startDateMillis ?: return ForecastResult(
            byDate = emptyMap(),
            notice = null,
            coveredDays = 0,
            requestedDays = 0
        )
        val days = prefs.days.coerceAtLeast(1)

        val repoResult = weatherRepository.getForecast(
            cityQuery = dest.apiQuery,
            forceRefresh = force
        )

        return when (repoResult) {
            is WeatherResult.Success -> {
                val forecast = repoResult.data
                val byDate = forecast.associateBy { it.dateMillis }

                val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
                val map = mutableMapOf<Long, ForecastDay?>()
                var covered = 0

                for (i in 0 until days) {
                    val dayMillis = DateUtils.dayMillisForIndex(startNorm, i)
                    val f = byDate[dayMillis]
                    if (f != null) covered++
                    map[dayMillis] = f
                }

                val notice = when {
                    covered == 0 -> ForecastNotice.UNAVAILABLE
                    covered < days -> ForecastNotice.PARTIAL
                    else -> null
                }

                ForecastResult(
                    byDate = map,
                    notice = notice,
                    coveredDays = covered,
                    requestedDays = days
                )
            }

            is WeatherResult.Failure -> {
                ForecastResult(
                    byDate = emptyMap(),
                    notice = mapFailureToNotice(repoResult.failure),
                    coveredDays = 0,
                    requestedDays = days
                )
            }
        }
    }

    private fun mapFailureToNotice(f: WeatherFailure): ForecastNotice {
        return when (f) {
            is WeatherFailure.Unauthorized -> ForecastNotice.INVALID_API_KEY
            is WeatherFailure.NotFound -> ForecastNotice.CITY_NOT_FOUND
            is WeatherFailure.Network -> ForecastNotice.NETWORK
            is WeatherFailure.RateLimited -> ForecastNotice.API_ERROR
            is WeatherFailure.ApiError -> ForecastNotice.API_ERROR
            is WeatherFailure.Unknown -> ForecastNotice.UNKNOWN
        }
    }
}
