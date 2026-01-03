package com.example.wakacje1.presentation.common

sealed class UiEvent {
    data class Message(val text: String) : UiEvent()
    data class Error(val error: AppError) : UiEvent()
}
