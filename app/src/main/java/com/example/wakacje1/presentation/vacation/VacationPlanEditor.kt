package com.example.wakacje1.presentation.vacation

import com.example.wakacje1.domain.engine.PlanGenerator
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
import com.example.wakacje1.domain.usecase.GeneratePlanUseCase
import com.example.wakacje1.domain.usecase.RegenerateDayUseCase
import com.example.wakacje1.domain.usecase.RollNewActivityUseCase

data class PlanEditResult(
    val internalPlan: List<InternalDayPlan>,
    val uiPlan: List<DayPlan>
)

/**
 * Logika generowania i edycji planu wycieczki.
 * Cel: przenieść ciężar z VacationViewModel do osobnej klasy (delegacja).
 */
class VacationPlanEditor(
    private val generatePlanUseCase: GeneratePlanUseCase,
    private val regenerateDayUseCase: RegenerateDayUseCase,
    private val rollNewActivityUseCase: RollNewActivityUseCase,
    private val planGenerator: PlanGenerator
) {

    // Session Blacklist (zapobiega ponownemu losowaniu odrzuconych atrakcji)
    private val ignoredActivityIds = mutableSetOf<String>()

    fun resetSession() {
        ignoredActivityIds.clear()
    }

    suspend fun generatePlan(
        prefs: Preferences,
        dest: Destination,
        transportCost: Int,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): PlanEditResult {
        resetSession()
        val internal = generatePlanUseCase.execute(
            prefs = prefs,
            dest = dest,
            transportCost = transportCost,
            isBadWeatherForDayIndex = isBadWeatherForDayIndex
        )
        val uiPlan = planGenerator.rebuildDayPlans(internal, dest.displayName)
        return PlanEditResult(internalPlan = internal, uiPlan = uiPlan)
    }

    suspend fun regenerateDay(
        dayIndex: Int,
        prefs: Preferences,
        dest: Destination,
        transportCost: Int,
        currentInternal: List<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): PlanEditResult {
        val internal = currentInternal.toMutableList()
        if (internal.isEmpty()) return PlanEditResult(emptyList(), emptyList())

        regenerateDayUseCase.execute(
            dayIndex = dayIndex,
            prefs = prefs,
            dest = dest,
            transportCost = transportCost,
            internal = internal,
            isBadWeatherForDayIndex = isBadWeatherForDayIndex
        )

        val uiPlan = planGenerator.rebuildDayPlans(internal, dest.displayName)
        return PlanEditResult(internalPlan = internal, uiPlan = uiPlan)
    }

    suspend fun rollNewActivity(
        dayIndex: Int,
        slot: DaySlot,
        prefs: Preferences,
        dest: Destination,
        transportCost: Int,
        currentInternal: List<InternalDayPlan>,
        isBadWeatherForDayIndex: (Int) -> Boolean
    ): PlanEditResult {
        val internal = currentInternal.toMutableList()
        if (internal.isEmpty()) return PlanEditResult(emptyList(), emptyList())

        // Dodajemy usuwaną atrakcję do Czarnej Listy
        val currentSlotPlan = getSlotOrNull(internal, dayIndex, slot)
        currentSlotPlan?.baseActivityId?.let { ignoredActivityIds.add(it) }

        rollNewActivityUseCase.execute(
            dayIndex = dayIndex,
            slot = slot,
            prefs = prefs,
            dest = dest,
            transportCost = transportCost,
            internal = internal,
            isBadWeatherForDayIndex = isBadWeatherForDayIndex,
            ignoredIds = ignoredActivityIds
        )

        val uiPlan = planGenerator.rebuildDayPlans(internal, dest.displayName)
        return PlanEditResult(internalPlan = internal, uiPlan = uiPlan)
    }

    fun setCustomActivity(
        dayIndex: Int,
        slot: DaySlot,
        title: String,
        description: String,
        currentInternal: List<InternalDayPlan>,
        dest: Destination
    ): PlanEditResult {
        val internal = currentInternal.toMutableList()
        if (internal.isEmpty()) return PlanEditResult(emptyList(), emptyList())

        planGenerator.setCustomSlot(dayIndex, slot, title, description, internal)
        val uiPlan = planGenerator.rebuildDayPlans(internal, dest.displayName)
        return PlanEditResult(internalPlan = internal, uiPlan = uiPlan)
    }

    fun moveDayUp(
        index: Int,
        currentInternal: List<InternalDayPlan>,
        dest: Destination?
    ): PlanEditResult {
        val internal = currentInternal.toMutableList()
        if (index <= 0 || index >= internal.size) {
            val uiPlan = dest?.let { planGenerator.rebuildDayPlans(internal, it.displayName) } ?: emptyList()
            return PlanEditResult(internalPlan = internal, uiPlan = uiPlan)
        }

        val tmp = internal[index - 1]
        internal[index - 1] = internal[index]
        internal[index] = tmp

        val renumbered = internal.mapIndexed { i, dayPlan -> dayPlan.copy(day = i + 1) }
        val uiPlan = dest?.let { planGenerator.rebuildDayPlans(renumbered, it.displayName) } ?: emptyList()
        return PlanEditResult(internalPlan = renumbered, uiPlan = uiPlan)
    }

    fun moveDayDown(
        index: Int,
        currentInternal: List<InternalDayPlan>,
        dest: Destination?
    ): PlanEditResult {
        val internal = currentInternal.toMutableList()
        if (index < 0 || index >= internal.size - 1) {
            val uiPlan = dest?.let { planGenerator.rebuildDayPlans(internal, it.displayName) } ?: emptyList()
            return PlanEditResult(internalPlan = internal, uiPlan = uiPlan)
        }

        val tmp = internal[index + 1]
        internal[index + 1] = internal[index]
        internal[index] = tmp

        val renumbered = internal.mapIndexed { i, dayPlan -> dayPlan.copy(day = i + 1) }
        val uiPlan = dest?.let { planGenerator.rebuildDayPlans(renumbered, it.displayName) } ?: emptyList()
        return PlanEditResult(internalPlan = renumbered, uiPlan = uiPlan)
    }

    private fun getSlotOrNull(internal: List<InternalDayPlan>, dayIndex: Int, slot: DaySlot): SlotPlan? {
        val d = internal.getOrNull(dayIndex) ?: return null
        return when (slot) {
            DaySlot.MORNING -> d.morning
            DaySlot.MIDDAY -> d.midday
            DaySlot.EVENING -> d.evening
        }
    }
}
