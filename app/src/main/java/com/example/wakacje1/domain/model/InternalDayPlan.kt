package com.example.wakacje1.domain.model

data class InternalDayPlan(
    val day: Int,
    val morning: SlotPlan,
    val midday: SlotPlan,
    val evening: SlotPlan
)