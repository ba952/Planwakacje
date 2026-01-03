package com.example.wakacje1.domain.model

data class SlotPlan(
    val baseActivityId: String? = null,
    val title: String = "",
    val description: String = "",
    val indoor: Boolean = false
)