package com.example.wakacje1.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

/**
 * Niskopoziomowy zarządca instancją bazy danych Room.
 * Implementuje wzorzec Singleton dla obiektu bazy oraz
 * obsługuje fizyczną serializację/deserializację planów (JSON <-> Entity).
 */
object PlanStorage {

    @Volatile
    private var db: LocalPlansDb? = null

    /**
     * Zwraca instancję bazy danych (Singleton).
     * Wykorzystuje double-checked locking dla bezpieczeństwa wątkowego.
     * Rejestruje migracje schematu.
     */
    private fun getDb(context: Context): LocalPlansDb {
        val appCtx = context.applicationContext
        return db ?: synchronized(this) {
            db ?: Room.databaseBuilder(appCtx, LocalPlansDb::class.java, "local_plans.db")
                .addMigrations(LocalPlansDb.MIGRATION_1_2, LocalPlansDb.MIGRATION_2_3)
                .build()
                .also { db = it }
        }
    }

    fun observePlans(context: Context, uid: String): Flow<List<LocalPlanRow>> {
        return getDb(context).plans().observeRows(uid)
    }

    /**
     * Zapisuje plan w bazie (Insert lub Update).
     * Konwertuje złożony obiekt [StoredPlan] na ciąg JSON (payload) oraz
     * zabezpiecza pola dat przed wartościami "0" (defensive programming).
     */
    suspend fun upsertPlan(
        context: Context,
        uid: String,
        plan: StoredPlan,
        title: String,
        startDateMillis: Long?,
        endDateMillis: Long?,
        updatedAtMillis: Long
    ) {
        val entity = LocalPlanEntity(
            uid = uid,
            id = plan.id,
            title = title,
            // Defensywnie: konwersja technicznego 0L na logiczny NULL
            startDateMillis = startDateMillis?.takeIf { it > 0L },
            endDateMillis = endDateMillis?.takeIf { it > 0L },
            createdAtMillis = plan.createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            payloadJson = plan.toJsonString() // Fizyczna serializacja struktury
        )
        getDb(context).plans().upsert(entity)
    }

    /**
     * Pobiera encję z bazy i deserializuje payload JSON do obiektu domenowego.
     */
    suspend fun loadPlan(context: Context, uid: String, id: String): StoredPlan? {
        val e = getDb(context).plans().getById(uid, id) ?: return null
        return StoredPlan.fromJsonString(e.payloadJson)
    }

    suspend fun loadLatestPlan(context: Context, uid: String): StoredPlan? {
        val e = getDb(context).plans().getLatest(uid) ?: return null
        return StoredPlan.fromJsonString(e.payloadJson)
    }

    suspend fun deletePlan(context: Context, uid: String, id: String) {
        getDb(context).plans().delete(uid, id)
    }
}