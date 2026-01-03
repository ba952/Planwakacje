package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeneratePlanUseCase(
    private val activitiesRepository: ActivitiesRepository
) {
    suspend fun execute(
        prefs: Preferences,
        dest: Destination,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): MutableList<InternalDayPlan> {
        val allActivities = withContext(Dispatchers.IO) {
            activitiesRepository.getAllActivities()
        }

        return withContext(Dispatchers.Default) {
            PlanGenerator.generateInternalPlan(
                prefs = prefs,
                dest = dest,
                allActivities = allActivities,
                isBadWeatherForDayIndex = isBadWeatherForDayIndex
            )
        }
    }
}
