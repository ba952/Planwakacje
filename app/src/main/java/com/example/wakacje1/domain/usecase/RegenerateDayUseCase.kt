package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RegenerateDayUseCase(
    private val activitiesRepository: ActivitiesRepository,
    private val planGenerator: PlanGenerator
) {
    suspend fun execute(
        dayIndex: Int,
        prefs: Preferences,
        dest: Destination,
        internal: MutableList<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ) {
        // 1. Pobieramy dane (IO)
        val allActivities = withContext(Dispatchers.IO) {
            activitiesRepository.getAllActivities()
        }

        // 2. Uruchamiamy logikę generatora (Default/CPU)
        withContext(Dispatchers.Default) {
            // POPRAWKA: Zmiana nazwy metody z regenerateWholeDay na regenerateDay
            planGenerator.regenerateDay(
                dayIndex = dayIndex,
                prefs = prefs,
                dest = dest,
                allActivities = allActivities,
                internal = internal,
                isBadWeatherForDayIndex = isBadWeatherForDayIndex
            )
        }
    }
}