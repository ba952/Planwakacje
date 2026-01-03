package com.example.wakacje1.domain.model

data class Preferences(
    val budget: Int,                 // budżet całkowity (PLN)
    val days: Int,                   // liczba dni
    val climate: String,             // np. "Ciepły", "Umiarkowany", "Chłodny"
    val region: String,              // np. "Europa - miasto", "Morze Śródziemne", "Góry"
    val style: String,               // np. "Relaks", "Zwiedzanie", "Aktywny", "Mix"
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null
)