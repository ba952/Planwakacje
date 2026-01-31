package com.example.wakacje1.presentation.common

import com.example.wakacje1.domain.model.InternalDayPlan

sealed interface UiEvent {
    // ZMIANA 1: Typ pola to UiText (zamiast String), żeby obsługiwać R.string
    // ZMIANA 2: Nazwa pola to 'uiText' (zamiast 'msg'), żeby pasowało do kodu w PlanScreen
    data class Message(val uiText: UiText) : UiEvent

    // Error przechowuje obiekt AppError (który w środku ma już pole uiText)
    data class Error(val error: AppError) : UiEvent

    // Eksport PDF bez zmian
    data class ExportPdf(
        val destinationName: String,
        val tripStartDateMillis: Long?,
        val plan: List<InternalDayPlan>
    ) : UiEvent
}