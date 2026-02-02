package com.example.wakacje1.domain.usecase

import com.example.wakacje1.R
import com.example.wakacje1.data.remote.WeatherException
import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.presentation.common.UiText
import com.example.wakacje1.presentation.viewmodel.DayWeatherUi
import com.example.wakacje1.util.DateUtils

data class ForecastResult(
    val byDate: Map<Long, DayWeatherUi>,
    val notice: UiText?
)

class LoadForecastForTripUseCase(
    private val weatherRepository: WeatherRepository
) {
    suspend fun execute(prefs: Preferences, dest: Destination, force: Boolean): ForecastResult {
        val startMillis = prefs.startDateMillis ?: return ForecastResult(emptyMap(), null)
        val days = prefs.days.coerceAtLeast(1)

        return try {
            val forecast = weatherRepository.getForecastForCity(dest.apiQuery, forceRefresh = force)
            val byDate = forecast.associateBy { it.dateMillis }

            val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
            val map = mutableMapOf<Long, DayWeatherUi>()
            var covered = 0

            for (i in 0 until days) {
                val dayMillis = DateUtils.dayMillisForIndex(startNorm, i)
                val f = byDate[dayMillis]
                if (f != null) covered++

                map[dayMillis] = DayWeatherUi(
                    dateMillis = dayMillis,
                    tempMin = f?.tempMin,
                    tempMax = f?.tempMax,
                    description = f?.description,
                    isBadWeather = f?.isBadWeather ?: false
                )
            }

            val notice = when {
                covered == 0 -> UiText.StringResource(R.string.notice_forecast_unavailable)
                covered < days -> UiText.StringResource(R.string.notice_forecast_partial, covered, days)
                else -> null
            }

            ForecastResult(map, notice)
        } catch (e: WeatherException) {
            ForecastResult(emptyMap(), mapForecastError(e))
        } catch (_: Exception) {
            ForecastResult(emptyMap(), UiText.StringResource(R.string.notice_forecast_failed))
        }
    }

    private fun mapForecastError(e: WeatherException): UiText {
        return when (e) {
            is WeatherException.InvalidApiKey ->
                UiText.StringResource(R.string.notice_forecast_invalid_api_key)
            is WeatherException.CityNotFound ->
                UiText.StringResource(R.string.notice_forecast_city_not_found)
            is WeatherException.NetworkError ->
                UiText.StringResource(R.string.notice_forecast_network)
            is WeatherException.ApiError ->
                UiText.StringResource(R.string.notice_forecast_api, e.code)
            is WeatherException.Unknown ->
                UiText.StringResource(R.string.notice_forecast_unknown)
        }
    }
}
