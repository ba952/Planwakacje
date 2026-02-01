package com.example.wakacje1.domain.model

/**
 * Model widoku (View Model / DTO) reprezentujący plan jednego dnia wycieczki.
 *
 * Służy do przekazywania sformatowanych danych z warstwy domeny do warstwy prezentacji (UI).
 * W przeciwieństwie do InternalDayPlan, nie zawiera surowych obiektów Slotów,
 * lecz gotowe ciągi znaków przygotowane do wyświetlenia na ekranie.
 *
 * property day Numer kolejny dnia (1, 2, ...).
 * property title Sformatowany nagłówek dnia (np. "Dzień 1: Paryż").
 * property details Sformatowana treść planu (lista atrakcji z opisami, gotowa do wstawienia w Text).
 */
data class DayPlan(
    val day: Int,
    val title: String,
    val details: String
)