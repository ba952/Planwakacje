package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.presentation.viewmodel.WeatherUiState

/**
 * UseCase pobierający bieżącą pogodę dla wskazanej lokalizacji.
 * Mapuje wynik domenowy bezpośrednio na obiekt stanu UI, obsługując błędy (try-catch).
 */
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
                description = r.description
            )
        } catch (e: Exception) {
            // Fail-safe: Zwracamy stan błędu zamiast rzucać wyjątek do ViewModelu
            WeatherUiState(loading = false, error = e.message ?: "Nie udało się pobrać pogody.")
        }
    }
}