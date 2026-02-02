package com.example.wakacje1.domain.model

/**
 * Model widoku (View Model / DTO) reprezentujący plan jednego dnia wycieczki.
 *
 * - details: nadal zostaje (np. eksport PDF / prosty widok tekstowy).
 * - slots: NOWE – gotowe dane do UI per slot, żeby UI nie pytało ViewModelu o sloty.
 */
data class DayPlan(
    val day: Int,
    val title: String,
    val details: String,

    // ✅ MVVM: UI bierze sloty bezpośrednio z DayPlan (zamiast viewModel.getSlotOrNull)
    val slots: DaySlotsUi = DaySlotsUi()
)

/**
 * Sloty dnia w formie gotowej do wyświetlenia w UI.
 */
data class DaySlotsUi(
    val morning: SlotUi = SlotUi(),
    val midday: SlotUi = SlotUi(),
    val evening: SlotUi = SlotUi()
)

/**
 * Pojedynczy slot do UI.
 */
data class SlotUi(
    val title: String = "",
    val description: String = ""
)
