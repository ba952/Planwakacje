package com.example.wakacje1.data.local

import android.content.Context
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class PlansLocalRepository(private val context: Context) {

    fun observePlans(uid: String): Flow<List<LocalPlanRow>> {
        return PlanStorage.observePlans(context, uid)
    }

    suspend fun loadPlan(uid: String, planId: String): StoredPlan? {
        return PlanStorage.loadPlan(context, uid, planId)
    }

    suspend fun loadLatestPlan(uid: String): StoredPlan? {
        return PlanStorage.loadLatestPlan(context, uid)
    }

    suspend fun deletePlan(uid: String, planId: String) {
        PlanStorage.deletePlan(context, uid, planId)
    }

    suspend fun upsertPlan(
        uid: String,
        planId: String?,
        createdAtMillis: Long?,
        prefs: Preferences,
        dest: Destination,
        internalDays: List<InternalDayPlan>,
        updatedAtMillis: Long // ZMIANA: Przyjmujemy czas z zewnątrz
    ): StoredPlan {
        // 1. Generujemy ID i daty
        val finalId = planId ?: UUID.randomUUID().toString()
        val finalCreated = createdAtMillis ?: System.currentTimeMillis()

        // 2. Konwersja Domain -> Stored
        val storedPlan = StoredPlan(
            id = finalId,
            createdAtMillis = finalCreated,
            preferences = prefs.toStored(),
            destination = dest.toStored(),
            internalDays = internalDays.map { it.toStored() }
        )

        // 3. Obliczenia pomocnicze
        val title = dest.displayName
        val startDate = prefs.startDateMillis
        val endDate = startDate?.let { start ->
            start + (prefs.days.toLong() * 24L * 60L * 60L * 1000L)
        }

        // 4. Zapis fizyczny
        PlanStorage.upsertPlan(
            context = context,
            uid = uid,
            plan = storedPlan,
            title = title,
            startDateMillis = startDate,
            endDateMillis = endDate,
            updatedAtMillis = updatedAtMillis // Używamy przekazanego czasu
        )

        return storedPlan
    }

    // --- Extensions (bez zmian) ---

    private fun Preferences.toStored() = StoredPreferences(
        budget = this.budget,
        days = this.days,
        climate = this.climate,
        region = this.region,
        style = this.style,
        startDateMillis = this.startDateMillis,
        endDateMillis = this.endDateMillis
    )

    private fun Destination.toStored() = StoredDestination(
        id = this.id,
        displayName = this.displayName,
        country = this.country,
        region = this.region,
        climate = this.climate,
        minBudgetPerDay = this.minBudgetPerDay,
        typicalBudgetPerDay = this.typicalBudgetPerDay,
        tags = this.tags,
        apiQuery = this.apiQuery
    )

    private fun InternalDayPlan.toStored() = StoredInternalDayPlan(
        day = this.day,
        morning = this.morning?.toStored() ?: StoredSlotPlan(),
        midday = this.midday?.toStored() ?: StoredSlotPlan(),
        evening = this.evening?.toStored() ?: StoredSlotPlan()
    )

    private fun SlotPlan.toStored() = StoredSlotPlan(
        baseActivityId = this.baseActivityId,
        title = this.title,
        description = this.description,
        indoor = this.indoor
    )
}