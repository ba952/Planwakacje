package com.example.wakacje1.domain.model

data class Destination(
    val id: String,
    val displayName: String,
    val country: String,
    val region: String,
    val climate: String,
    val minBudgetPerDay: Int,
    val typicalBudgetPerDay: Int,
    val tags: List<String> = emptyList(),
    val apiQuery: String,

    // NEW: transport RT (min/max) — zgodne z JSON: transportCostRoundTripPlnRange [min,max]
    val transportCostRoundTripPlnMin: Int = 0,
    val transportCostRoundTripPlnMax: Int = 0
) {
    val transportCostRoundTripPlnAvg: Int
        get() = ((transportCostRoundTripPlnMin + transportCostRoundTripPlnMax) / 2).coerceAtLeast(0)
}
