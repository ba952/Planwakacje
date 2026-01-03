package com.example.wakacje1.data.local

import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.domain.model.SlotPlan
import org.json.JSONArray
import org.json.JSONObject

data class StoredPlan(
    val id: String,
    val createdAtMillis: Long,
    val destination: StoredDestination,
    val preferences: StoredPreferences?,
    val internalDays: List<StoredInternalDayPlan>
) {
    fun toJsonString(): String = toJson().toString()

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("createdAtMillis", createdAtMillis)
        obj.put("destination", destination.toJson())
        obj.put("preferences", preferences?.toJson())

        val daysArr = JSONArray()
        internalDays.forEach { daysArr.put(it.toJson()) }
        obj.put("internalDays", daysArr)

        return obj
    }

    companion object {
        fun fromJsonString(json: String): StoredPlan = fromJson(JSONObject(json))

        fun fromJson(obj: JSONObject): StoredPlan {
            val id = obj.optString("id", "")
            val createdAt = obj.optLong("createdAtMillis", System.currentTimeMillis())

            val destObj = obj.optJSONObject("destination") ?: JSONObject()
            val prefsObj = obj.optJSONObject("preferences")

            val days = mutableListOf<StoredInternalDayPlan>()
            val daysArr = obj.optJSONArray("internalDays") ?: JSONArray()
            for (i in 0 until daysArr.length()) {
                val dObj = daysArr.optJSONObject(i) ?: continue
                days.add(StoredInternalDayPlan.fromJson(dObj))
            }

            return StoredPlan(
                id = id,
                createdAtMillis = createdAt,
                destination = StoredDestination.fromJson(destObj),
                preferences = prefsObj?.let { StoredPreferences.fromJson(it) },
                internalDays = days
            )
        }
    }
}

data class StoredDestination(
    val id: String,
    val displayName: String,
    val country: String,
    val region: String,
    val climate: String,
    val minBudgetPerDay: Int,
    val typicalBudgetPerDay: Int,
    val tags: List<String>,
    val apiQuery: String
) {
    fun toDestination(): Destination = Destination(
        id = id,
        displayName = displayName,
        country = country,
        region = region,
        climate = climate,
        minBudgetPerDay = minBudgetPerDay,
        typicalBudgetPerDay = typicalBudgetPerDay,
        tags = tags,
        apiQuery = apiQuery
    )

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("displayName", displayName)
        obj.put("country", country)
        obj.put("region", region)
        obj.put("climate", climate)
        obj.put("minBudgetPerDay", minBudgetPerDay)
        obj.put("typicalBudgetPerDay", typicalBudgetPerDay)

        val arr = JSONArray()
        tags.forEach { arr.put(it) }
        obj.put("tags", arr)

        obj.put("apiQuery", apiQuery)
        return obj
    }

    companion object {
        fun from(d: Destination): StoredDestination = StoredDestination(
            id = d.id,
            displayName = d.displayName,
            country = d.country,
            region = d.region,
            climate = d.climate,
            minBudgetPerDay = d.minBudgetPerDay,
            typicalBudgetPerDay = d.typicalBudgetPerDay,
            tags = d.tags,
            apiQuery = d.apiQuery
        )

        fun fromJson(obj: JSONObject): StoredDestination {
            val tags = mutableListOf<String>()
            val arr = obj.optJSONArray("tags") ?: JSONArray()
            for (i in 0 until arr.length()) tags.add(arr.optString(i, ""))

            return StoredDestination(
                id = obj.optString("id", ""),
                displayName = obj.optString("displayName", ""),
                country = obj.optString("country", ""),
                region = obj.optString("region", ""),
                climate = obj.optString("climate", ""),
                minBudgetPerDay = obj.optInt("minBudgetPerDay", 0),
                typicalBudgetPerDay = obj.optInt("typicalBudgetPerDay", 0),
                tags = tags.filter { it.isNotBlank() },
                apiQuery = obj.optString("apiQuery", "")
            )
        }
    }
}

