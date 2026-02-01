package com.example.wakacje1.presentation.common

/**
 * Zdarzenia jednorazowe (One-Shot Events) wysyłane z ViewModelu do Widoku.
 * Obsługują akcje, które nie są trwałym stanem ekranu (np. Toasty, nawigacja).
 */
sealed interface UiEvent {
    // Wyświetlenie komunikatu (Toast/Snackbar)
    data class Message(val uiText: UiText) : UiEvent

    // Obsługa błędu (np. wyświetlenie dialogu)
    data class Error(val error: AppError) : UiEvent

    // Polecenie wydruku - przekazuje HTML do Activity, która ma dostęp do PrintManagera
    data class PrintPdf(val html: String) : UiEvent
}