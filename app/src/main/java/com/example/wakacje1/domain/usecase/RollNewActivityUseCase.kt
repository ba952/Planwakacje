package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UseCase realizujący wymianę pojedynczej aktywności (np. "Zmień tylko poranek").
 * Pozwala na granularną edycję planu bez naruszania pozostałych slotów w danym dniu.
 */
class RollNewActivityUseCase(
    private val activitiesRepository: ActivitiesRepository,
    private val planGenerator: PlanGenerator
) {
    suspend fun execute(
        dayIndex: Int,
        slot: DaySlot,
        prefs: Preferences,
        dest: Destination,
        internal: MutableList<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ) {
        // 1. Pobranie bazy aktywności (I/O intensive)
        val allActivities = withContext(Dispatchers.IO) {
            activitiesRepository.getAllActivities()
        }

        // 2. Wybór nowej atrakcji w silniku (CPU intensive)
        withContext(Dispatchers.Default) {
            planGenerator.rollNewSlot(
                dayIndex = dayIndex,
                slot = slot,
                prefs = prefs,
                dest = dest,
                allActivities = allActivities,
                internal = internal,
                isBadWeatherForDayIndex = isBadWeatherForDayIndex
            )
        }
    }
}