package com.example.wakacje1.data.remote

data class CloudPlanRow(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
)
