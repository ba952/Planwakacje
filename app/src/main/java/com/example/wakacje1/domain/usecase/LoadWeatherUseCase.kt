package com.example.wakacje1.domain.usecase

import com.example.wakacje1.R
import com.example.wakacje1.data.remote.WeatherException
import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.presentation.common.UiText
import com.example.wakacje1.presentation.viewmodel.WeatherUiState

class LoadWeatherUseCase(
    private val weatherRepository: WeatherRepository
) {
    suspend fun execute(cityQuery: String, force: Boolean): WeatherUiState {
        return try {
            val r = weatherRepository.getWeatherForCity(cityQuery, forceRefresh = force)

            WeatherUiState(
                loading = false,
                city = r.city,
                temperature = r.temperature,
                description = r.description,
                error = null
            )
        } catch (e: WeatherException) {
            WeatherUiState(
                loading = false,
                error = mapWeatherError(e)
            )
        } catch (_: Exception) {
            WeatherUiState(
                loading = false,
                error = UiText.StringResource(R.string.error_weather_generic)
            )
        }
    }

    private fun mapWeatherError(e: WeatherException): UiText {
        return when (e) {
            is WeatherException.InvalidApiKey ->
                UiText.StringResource(R.string.error_weather_invalid_api_key)
            is WeatherException.CityNotFound ->
                UiText.StringResource(R.string.error_weather_city_not_found)
            is WeatherException.NetworkError ->
                UiText.StringResource(R.string.error_weather_network)
            is WeatherException.ApiError ->
                UiText.StringResource(R.string.error_weather_api, e.code)
            is WeatherException.Unknown ->
                UiText.StringResource(R.string.error_weather_unknown)
        }
    }
}
