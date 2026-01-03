package com.example.wakacje1.domain.model

import com.example.wakacje1.domain.model.SlotPlan

data class InternalDayPlan(
    val day: Int,
    val morning: SlotPlan,
    val midday: SlotPlan,
    val evening: SlotPlan
)