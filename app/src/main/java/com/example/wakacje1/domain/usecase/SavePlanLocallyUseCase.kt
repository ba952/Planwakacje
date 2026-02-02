package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.remote.PlansCloudRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class SaveResult(
    val planId: String,
    val createdAtMillis: Long,
    val cloudError: Throwable? = null
)

/**
 * Offline-first:
 * 1) zapis lokalny zawsze kończy sukcesem (jeśli IO się uda)
 * 2) chmura jest best-effort i NIE MOŻE blokować UI (timeout)
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

        val now = System.currentTimeMillis()

        // 1) Lokalnie (krytyczne)
        val stored = localRepository.upsertPlan(
            uid = uid,
            planId = currentPlanId,
            createdAtMillis = currentCreatedAtMillis,
            prefs = prefs,
            dest = dest,
            internalDays = internalDays,
            updatedAtMillis = now
        )

        // 2) Chmura (best-effort, NIE BLOKUJE)
        var cloudErr: Throwable? = null
        try {
            // twardy limit – w offline Firestore potrafi wisieć na await()
            withTimeout(3_000) {
                cloudRepository.upsertPlan(uid, stored, now)
            }
        } catch (e: TimeoutCancellationException) {
            cloudErr = e // offline/timeout -> zapis lokalny i tak jest OK
        } catch (e: Exception) {
            cloudErr = e
        }

        SaveResult(stored.id, stored.createdAtMillis, cloudErr)
    }
}
