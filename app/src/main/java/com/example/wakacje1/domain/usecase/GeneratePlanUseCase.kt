package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeneratePlanUseCase(
    private val activitiesRepository: ActivitiesRepository,
    private val planGenerator: PlanGenerator
) {
    /**
     * Pobiera aktywności z repozytorium (IO) i uruchamia generator (Default).
     * Zwraca gotową listę dni.
     */
    suspend fun execute(
        prefs: Preferences,
        dest: Destination,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {

        // 1. Pobranie danych (IO)
        val allActivities = withContext(Dispatchers.IO) {
            activitiesRepository.getAllActivities()
        }

        // 2. Generowanie planu (CPU / Default)
        return withContext(Dispatchers.Default) {
            planGenerator.generateInternalPlan(
                prefs = prefs,
                dest = dest,
                allActivities = allActivities,
                isBadWeatherForDayIndex = isBadWeatherForDayIndex
            )
        }
    }
}