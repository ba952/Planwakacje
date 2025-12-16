package com.example.wakacje1.data.model

data class Destination(
    val id: String,
    val displayName: String,
    val country: String,
    val region: String,
    val climate: String,
    val minBudgetPerDay: Int,
    val typicalBudgetPerDay: Int,
    val tags: List<String> = emptyList(),
    val apiQuery: String
)
