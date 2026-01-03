package com.example.wakacje1.data.local

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "local_plans",
    primaryKeys = ["uid", "id"],
    indices = [
        Index(value = ["uid", "updatedAtMillis"])
    ]
)
data class LocalPlanEntity(
    val uid: String,
    val id: String,
    val title: String,
    val startDateMillis: Long?,
    val endDateMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val payloadJson: String
)
