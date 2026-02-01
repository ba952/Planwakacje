package com.example.wakacje1.data.local

import androidx.room.Entity
import androidx.room.Index

/**
 * Encja reprezentująca wiersz w tabeli 'local_plans'.
 * Przechowuje zapisane lokalnie plany wycieczek dla konkretnego użytkownika.
 *
 */
@Entity(
    tableName = "local_plans",
    // Złożony klucz główny (Composite Key) pozwala na przechowywanie planów wielu użytkowników w jednej tabeli
    primaryKeys = ["uid", "id"],
    // Indeks przyspieszający sortowanie po dacie modyfikacji dla danego usera (kluczowe dla "Ostatnie plany")
    indices = [
        Index(value = ["uid", "updatedAtMillis"])
    ]
)
data class LocalPlanEntity(
    val uid: String,
    val id: String,
    val title: String,
    val startDateMillis: Long?,
    val endDateMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val payloadJson: String // Serializowany obiekt StoredPlanJson
)