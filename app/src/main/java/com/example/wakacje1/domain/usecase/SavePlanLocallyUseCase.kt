package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.remote.PlansCloudRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SaveResult(
    val planId: String,
    val createdAtMillis: Long,
    val cloudError: Throwable? = null
)

/**
 * UseCase realizujący strategię "Local-First" (Offline-First).
 * Gwarantuje natychmiastowy zapis lokalny, traktując synchronizację z chmurą
 * jako operację drugorzędną (Best-Effort), która nie może zablokować UI w razie braku sieci.
 */
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

        // Timestamp generowany raz dla spójności obu źródeł danych (Atomic-like logic)
        val now = System.currentTimeMillis()

        // 1. Zapis Lokalny (Source of Truth)
        // To jest operacja krytyczna - jeśli się nie uda, rzucamy wyjątek wyżej.
        val stored = localRepository.upsertPlan(
            uid = uid,
            planId = currentPlanId,
            createdAtMillis = currentCreatedAtMillis,
            prefs = prefs,
            dest = dest,
            internalDays = internalDays,
            updatedAtMillis = now
        )

        var cloudErr: Throwable? = null

        // 2. Synchronizacja z Chmurą (Fire-and-forget / Best-Effort)
        // Błąd sieci (np. offline) jest łapany i zwracany jako ostrzeżenie,
        // ale nie powoduje uznania całej operacji zapisu za nieudaną.
        try {
            cloudRepository.upsertPlan(uid, stored, now)
        } catch (e: Exception) {
            cloudErr = e
        }

        SaveResult(stored.id, stored.createdAtMillis, cloudErr)
    }
}