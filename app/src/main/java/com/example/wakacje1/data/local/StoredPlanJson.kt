package com.example.wakacje1.data.local

import org.json.JSONArray
import org.json.JSONObject

object StoredPlanJson {

    fun toJson(plan: StoredPlan): String = toJsonObject(plan).toString()

    fun fromJson(json: String): StoredPlan {
        val obj = JSONObject(json)
        return fromJsonObject(obj)
    }

    private fun toJsonObject(p: StoredPlan): JSONObject {
        val o = JSONObject()
        o.put("id", p.id)
        o.put("createdAtMillis", p.createdAtMillis)
        o.put("destination", destinationToJson(p.destination))
        o.put("preferences", p.preferences?.let { prefsToJson(it) } ?: JSONObject.NULL)

        val daysArr = JSONArray()
        p.internalDays.forEach { daysArr.put(internalDayToJson(it)) }
        o.put("internalDays", daysArr)

        return o
    }

    private fun fromJsonObject(o: JSONObject): StoredPlan {
        val id = o.optString("id", "")
        val createdAt = o.optLong("createdAtMillis", 0L)

        val dest = destinationFromJson(o.getJSONObject("destination"))

        val prefs = if (o.isNull("preferences")) null else prefsFromJson(o.getJSONObject("preferences"))

        val internalDays = mutableListOf<StoredInternalDayPlan>()
        val arr = o.optJSONArray("internalDays") ?: JSONArray()
        for (i in 0 until arr.length()) {
            internalDays += internalDayFromJson(arr.getJSONObject(i))
        }

        return StoredPlan(
            id = id,
            createdAtMillis = createdAt,
            destination = dest,
            preferences = prefs,
            internalDays = internalDays
        )
    }

    private fun destinationToJson(d: StoredDestination): JSONObject = JSONObject().apply {
        put("id", d.id)
        put("displayName", d.displayName)
        put("country", d.country)
        put("region", d.region)
        put("climate", d.climate)
        put("minBudgetPerDay", d.minBudgetPerDay)
        put("typicalBudgetPerDay", d.typicalBudgetPerDay)
        put("apiQuery", d.apiQuery)

        val tagsArr = JSONArray()
        d.tags.forEach { tagsArr.put(it) }
        put("tags", tagsArr)
    }

    private fun destinationFromJson(o: JSONObject): StoredDestination {
        val tags = mutableListOf<String>()
        val tagsArr = o.optJSONArray("tags") ?: JSONArray()
        for (i in 0 until tagsArr.length()) tags += tagsArr.optString(i, "")

        return StoredDestination(
            id = o.optString("id", ""),
            displayName = o.optString("displayName", ""),
            country = o.optString("country", ""),
            region = o.optString("region", ""),
            climate = o.optString("climate", ""),
            minBudgetPerDay = o.optInt("minBudgetPerDay", 0),
            typicalBudgetPerDay = o.optInt("typicalBudgetPerDay", 0),
            tags = tags.filter { it.isNotBlank() },
            apiQuery = o.optString("apiQuery", "")
        )
    }

    private fun prefsToJson(p: StoredPreferences): JSONObject = JSONObject().apply {
        put("region", p.region)
        put("climate", p.climate)
        put("style", p.style)
        put("budget", p.budget)
        put("days", p.days)
        put("startDateMillis", p.startDateMillis?.let { it } ?: JSONObject.NULL)
        put("endDateMillis", p.endDateMillis?.let { it } ?: JSONObject.NULL)
    }

    private fun prefsFromJson(o: JSONObject): StoredPreferences {
        val start = if (o.isNull("startDateMillis")) null else o.optLong("startDateMillis")
        val end = if (o.isNull("endDateMillis")) null else o.optLong("endDateMillis")

        return StoredPreferences(
            region = o.optString("region", ""),
            climate = o.optString("climate", ""),
            style = o.optString("style", ""),
            budget = o.optInt("budget", 0),
            days = o.optInt("days", 1),
            startDateMillis = start,
            endDateMillis = end
        )
    }

    private fun internalDayToJson(d: StoredInternalDayPlan): JSONObject = JSONObject().apply {
        put("day", d.day)
        put("morning", slotToJson(d.morning))
        put("midday", slotToJson(d.midday))
        put("evening", slotToJson(d.evening))
    }

    private fun internalDayFromJson(o: JSONObject): StoredInternalDayPlan = StoredInternalDayPlan(
        day = o.optInt("day", 1),
        morning = slotFromJson(o.getJSONObject("morning")),
        midday = slotFromJson(o.getJSONObject("midday")),
        evening = slotFromJson(o.getJSONObject("evening"))
    )

    private fun slotToJson(s: StoredSlotPlan): JSONObject = JSONObject().apply {
        put("baseActivityId", s.baseActivityId ?: JSONObject.NULL)
        put("title", s.title)
        put("description", s.description)
        put("indoor", s.indoor)
    }

    private fun slotFromJson(o: JSONObject): StoredSlotPlan {
        val baseId = if (o.isNull("baseActivityId")) null else o.optString("baseActivityId", null)
        return StoredSlotPlan(
            baseActivityId = baseId,
            title = o.optString("title", ""),
            description = o.optString("description", ""),
            indoor = o.optBoolean("indoor", false)
        )
    }
}
