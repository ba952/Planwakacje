package com.example.wakacje1.data.local

data class LocalPlanRow(
    val id: String,
    val title: String,
    val startDateMillis: Long?,
    val endDateMillis: Long?,
    val updatedAtMillis: Long
)
