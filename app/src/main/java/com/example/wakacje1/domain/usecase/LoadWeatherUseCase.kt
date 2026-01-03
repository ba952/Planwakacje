package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.presentation.viewmodel.WeatherUiState

class LoadWeatherUseCase {
    suspend fun execute(cityQuery: String, force: Boolean): WeatherUiState {
        return try {
            val r = WeatherRepository.getWeatherForCity(cityQuery, forceRefresh = force)
            WeatherUiState(
                loading = false,
                city = r.city,
                temperature = r.temperature,
                description = r.description
            )
        } catch (e: Exception) {
            WeatherUiState(loading = false, error = e.message ?: "Nie udało się pobrać pogody.")
        }
    }
}
