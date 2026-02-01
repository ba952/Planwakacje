package com.example.wakacje1.domain.model

/**
 * Model pojedynczej aktywności w harmonogramie (np. "Spacer rano").
 */
data class SlotPlan(
    // ID szablonu z assets (null = aktywność własna użytkownika)
    val baseActivityId: String? = null,
    val title: String = "",
    val description: String = "",
    // Flaga określająca, czy atrakcja jest pod dachem (ważne przy deszczu)
    val indoor: Boolean = false
)