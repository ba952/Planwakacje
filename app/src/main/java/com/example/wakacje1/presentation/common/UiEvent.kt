package com.example.wakacje1.presentation.common

sealed interface UiEvent {
    // Komunikat (Toast/Snackbar) z obsługą R.string
    data class Message(val uiText: UiText) : UiEvent

    // Błąd (obsługiwany przez AppError)
    data class Error(val error: AppError) : UiEvent

    // [ZMIANA] Nowe zdarzenie: ViewModel wysyła gotowy HTML, Widok go drukuje
    data class PrintPdf(val html: String) : UiEvent
}