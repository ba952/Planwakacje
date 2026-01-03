package com.example.wakacje1.data.local

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow

object PlanStorage {

    @Volatile
    private var db: LocalPlansDb? = null

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
            // defensywnie: jeśli ktoś jeszcze poda 0L -> zapisujemy null
            startDateMillis = startDateMillis?.takeIf { it > 0L },
            endDateMillis = endDateMillis?.takeIf { it > 0L },
            createdAtMillis = plan.createdAtMillis,
            updatedAtMillis = updatedAtMillis,
            payloadJson = plan.toJsonString()
        )
        getDb(context).plans().upsert(entity)
    }

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
