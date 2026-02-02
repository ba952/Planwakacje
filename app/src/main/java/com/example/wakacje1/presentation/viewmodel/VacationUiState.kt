package com.example.wakacje1.presentation.viewmodel

import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.usecase.TransportPass
import com.example.wakacje1.presentation.common.UiText

/**
 * Główny stan interfejsu użytkownika (UI State) dla procesu planowania wakacji.
 */
data class VacationUiState(
    val isLoading: Boolean = false,
    val preferences: Preferences? = null,
    val destinationSuggestions: List<Destination> = emptyList(),
    val chosenDestination: Destination? = null,
    val plan: List<DayPlan> = emptyList(),
    val weather: WeatherUiState = WeatherUiState(),
    val dayWeatherByDate: Map<Long, DayWeatherUi> = emptyMap(),

    // ✅ UiText zamiast String (stringi z zasobów)
    val forecastNotice: UiText? = null,

    val uiMessage: String? = null,
    val canEditPlan: Boolean = true,
    val lastTransportPass: TransportPass = TransportPass.T_MAX
)

data class WeatherUiState(
    val loading: Boolean = false,
    val city: String? = null,
    val temperature: Double? = null,
    val description: String? = null,

    // ✅ UiText zamiast String
    val error: UiText? = null
)

data class DayWeatherUi(
    val dateMillis: Long,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    val isBadWeather: Boolean
)
