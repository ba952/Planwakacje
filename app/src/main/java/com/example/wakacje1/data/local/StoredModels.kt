package com.example.wakacje1.data.local

import com.example.wakacje1.data.model.Destination
import com.example.wakacje1.data.model.InternalDayPlan
import com.example.wakacje1.data.model.Preferences
import com.example.wakacje1.data.model.SlotPlan

data class StoredPlan(
    val id: String = "",
    val createdAtMillis: Long = 0L,
    val destination: StoredDestination = StoredDestination(),
    val preferences: StoredPreferences? = null,
    val internalDays: List<StoredInternalDayPlan> = emptyList()
)

data class StoredDestination(
    val id: String = "",              // <- DODANE
    val displayName: String = "",
    val country: String = "",
    val region: String = "",
    val climate: String = "",
    val minBudgetPerDay: Int = 0,
    val typicalBudgetPerDay: Int = 0,
    val tags: List<String> = emptyList(),
    val apiQuery: String = ""
) {
    companion object {
        fun from(d: Destination): StoredDestination = StoredDestination(
            id = d.id,                 // <- DODANE
            displayName = d.displayName,
            country = d.country,
            region = d.region,
            climate = d.climate,
            minBudgetPerDay = d.minBudgetPerDay,
            typicalBudgetPerDay = d.typicalBudgetPerDay,
            tags = d.tags,
            apiQuery = d.apiQuery
        )
    }

    fun toDestination(): Destination = Destination(
        id = id,                       // <- DODANE (to naprawia błąd)
        displayName = displayName,
        country = country,
        region = region,
        climate = climate,
        minBudgetPerDay = minBudgetPerDay,
        typicalBudgetPerDay = typicalBudgetPerDay,
        tags = tags,
        apiQuery = apiQuery
    )
}

data class StoredPreferences(
    val region: String = "",
    val climate: String = "",
    val style: String = "",
    val budget: Int = 0,
    val days: Int = 1,
    val startDateMillis: Long? = null
) {
    companion object {
        fun from(p: Preferences): StoredPreferences = StoredPreferences(
            region = p.region,
            climate = p.climate,
            style = p.style,
            budget = p.budget,
            days = p.days,
            startDateMillis = p.startDateMillis
        )
    }

    fun toPreferences(): Preferences = Preferences(
        region = region,
        climate = climate,
        style = style,
        budget = budget,
        days = days,
        startDateMillis = startDateMillis
    )
}

data class StoredInternalDayPlan(
    val day: Int = 1,
    val morning: StoredSlotPlan = StoredSlotPlan(),
    val midday: StoredSlotPlan = StoredSlotPlan(),
    val evening: StoredSlotPlan = StoredSlotPlan()
) {
    companion object {
        fun from(dayIndex: Int, i: InternalDayPlan): StoredInternalDayPlan {
            val safeDay = if (i.day > 0) i.day else (dayIndex + 1)
            return StoredInternalDayPlan(
                day = safeDay,
                morning = StoredSlotPlan.from(i.morning),
                midday = StoredSlotPlan.from(i.midday),
                evening = StoredSlotPlan.from(i.evening)
            )
        }

        fun from(i: InternalDayPlan): StoredInternalDayPlan = StoredInternalDayPlan(
            day = i.day,
            morning = StoredSlotPlan.from(i.morning),
            midday = StoredSlotPlan.from(i.midday),
            evening = StoredSlotPlan.from(i.evening)
        )
    }

    fun toInternalDayPlan(): InternalDayPlan = InternalDayPlan(
        day = day,
        morning = morning.toSlotPlan(),
        midday = midday.toSlotPlan(),
        evening = evening.toSlotPlan()
    )
}

data class StoredSlotPlan(
    val baseActivityId: String? = null,
    val title: String = "",
    val description: String = "",
    val indoor: Boolean = false
) {
    companion object {
        fun from(s: SlotPlan): StoredSlotPlan = StoredSlotPlan(
            baseActivityId = s.baseActivityId,
            title = s.title,
            description = s.description,
            indoor = s.indoor
        )
    }

    fun toSlotPlan(): SlotPlan = SlotPlan(
        baseActivityId = baseActivityId,
        title = title,
        description = description,
        indoor = indoor
    )
}
