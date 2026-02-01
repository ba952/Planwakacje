package com.example.wakacje1.domain.model

/**
 * Wewnętrzny model domeny reprezentujący strukturę jednego dnia.
 * Przechowuje surowe dane aktywności w podziale na pory dnia (sloty).
 */
data class InternalDayPlan(
    val day: Int,
    val morning: SlotPlan,
    val midday: SlotPlan,
    val evening: SlotPlan
)