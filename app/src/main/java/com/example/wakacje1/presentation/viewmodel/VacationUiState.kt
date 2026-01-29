package com.example.wakacje1.presentation.viewmodel

import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.usecase.TransportPass

// Główny stan ekranu
data class VacationUiState(
    val isLoading: Boolean = false,
    val preferences: Preferences? = null,
    val destinationSuggestions: List<Destination> = emptyList(),
    val chosenDestination: Destination? = null,
    val plan: List<DayPlan> = emptyList(),
    val weather: WeatherUiState = WeatherUiState(),
    val dayWeatherByDate: Map<Long, DayWeatherUi> = emptyMap(),
    val forecastNotice: String? = null,
    val uiMessage: String? = null,
    val canEditPlan: Boolean = true,
    val lastTransportPass: TransportPass = TransportPass.T_MAX
)

// Stan pogody bieżącej (nagłówek)
data class WeatherUiState(
    val loading: Boolean = false,
    val city: String? = null,
    val temperature: Double? = null,
    val description: String? = null,
    val error: String? = null
)

// Stan pogody na konkretny dzień (lista)
data class DayWeatherUi(
    val dateMillis: Long,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    val isBadWeather: Boolean
)