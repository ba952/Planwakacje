package com.example.wakacje1.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlanDao {

    @Query("""
        SELECT id, title, startDateMillis, endDateMillis, updatedAtMillis
        FROM local_plans
        WHERE uid = :uid
        ORDER BY updatedAtMillis DESC
    """)
    fun observeRows(uid: String): Flow<List<LocalPlanRow>>

    @Query("""
        SELECT * FROM local_plans
        WHERE uid = :uid AND id = :id
        LIMIT 1
    """)
    suspend fun getById(uid: String, id: String): LocalPlanEntity?

    @Query("""
        SELECT * FROM local_plans
        WHERE uid = :uid
        ORDER BY updatedAtMillis DESC
        LIMIT 1
    """)
    suspend fun getLatest(uid: String): LocalPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalPlanEntity)

    @Query("DELETE FROM local_plans WHERE uid = :uid AND id = :id")
    suspend fun delete(uid: String, id: String)
}
