package com.example.wakacje1.presentation.vacation

import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.session.SessionProvider
import com.example.wakacje1.domain.usecase.LoadLatestLocalPlanUseCase
import com.example.wakacje1.domain.usecase.SavePlanLocallyUseCase
import com.example.wakacje1.presentation.viewmodel.DayWeatherUi
import com.example.wakacje1.presentation.viewmodel.VacationUiState
import com.example.wakacje1.presentation.viewmodel.WeatherUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SaveLocalPlanResult {
    data object NoUser : SaveLocalPlanResult
    data object NothingToSave : SaveLocalPlanResult
    data class Success(
        val planId: String,
        val createdAtMillis: Long,
        val cloudError: Throwable?
    ) : SaveLocalPlanResult
    data class Failure(val error: Throwable) : SaveLocalPlanResult
}

sealed interface LoadLocalPlanResult {
    data object NoUser : LoadLocalPlanResult
    data object NoPlans : LoadLocalPlanResult
    data class Success(
        val newState: VacationUiState,
        val planId: String,
        val createdAtMillis: Long
    ) : LoadLocalPlanResult
    data class Failure(val error: Throwable) : LoadLocalPlanResult
}

/**
 * Logika zapisu/odczytu planu oraz trzymanie "ukrytego" stanu zapisu (planId/createdAt).
 * Cel: wyjąć persistence z VacationViewModel.
 */
class VacationPersistenceManager(
    private val sessionProvider: SessionProvider,
    private val savePlanLocallyUseCase: SavePlanLocallyUseCase,
    private val loadLatestLocalPlanUseCase: LoadLatestLocalPlanUseCase,
    private val planGenerator: PlanGenerator
) {

    private var currentPlanId: String? = null
    private var currentCreatedAtMillis: Long? = null

    fun resetCurrentPlan() {
        currentPlanId = null
        currentCreatedAtMillis = null
    }

    suspend fun savePlanLocally(uid: String? = null, state: VacationUiState): SaveLocalPlanResult {
        val realUid = uid ?: sessionProvider.currentUid()
        if (realUid.isNullOrBlank()) return SaveLocalPlanResult.NoUser

        val prefs = state.preferences ?: return SaveLocalPlanResult.NothingToSave
        val dest = state.chosenDestination ?: return SaveLocalPlanResult.NothingToSave
        val internalToSave = state.internalPlan
        if (internalToSave.isEmpty()) return SaveLocalPlanResult.NothingToSave

        return try {
            val res = savePlanLocallyUseCase.execute(
                realUid, prefs, dest, internalToSave, currentPlanId, currentCreatedAtMillis
            )
            currentPlanId = res.planId
            currentCreatedAtMillis = res.createdAtMillis

            SaveLocalPlanResult.Success(
                planId = res.planId,
                createdAtMillis = res.createdAtMillis,
                cloudError = res.cloudError
            )
        } catch (e: Exception) {
            SaveLocalPlanResult.Failure(e)
        }
    }

    suspend fun loadPlanLocally(uid: String? = null): LoadLocalPlanResult {
        val realUid = uid ?: sessionProvider.currentUid()
        if (realUid.isNullOrBlank()) return LoadLocalPlanResult.NoUser

        return try {
            val stored = withContext(Dispatchers.IO) { loadLatestLocalPlanUseCase.execute(realUid) }
            if (stored == null) return LoadLocalPlanResult.NoPlans

            val newState = applyStoredPlan(stored)
            LoadLocalPlanResult.Success(
                newState = newState,
                planId = stored.id,
                createdAtMillis = stored.createdAtMillis
            )
        } catch (e: Exception) {
            LoadLocalPlanResult.Failure(e)
        }
    }

    fun applyStoredPlan(stored: StoredPlan): VacationUiState {
        val loadedInternal = stored.internalDays.map { it.toInternalDayPlan() }

        currentPlanId = stored.id
        currentCreatedAtMillis = stored.createdAtMillis

        val uiPlan = planGenerator.rebuildDayPlans(loadedInternal, stored.destination.displayName)

        return VacationUiState(
            preferences = stored.preferences?.toPreferences(),
            chosenDestination = stored.destination.toDestination(),
            plan = uiPlan,
            internalPlan = loadedInternal,
            weather = WeatherUiState(),
            dayWeatherByDate = emptyMap<Long, DayWeatherUi>(),
            forecastNotice = null,
            canEditPlan = true,
            isLoading = false
        )
    }
}
