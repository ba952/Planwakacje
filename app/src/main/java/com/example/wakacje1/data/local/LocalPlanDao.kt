package com.example.wakacje1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) dla tabeli local_plans.
 * Definiuje metody dostępu do lokalnej bazy SQLite (Room).
 */
@Dao
interface LocalPlanDao {

    /**
     * Zwraca reaktywny strumień (Flow) listy planów dla danego użytkownika.
     * Pobiera tylko kluczowe pola (projekcja do LocalPlanRow) w celu optymalizacji UI.
     * Dane są automatycznie odświeżane przy zmianach w tabeli.
     */
    @Query("""
        SELECT id, title, startDateMillis, endDateMillis, updatedAtMillis
        FROM local_plans
        WHERE uid = :uid
        ORDER BY updatedAtMillis DESC
    """)
    fun observeRows(uid: String): Flow<List<LocalPlanRow>>

    /**
     * Pobiera pełną encję planu (wraz z dużym blobem JSON) na podstawie ID.
     */
    @Query("""
        SELECT * FROM local_plans
        WHERE uid = :uid AND id = :id
        LIMIT 1
    """)
    suspend fun getById(uid: String, id: String): LocalPlanEntity?

    /**
     * Pobiera ostatnio edytowany plan użytkownika (mechanizm "Ostatnia sesja").
     */
    @Query("""
        SELECT * FROM local_plans
        WHERE uid = :uid
        ORDER BY updatedAtMillis DESC
        LIMIT 1
    """)
    suspend fun getLatest(uid: String): LocalPlanEntity?

    /**
     * Wstawia nowy plan lub nadpisuje istniejący (Update), jeśli ID już istnieje.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalPlanEntity)

    /**
     * Trwale usuwa wskazany plan z bazy danych.
     */
    @Query("DELETE FROM local_plans WHERE uid = :uid AND id = :id")
    suspend fun delete(uid: String, id: String)
}