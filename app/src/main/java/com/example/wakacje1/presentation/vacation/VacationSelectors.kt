package com.example.wakacje1.presentation.vacation

import com.example.wakacje1.R
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.SlotPlan
import com.example.wakacje1.domain.usecase.TransportPass
import com.example.wakacje1.presentation.common.UiText
import com.example.wakacje1.presentation.viewmodel.VacationUiState

/**
 * Zbiór prostych selektorów/obliczeń dla UI.
 * Cel: odchudzić VacationViewModel z "getterów" i kalkulatorów.
 */
object VacationSelectors {

    fun getInternalDayOrNull(state: VacationUiState, dayIndex: Int): InternalDayPlan? =
        state.internalPlan.getOrNull(dayIndex)

    fun getSlotOrNull(state: VacationUiState, dayIndex: Int, slot: DaySlot): SlotPlan? {
        val d = state.internalPlan.getOrNull(dayIndex) ?: return null
        return when (slot) {
            DaySlot.MORNING -> d.morning
            DaySlot.MIDDAY -> d.midday
            DaySlot.EVENING -> d.evening
        }
    }

    fun getTransportCostUsedForSuggestions(state: VacationUiState, d: Destination): Int {
        return when (state.lastTransportPass) {
            TransportPass.T_MAX -> d.transportCostRoundTripPlnMax
            TransportPass.T_AVG -> d.transportCostRoundTripPlnAvg
            TransportPass.T_MIN -> d.transportCostRoundTripPlnMin
        }
    }

    fun getTransportScenarioLabel(state: VacationUiState): UiText {
        return when (state.lastTransportPass) {
            TransportPass.T_MAX -> UiText.StringResource(R.string.transport_label_max)
            TransportPass.T_AVG -> UiText.StringResource(R.string.transport_label_avg)
            TransportPass.T_MIN -> UiText.StringResource(R.string.transport_label_min)
        }
    }

    fun getBudgetPerDayWithTransport(state: VacationUiState, d: Destination): Int {
        val prefs = state.preferences ?: return 0
        val days = prefs.days.coerceAtLeast(1)

        val budgetAfterSafety = prefs.budget * 0.9
        val t = getTransportCostUsedForSuggestions(state, d)
        val typicalDaily = d.typicalBudgetPerDay.toDouble().takeIf { it > 0.0 } ?: 450.0
        val hotelTotal = (typicalDaily * 0.45) * days

        val remaining = budgetAfterSafety - t - hotelTotal
        return if (remaining <= 0) 0 else (remaining / days).toInt()
    }
}
