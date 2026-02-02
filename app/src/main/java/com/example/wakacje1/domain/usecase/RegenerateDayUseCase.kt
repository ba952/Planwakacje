package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase odpowiedzialny za przelosowanie planu dla jednego, konkretnego dnia.
 * Reaguje na przycisk "Odśwież dzień" w UI.
 */
class RegenerateDayUseCase(
    private val activitiesRepository: ActivitiesRepository,
    private val planGenerator: PlanGenerator
) {
    suspend fun execute(
        dayIndex: Int,
        prefs: Preferences,
        dest: Destination,
        transportCost: Int, // Parametr
        internal: MutableList<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ) {
        val allActivities = withContext(Dispatchers.IO) {
            activitiesRepository.getAllActivities()
        }
        withContext(Dispatchers.Default) {
            planGenerator.regenerateDay(
                dayIndex, prefs, dest, transportCost, allActivities, internal, isBadWeatherForDayIndex
            )
        }
    }
}