package com.example.wakacje1.domain.assets

import com.example.wakacje1.domain.model.Destination

/**
 * Modele domenowe dla "assets" + kontrakty repozytoriów.
 * Cel: domain NIE zna pakietu data.assets.
 */

enum class ActivityType {
    CULTURE,
    NATURE,
    RELAX,
    FOOD,
    ACTIVE,
    NIGHT,
    HISTORY
}

data class ActivityTemplate(
    val id: String,
    val title: String,
    val description: String,
    val type: ActivityType,
    val suitableRegions: Set<String>,
    val suitableStyles: Set<String>,
    val indoor: Boolean,
    val destinationId: String? = null
)

interface ActivitiesRepository {
    fun getAllActivities(): List<ActivityTemplate>
}

interface DestinationRepository {
    fun getAllDestinations(): List<Destination>
}
