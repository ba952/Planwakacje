package com.example.wakacje1.data.remote

/**
 * Model DTO (Data Transfer Object) reprezentujący skrócony widok planu w chmurze (Firestore).
 * Używany do wyświetlania listy planów bez pobierania pełnego, ciężkiego payloadu JSON.
 * Wymaga pustego konstruktora (wartości domyślne) dla deserializatora Firebase.
 */
data class CloudPlanRow(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)