data class StoredPreferences(
    val budget: Int,
    val days: Int,
    val climate: String,
    val region: String,
    val style: String,
    val startDateMillis: Long?,
    val endDateMillis: Long?
) {
    fun toPreferences(): Preferences = Preferences(
        budget = budget,
        days = days,
        climate = climate,
        region = region,
        style = style,
        startDateMillis = startDateMillis,
        endDateMillis = endDateMillis
    )

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("budget", budget)
        obj.put("days", days)
        obj.put("climate", climate)
        obj.put("region", region)
        obj.put("style", style)
        obj.put("startDateMillis", startDateMillis)
        obj.put("endDateMillis", endDateMillis)
        return obj
    }

    companion object {
        fun from(p: Preferences): StoredPreferences = StoredPreferences(
            budget = p.budget,
            days = p.days,
            climate = p.climate,
            region = p.region,
            style = p.style,
            startDateMillis = p.startDateMillis,
            endDateMillis = p.endDateMillis
        )

        fun fromJson(obj: JSONObject): StoredPreferences = StoredPreferences(
            budget = obj.optInt("budget", 0),
            days = obj.optInt("days", 1),
            climate = obj.optString("climate", ""),
            region = obj.optString("region", ""),
            style = obj.optString("style", ""),
            startDateMillis = if (obj.isNull("startDateMillis")) null else obj.optLong("startDateMillis"),
            endDateMillis = if (obj.isNull("endDateMillis")) null else obj.optLong("endDateMillis")
        )
    }
}

data class StoredInternalDayPlan(
    val day: Int,
    val morning: StoredSlotPlan,
    val midday: StoredSlotPlan,
    val evening: StoredSlotPlan
) {
    fun toInternalDayPlan(): InternalDayPlan = InternalDayPlan(
        day = day,
        morning = morning.toSlotPlan(),
        midday = midday.toSlotPlan(),
        evening = evening.toSlotPlan()
    )

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("day", day)
        obj.put("morning", morning.toJson())
        obj.put("midday", midday.toJson())
        obj.put("evening", evening.toJson())
        return obj
    }

    companion object {
        fun from(d: InternalDayPlan): StoredInternalDayPlan = StoredInternalDayPlan(
            day = d.day,
            morning = StoredSlotPlan.from(d.morning),
            midday = StoredSlotPlan.from(d.midday),
            evening = StoredSlotPlan.from(d.evening)
        )

        fun fromJson(obj: JSONObject): StoredInternalDayPlan {
            return StoredInternalDayPlan(
                day = obj.optInt("day", 1),
                morning = StoredSlotPlan.fromJson(obj.optJSONObject("morning") ?: JSONObject()),
                midday = StoredSlotPlan.fromJson(obj.optJSONObject("midday") ?: JSONObject()),
                evening = StoredSlotPlan.fromJson(obj.optJSONObject("evening") ?: JSONObject())
            )
        }
    }
}

data class StoredSlotPlan(
    val baseActivityId: String? = null,
    val title: String = "",
    val description: String = "",
    val indoor: Boolean = false
) {
    fun toSlotPlan(): SlotPlan = SlotPlan(
        baseActivityId = baseActivityId,
        title = title,
        description = description,
        indoor = indoor
    )

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("baseActivityId", baseActivityId)
        obj.put("title", title)
        obj.put("description", description)
        obj.put("indoor", indoor)
        return obj
    }

    companion object {
        fun from(s: SlotPlan): StoredSlotPlan = StoredSlotPlan(
            baseActivityId = s.baseActivityId,
            title = s.title,
            description = s.description,
            indoor = s.indoor
        )

        fun fromJson(obj: JSONObject): StoredSlotPlan = StoredSlotPlan(
            baseActivityId = if (obj.isNull("baseActivityId")) null else obj.optString("baseActivityId", null),
            title = obj.optString("title", ""),
            description = obj.optString("description", ""),
            indoor = obj.optBoolean("indoor", false)
        )
    }
}
