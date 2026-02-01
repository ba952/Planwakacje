package com.example.wakacje1.data.local

/**
 * Zoptymalizowany model (Projekcja) do wyświetlania listy planów w UI.
 * Zawiera tylko niezbędne pola nagłówkowe, pomijając ciężki 'payloadJson'.
 * Dzięki temu zapytanie SQL jest szybsze i zużywa mniej pamięci.
 */
data class LocalPlanRow(
    val id: String,
    val title: String,
    val startDateMillis: Long?,
    val endDateMillis: Long?,
    val updatedAtMillis: Long
)