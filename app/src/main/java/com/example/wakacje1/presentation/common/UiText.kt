package com.example.wakacje1.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Wrapper uniezależniający ViewModel od Contextu przy obsłudze tekstów.
 * Pozwala przekazywać zarówno surowe Stringi (z API), jak i ID zasobów (R.string).
 */
sealed class UiText {
    data class DynamicString(val value: String) : UiText()

    class StringResource(
        @StringRes val resId: Int,
        vararg val args: Any
    ) : UiText()

    // Pobranie tekstu w klasycznym systemie View / Context (np. Toast)
    fun asString(context: Context): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
        }
    }

    // Pobranie tekstu w Jetpack Compose
    @Composable
    fun asString(): String {
        return when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args)
        }
    }
}