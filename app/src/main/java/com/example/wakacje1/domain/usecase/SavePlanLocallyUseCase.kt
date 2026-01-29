package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.remote.PlansCloudRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.engine.PlanGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SaveResult(
    val planId: String,
    val createdAtMillis: Long,
    val cloudError: Throwable? = null
)

class SavePlanLocallyUseCase(
    private val localRepository: PlansLocalRepository,
    private val cloudRepository: PlansCloudRepository
) {
    suspend fun execute(
        uid: String,
        prefs: Preferences,
        dest: Destination,
        internalDays: List<InternalDayPlan>,
        currentPlanId: String?,
        currentCreatedAtMillis: Long?
    ): SaveResult = withContext(Dispatchers.IO) {

        // ZMIANA: Generujemy czas aktualizacji tutaj, raz dla obu źródeł
        val now = System.currentTimeMillis()

        // 1. Zapisz lokalnie
        val stored = localRepository.upsertPlan(
            uid = uid,
            planId = currentPlanId,
            createdAtMillis = currentCreatedAtMillis,
            prefs = prefs,
            dest = dest,
            internalDays = internalDays,
            updatedAtMillis = now // Przekazujemy 'now'
        )

        var cloudErr: Throwable? = null

        // 2. Wyślij do chmury
        try {
            // Używamy zmiennej 'now' zamiast stored.updatedAtMillis
            cloudRepository.upsertPlan(uid, stored, now)
        } catch (e: Exception) {
            cloudErr = e
        }

        SaveResult(stored.id, stored.createdAtMillis, cloudErr)
    }
}