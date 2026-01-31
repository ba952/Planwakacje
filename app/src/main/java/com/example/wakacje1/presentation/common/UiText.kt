package com.example.wakacje1.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

sealed class UiText {
    // Wariant 1: Tekst dynamiczny (np. z API)
    data class DynamicString(val value: String) : UiText()

    // Wariant 2: Tekst z zasobów (R.string.xxx)
    class StringResource(
        @StringRes val resId: Int,
        vararg val args: Any
    ) : UiText()

    // Funkcja do wyciągania Stringa w zwykłym kodzie (np. Activity, Toast)
    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
    }

    // Funkcja do wyciągania Stringa w Composable (UI)
    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args)
        }
    }
}