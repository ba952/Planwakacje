package com.example.wakacje1.domain.model

/**
 * Model wejściowy preferencji użytkownika.
 * Zbiera kryteria niezbędne do filtrowania destynacji i generowania harmonogramu.
 */
data class Preferences(
    val budget: Int,                 // Całkowity budżet na wyjazd (PLN)
    val days: Int,                   // Długość pobytu
    val climate: String,             // Filtr: Klimat (np. "Ciepły")
    val region: String,              // Filtr: Region (np. "Europa")
    val style: String,               // Filtr: Styl (np. "Zwiedzanie")
    val startDateMillis: Long? = null, // Opcjonalne: Data startu (dla WeatherApi)
    val endDateMillis: Long? = null    // Opcjonalne: Data końca
)