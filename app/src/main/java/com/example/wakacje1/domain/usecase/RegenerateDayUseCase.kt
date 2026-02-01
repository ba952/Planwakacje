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
 * Zachowuje spójność wątkową (IO dla odczytu danych, Default dla logiki algorytmu).
 */
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
        // 1. Pobieranie surowych danych z assets (Context: IO)
        val allActivities = withContext(Dispatchers.IO) {
            activitiesRepository.getAllActivities()
        }

        // 2. Przeliczanie nowych slotów w silniku (Context: Default)
        withContext(Dispatchers.Default) {
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