package com.example.wakacje1.presentation.viewmodel

import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.usecase.TransportPass

/**
 * Główny stan interfejsu użytkownika (UI State) dla procesu planowania wakacji.
 * Agreguje wszystkie dane niezbędne do wyświetlenia ekranów kreatora i podglądu planu.
 */
data class VacationUiState(
    // Flaga określająca globalny stan ładowania (np. podczas generowania planu)
    val isLoading: Boolean = false,

    // Zapisane preferencje użytkownika dotyczące wyjazdu (budżet, daty, styl)
    val preferences: Preferences? = null,

    // Lista sugerowanych celów podróży wygenerowana na podstawie preferencji
    val destinationSuggestions: List<Destination> = emptyList(),

    // Wybrana przez użytkownika destynacja z listy sugestii
    val chosenDestination: Destination? = null,

    // Finalny, sformatowany harmonogram wycieczki podzielony na dni
    val plan: List<DayPlan> = emptyList(),

    // Szczegółowy stan pogody bieżącej dla wybranego miejsca
    val weather: WeatherUiState = WeatherUiState(),

    // Mapa prognoz pogody przypisanych do konkretnych dni (kluczem jest milisekundowy timestamp)
    val dayWeatherByDate: Map<Long, DayWeatherUi> = emptyMap(),

    // Dodatkowa informacja o prognozie (np. "Brak danych dla odległych dat")
    val forecastNotice: String? = null,

    // Komunikat tekstowy dla UI (np. powiadomienie o sukcesie zapisu)
    val uiMessage: String? = null,

    // Flaga blokująca edycję planu (np. gdy plan jest wczytany z archiwum)
    val canEditPlan: Boolean = true,

    // Ostatnio zastosowany tryb kosztów transportu (Min/Avg/Max) do filtrowania sugestii
    val lastTransportPass: TransportPass = TransportPass.T_MAX
)

/**
 * Reprezentacja danych pogodowych wyświetlanych w nagłówku ekranu.
 */
data class WeatherUiState(
    val loading: Boolean = false,
    val city: String? = null,
    val temperature: Double? = null,
    val description: String? = null, // Opis słowny (np. "Zachmurzenie umiarkowane")
    val error: String? = null        // Komunikat błędu pobierania pogody
)

/**
 * Model widoku (ViewModel model) dla pogody wyświetlanej przy każdym dniu planu.
 */
data class DayWeatherUi(
    val dateMillis: Long,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    // Flaga informująca logikę planera, czy pogoda jest "zła" (np. deszcz), co wymusza atrakcje indoor
    val isBadWeather: Boolean
)