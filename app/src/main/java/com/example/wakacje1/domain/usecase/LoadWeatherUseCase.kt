package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.presentation.viewmodel.WeatherUiState

class LoadWeatherUseCase(
    private val weatherRepository: WeatherRepository // <--- WSTRZYKUJEMY REPOZYTORIUM
) {
    suspend fun execute(cityQuery: String, force: Boolean): WeatherUiState {
        return try {
            // Używamy instancji 'weatherRepository' (z małej litery)
            val r = weatherRepository.getWeatherForCity(cityQuery, forceRefresh = force)

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