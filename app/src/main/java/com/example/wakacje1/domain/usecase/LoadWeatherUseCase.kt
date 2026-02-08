package com.example.wakacje1.domain.usecase

import com.example.wakacje1.domain.weather.CurrentWeather
import com.example.wakacje1.domain.weather.WeatherRepository
import com.example.wakacje1.domain.weather.WeatherResult

class LoadWeatherUseCase(
    private val weatherRepository: WeatherRepository
) {
    suspend fun execute(cityQuery: String, force: Boolean): WeatherResult<CurrentWeather> {
        // Domain nie mapuje na UI. Zwracamy domenowy wynik z repo.
        return weatherRepository.getCurrentWeather(
            cityQuery = cityQuery,
            forceRefresh = force
        )
    }
}
