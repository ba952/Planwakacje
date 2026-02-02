package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase koordynujący proces tworzenia nowego planu.
 * Pełni rolę orkiestratora: pobiera surowe dane (Repo) i deleguje logikę do silnika (Engine),
 * dbając o poprawny kontekst wielowątkowy.
 */
class GeneratePlanUseCase(
    private val activitiesRepository: ActivitiesRepository,
    private val planGenerator: PlanGenerator
) {
    suspend fun execute(
        prefs: Preferences,
        dest: Destination,
        transportCost: Int, // Parametr
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {
        val allActivities = withContext(Dispatchers.IO) {
            activitiesRepository.getAllActivities()
        }
        return withContext(Dispatchers.Default) {
            planGenerator.generateInternalPlan(
                prefs, dest, transportCost, allActivities, isBadWeatherForDayIndex
            )
        }
    }
}