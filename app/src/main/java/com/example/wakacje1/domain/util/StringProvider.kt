package com.example.wakacje1.domain.util

/**
 * Interfejs abstrahujący dostęp do zasobów tekstowych aplikacji (R.string).
 *
 * Cel architektoniczny:
 * 1. Uniezależnienie warstwy domeny (Domain) od frameworka Android (klasy Context).
 * 2. Umożliwienie testowania logiki biznesowej generującej teksty (Unit Tests) bez instrumentacji.
 */
interface StringProvider {
    fun getString(resId: Int, vararg args: Any): String
}