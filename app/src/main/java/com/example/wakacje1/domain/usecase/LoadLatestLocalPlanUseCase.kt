package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.local.PlansLocalRepository
import com.example.wakacje1.data.local.StoredPlan

/**
 * UseCase realizujący funkcję "Wznów pracę" (Resume).
 * Pobiera z lokalnej bazy danych plan o najświeższej dacie modyfikacji.
 */
class LoadLatestLocalPlanUseCase(
    private val localRepository: PlansLocalRepository
) {
    suspend fun execute(uid: String): StoredPlan? {
        return localRepository.loadLatestPlan(uid)
    }
}