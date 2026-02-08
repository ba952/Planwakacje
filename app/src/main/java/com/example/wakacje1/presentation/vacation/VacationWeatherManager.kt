package com.example.wakacje1.presentation.vacation

import com.example.wakacje1.R
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.usecase.ForecastNotice
import com.example.wakacje1.domain.usecase.ForecastResult
import com.example.wakacje1.domain.usecase.LoadForecastForTripUseCase
import com.example.wakacje1.domain.usecase.LoadWeatherUseCase
import com.example.wakacje1.domain.weather.CurrentWeather
import com.example.wakacje1.domain.weather.WeatherFailure
import com.example.wakacje1.domain.weather.WeatherResult
import com.example.wakacje1.presentation.common.UiText
import com.example.wakacje1.presentation.viewmodel.DayWeatherUi
import com.example.wakacje1.presentation.viewmodel.WeatherUiState
import com.example.wakacje1.util.DateUtils

/**
 * Logika pogody/prognozy (presentation-layer):
 * - use-case'y zwracają domenowe modele i błędy (bez UI)
 * - tutaj mapujemy na typy UI (WeatherUiState, DayWeatherUi, UiText)
 */
class VacationWeatherManager(
    private val loadWeatherUseCase: LoadWeatherUseCase,
    private val loadForecastForTripUseCase: LoadForecastForTripUseCase
) {

    suspend fun loadWeatherForCity(cityQuery: String, force: Boolean): WeatherUiState {
        return when (val res = loadWeatherUseCase.execute(cityQuery, force)) {
            is WeatherResult.Success -> mapCurrentSuccess(res.data)
            is WeatherResult.Failure -> WeatherUiState(
                loading = false,
                error = mapWeatherFailure(res.failure)
            )
        }
    }

    suspend fun loadForecastForTrip(prefs: Preferences, dest: Destination, force: Boolean): ForecastUiResult {
        val r: ForecastResult = loadForecastForTripUseCase.execute(prefs, dest, force)

        val uiMap = r.byDate.mapValues { (_, f) ->
            DayWeatherUi(
                dateMillis = f?.dateMillis ?: 0L, // zostanie nadpisane niżej jeśli null
                tempMin = f?.tempMin,
                tempMax = f?.tempMax,
                description = f?.description,
                isBadWeather = f?.isBadWeather ?: false
            )
        }.toMutableMap()

        // Upewniamy się, że dateMillis w DayWeatherUi = klucz mapy (ważne dla UI)
        uiMap.keys.forEach { k ->
            val old = uiMap[k]
            if (old != null) uiMap[k] = old.copy(dateMillis = k)
        }

        val noticeText = mapForecastNoticeToUiText(r.notice, r.coveredDays, r.requestedDays)

        return ForecastUiResult(
            byDate = uiMap,
            notice = noticeText
        )
    }

    fun isBadWeatherForDayIndex(
        prefs: Preferences?,
        dayWeatherByDate: Map<Long, DayWeatherUi>,
        dayIndex: Int
    ): Boolean {
        val startMillis = prefs?.startDateMillis ?: return false
        val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
        val dayMillis = DateUtils.dayMillisForIndex(startNorm, dayIndex)
        return dayWeatherByDate[dayMillis]?.isBadWeather ?: false
    }

    fun getDayWeatherForIndex(
        prefs: Preferences?,
        dayWeatherByDate: Map<Long, DayWeatherUi>,
        dayIndex: Int
    ): DayWeatherUi? {
        val startMillis = prefs?.startDateMillis ?: return null
        val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
        val key = DateUtils.dayMillisForIndex(startNorm, dayIndex)
        return dayWeatherByDate[key]
    }

    // --- mappers ---

    private fun mapCurrentSuccess(d: CurrentWeather): WeatherUiState {
        return WeatherUiState(
            loading = false,
            city = d.city,
            temperature = d.temperature,
            description = d.description,
            error = null
        )
    }

    private fun mapWeatherFailure(f: WeatherFailure): UiText {
        return when (f) {
            is WeatherFailure.Unauthorized ->
                UiText.StringResource(R.string.error_weather_invalid_api_key)
            is WeatherFailure.NotFound ->
                UiText.StringResource(R.string.error_weather_city_not_found)
            is WeatherFailure.Network ->
                UiText.StringResource(R.string.error_weather_network)
            is WeatherFailure.RateLimited ->
                UiText.StringResource(R.string.error_weather_api, 429)
            is WeatherFailure.ApiError ->
                UiText.StringResource(R.string.error_weather_api, f.code ?: -1)
            is WeatherFailure.Unknown ->
                UiText.StringResource(R.string.error_weather_unknown)
        }
    }

    private fun mapForecastNoticeToUiText(
        notice: ForecastNotice?,
        covered: Int,
        requested: Int
    ): UiText? {
        return when (notice) {
            null -> null
            ForecastNotice.UNAVAILABLE ->
                UiText.StringResource(R.string.notice_forecast_unavailable)
            ForecastNotice.PARTIAL ->
                UiText.StringResource(R.string.notice_forecast_partial, covered, requested)
            ForecastNotice.FAILED ->
                UiText.StringResource(R.string.notice_forecast_failed)
            ForecastNotice.INVALID_API_KEY ->
                UiText.StringResource(R.string.notice_forecast_invalid_api_key)
            ForecastNotice.CITY_NOT_FOUND ->
                UiText.StringResource(R.string.notice_forecast_city_not_found)
            ForecastNotice.NETWORK ->
                UiText.StringResource(R.string.notice_forecast_network)
            ForecastNotice.API_ERROR ->
                UiText.StringResource(R.string.notice_forecast_api, -1)
            ForecastNotice.UNKNOWN ->
                UiText.StringResource(R.string.notice_forecast_unknown)
        }
    }
}

/**
 * UI-wynik prognozy, który trafia do VacationViewModel.
 */
data class ForecastUiResult(
    val byDate: Map<Long, DayWeatherUi>,
    val notice: UiText?
)
