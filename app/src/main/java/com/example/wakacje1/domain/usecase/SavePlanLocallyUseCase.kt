package com.example.wakacje1.domain.usecase

import android.content.Context
import com.example.wakacje1.data.local.PlanStorage
import com.example.wakacje1.data.local.StoredDestination
import com.example.wakacje1.data.local.StoredInternalDayPlan
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.data.local.StoredPreferences
import com.example.wakacje1.data.remote.CloudPlansRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class SaveLocalResult(
    val planId: String,
    val createdAtMillis: Long,
    val cloudError: Throwable? = null
)

class SavePlanLocallyUseCase {
    suspend fun execute(
        ctx: Context,
        uid: String,
        prefs: Preferences,
        dest: Destination,
        internalDays: List<InternalDayPlan>,
        currentPlanId: String?,
        currentCreatedAtMillis: Long?
    ): SaveLocalResult {
        val now = System.currentTimeMillis()
        val id = currentPlanId ?: UUID.randomUUID().toString()
        val createdAt = currentCreatedAtMillis ?: now

        val stored = StoredPlan(
            id = id,
            createdAtMillis = createdAt,
            destination = StoredDestination.from(dest),
            preferences = StoredPreferences.from(prefs),
            internalDays = internalDays.map { StoredInternalDayPlan.from(it) }
        )

        val title = dest.displayName.ifBlank { "Plan" }
        val start = prefs.startDateMillis
        val end = prefs.endDateMillis

        // 1) zapis lokalny
        withContext(Dispatchers.IO) {
            PlanStorage.upsertPlan(
                context = ctx,
                uid = uid,
                plan = stored,
                title = title,
                startDateMillis = start,
                endDateMillis = end,
                updatedAtMillis = now
            )
        }

        // 2) próba zapisu do chmury (żeby działało między urządzeniami od razu)
        val cloudErr: Throwable? = try {
            withTimeout(15_000) {
                CloudPlansRepository.upsertPlan(uid, stored, updatedAtMillis = now)
            }
            null
        } catch (t: Throwable) {
            t
        }

        return SaveLocalResult(
            planId = stored.id,
            createdAtMillis = stored.createdAtMillis,
            cloudError = cloudErr
        )
    }
}
