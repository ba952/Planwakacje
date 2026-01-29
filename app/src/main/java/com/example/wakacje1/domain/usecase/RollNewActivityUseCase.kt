package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.assets.ActivitiesRepository
import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        // NAPRAWA BŁĘDU: Jawnie określamy typ funkcji, żeby Kotlin wiedział co to jest
        isBadWeatherForDayIndex: (Int) -> Boolean
    ) {
        val allActivities = withContext(Dispatchers.IO) {
            activitiesRepository.getAllActivities()
        }

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