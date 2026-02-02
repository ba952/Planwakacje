package com.example.wakacje1.domain.session

/**
 * Abstrakcja sesji użytkownika.
 * ViewModel nie zna Firebase ani żadnego SDK.
 */
interface SessionProvider {
    fun currentUid(): String?
}
