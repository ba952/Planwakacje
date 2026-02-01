package com.example.wakacje1.domain.model

/**
 * Model domeny reprezentujący destynację (miejsce wakacyjne).
 * Zawiera informacje geograficzne, budżetowe oraz metadane potrzebne do API pogodowego.
 * Jest to "czysty" obiekt Kotlin, niezależny od bazy danych czy formatu JSON.
 */
data class Destination(
    val id: String,
    val displayName: String,
    val country: String,
    val region: String,
    val climate: String,

    /**
     * Minimalny budżet dzienny (kieszonkowe) wymagany do "przeżycia" w tym miejscu
     * (jedzenie, tanie atrakcje), nie wliczając noclegu.
     */
    val minBudgetPerDay: Int,

    /**
     * Typowy budżet dzienny pozwalający na komfortowy wypoczynek.
     */
    val typicalBudgetPerDay: Int,

    val tags: List<String> = emptyList(),

    /**
     * Nazwa miasta (zapytanie) używana do komunikacji z zewnętrznym API pogodowym (OpenWeatherMap).
     * Czasami różni się od [displayName] (np. "Rome" vs "Rzym").
     */
    val apiQuery: String,

    // --- TRANSPORT (Round Trip) ---

    /** Minimalny szacowany koszt transportu (lot/dojazd) w obie strony (PLN). */
    val transportCostRoundTripPlnMin: Int = 0,

    /** Maksymalny szacowany koszt transportu w obie strony (PLN). */
    val transportCostRoundTripPlnMax: Int = 0
) {
    /**
     * Wyliczony średni koszt transportu.
     * Wykorzystywany przez algorytm sugestii w wariancie "Standardowym" (T_AVG).
     */
    val transportCostRoundTripPlnAvg: Int
        get() = ((transportCostRoundTripPlnMin + transportCostRoundTripPlnMax) / 2).coerceAtLeast(0)
}