package com.example.wakacje1.presentation.common

import com.example.wakacje1.domain.model.DayPlan

sealed interface UiEvent {
    // Zdarzenie informacyjne (np. "Zapisano plan")
    data class Message(val msg: String) : UiEvent

    // ZMIANA: Error przyjmuje teraz obiekt AppError (nie String!)
    // Dzięki temu w UI (Activity/Compose) możesz zdecydować, czy pokazać Toast, Dialog, czy Snackbar
    data class Error(val error: AppError) : UiEvent

    // Zdarzenie zlecające eksport PDF (odbierane przez Activity/Fragment)
    data class ExportPdf(
        val destinationName: String,
        val tripStartDateMillis: Long?,
        val plan: List<com.example.wakacje1.domain.model.InternalDayPlan> // <-- ZMIANA TYPU
    ) : UiEvent
}