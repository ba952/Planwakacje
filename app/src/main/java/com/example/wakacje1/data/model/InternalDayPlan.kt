package com.example.wakacje1.data.model

data class InternalDayPlan(
    val day: Int,
    val morning: SlotPlan,
    val midday: SlotPlan,
    val evening: SlotPlan
)
