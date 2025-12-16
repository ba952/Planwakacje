package com.example.wakacje1.data

import android.content.Context
import com.example.wakacje1.data.model.Destination
import com.example.wakacje1.data.model.InternalDayPlan
import com.example.wakacje1.data.model.Preferences
import com.example.wakacje1.data.model.SlotPlan
import org.json.JSONArray
import org.json.JSONObject

data class StoredPreferences(
    val budget: Int,
    val days: Int,
    val climate: String,
    val region: String,
    val style: String,
    val startDateMillis: Long? = null,
    val endDateMillis: Long? = null
) {
    fun toPreferences() = Preferences(
        budget = budget,
        days = days,
        climate = climate,
        region = region,
        style = style,
        startDateMillis = startDateMillis,
        endDateMillis = endDateMillis
    )

    companion object {
        fun from(p: Preferences) = StoredPreferences(
            budget = p.budget,
            days = p.days,
            climate = p.climate,
            region = p.region,
            style = p.style,
            startDateMillis = p.startDateMillis,
            endDateMillis = p.endDateMillis
        )
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
    val tags: List<String> = emptyList(),
    val apiQuery: String
) {
    fun toDestination() = Destination(
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

    companion object {
        fun from(d: Destination) = StoredDestination(
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
    }
}

data class StoredSlotPlan(
    val baseActivityId: String?,
    val title: String,
    val description: String,
    val isCustom: Boolean
) {
    fun toSlotPlan() = SlotPlan(
        baseActivityId = baseActivityId,
        title = title,
        description = description,
        isCustom = isCustom
    )

    companion object {
        fun from(s: SlotPlan) = StoredSlotPlan(
            baseActivityId = s.baseActivityId,
            title = s.title,
            description = s.description,
            isCustom = s.isCustom
        )
    }
}

data class StoredInternalDayPlan(
    val title: String,
    val morning: StoredSlotPlan?,
    val midday: StoredSlotPlan?,
    val evening: StoredSlotPlan?,
    val budgetPerDay: Int
) {
    fun toInternalDayPlan() = InternalDayPlan(
        title = title,
        morning = morning?.toSlotPlan(),
        midday = midday?.toSlotPlan(),
        evening = evening?.toSlotPlan(),
        budgetPerDay = budgetPerDay
    )

    companion object {
        fun from(d: InternalDayPlan) = StoredInternalDayPlan(
            title = d.title,
            morning = d.morning?.let { StoredSlotPlan.from(it) },
            midday = d.midday?.let { StoredSlotPlan.from(it) },
            evening = d.evening?.let { StoredSlotPlan.from(it) },
            budgetPerDay = d.budgetPerDay
        )
    }
}

data class StoredPlan(
    val id: String,
    val createdAtMillis: Long,
    val destination: StoredDestination,
    val preferences: StoredPreferences?,
    val internalDays: List<StoredInternalDayPlan>
)

object PlanStorage {

    private const val FILE_NAME = "saved_plan.json"
    private const val VERSION = 2

    fun savePlan(context: Context, plan: StoredPlan) {
        val json = toJson(plan).toString()
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { out ->
            out.write(json.toByteArray())
        }
    }

    fun loadPlan(context: Context): StoredPlan? {
        return try {
            val content = context.openFileInput(FILE_NAME).bufferedReader().use { it.readText() }
            fromJson(JSONObject(content))
        } catch (_: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        context.deleteFile(FILE_NAME)
    }

    private fun toJson(plan: StoredPlan): JSONObject {
        val obj = JSONObject()
        obj.put("version", VERSION)
        obj.put("id", plan.id)
        obj.put("createdAtMillis", plan.createdAtMillis)

        obj.put("destination", destinationToJson(plan.destination))
        obj.put("preferences", plan.preferences?.let { prefsToJson(it) })

        val internalArr = JSONArray()
        plan.internalDays.forEach { internalArr.put(internalDayToJson(it)) }
        obj.put("internalDays", internalArr)

        return obj
    }

    private fun fromJson(obj: JSONObject): StoredPlan {
        val id = obj.optString("id", "plan")
        val createdAt = obj.optLong("createdAtMillis", 0L)

        val destination = destinationFromJson(obj.getJSONObject("destination"))
        val prefsObj = obj.optJSONObject("preferences")
        val prefs = prefsObj?.let { prefsFromJson(it) }

        val internalArr = obj.optJSONArray("internalDays") ?: JSONArray()
        val internal = mutableListOf<StoredInternalDayPlan>()
        for (i in 0 until internalArr.length()) {
            internal += internalDayFromJson(internalArr.getJSONObject(i))
        }

        return StoredPlan(
            id = id,
            createdAtMillis = createdAt,
            destination = destination,
            preferences = prefs,
            internalDays = internal
        )
    }

    private fun destinationToJson(d: StoredDestination): JSONObject =
        JSONObject().apply {
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

    private fun destinationFromJson(obj: JSONObject): StoredDestination {
        val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
        val tags = mutableListOf<String>()
        for (i in 0 until tagsArr.length()) tags += tagsArr.optString(i, "")
        return StoredDestination(
            id = obj.optString("id", "unknown"),
            displayName = obj.optString("displayName", "Nieznane miejsce"),
            country = obj.optString("country", ""),
            region = obj.optString("region", ""),
            climate = obj.optString("climate", ""),
            minBudgetPerDay = obj.optInt("minBudgetPerDay", 0),
            typicalBudgetPerDay = obj.optInt("typicalBudgetPerDay", 0),
            tags = tags.filter { it.isNotBlank() },
            apiQuery = obj.optString("apiQuery", "")
        )
    }

    private fun prefsToJson(p: StoredPreferences): JSONObject =
        JSONObject().apply {
            put("budget", p.budget)
            put("days", p.days)
            put("climate", p.climate)
            put("region", p.region)
            put("style", p.style)
            put("startDateMillis", p.startDateMillis)
            put("endDateMillis", p.endDateMillis)
        }

    private fun prefsFromJson(obj: JSONObject): StoredPreferences =
        StoredPreferences(
            budget = obj.optInt("budget", 0),
            days = obj.optInt("days", 1),
            climate = obj.optString("climate", ""),
            region = obj.optString("region", ""),
            style = obj.optString("style", ""),
            startDateMillis = if (obj.has("startDateMillis") && !obj.isNull("startDateMillis")) obj.optLong("startDateMillis") else null,
            endDateMillis = if (obj.has("endDateMillis") && !obj.isNull("endDateMillis")) obj.optLong("endDateMillis") else null
        )

    private fun internalDayToJson(d: StoredInternalDayPlan): JSONObject =
        JSONObject().apply {
            put("title", d.title)
            put("budgetPerDay", d.budgetPerDay)
            put("morning", d.morning?.let { slotToJson(it) })
            put("midday", d.midday?.let { slotToJson(it) })
            put("evening", d.evening?.let { slotToJson(it) })
        }

    private fun internalDayFromJson(obj: JSONObject): StoredInternalDayPlan =
        StoredInternalDayPlan(
            title = obj.optString("title", ""),
            morning = obj.optJSONObject("morning")?.let { slotFromJson(it) },
            midday = obj.optJSONObject("midday")?.let { slotFromJson(it) },
            evening = obj.optJSONObject("evening")?.let { slotFromJson(it) },
            budgetPerDay = obj.optInt("budgetPerDay", 0)
        )

    private fun slotToJson(s: StoredSlotPlan): JSONObject =
        JSONObject().apply {
            put("baseActivityId", s.baseActivityId)
            put("title", s.title)
            put("description", s.description)
            put("isCustom", s.isCustom)
        }

    private fun slotFromJson(obj: JSONObject): StoredSlotPlan =
        StoredSlotPlan(
            baseActivityId = if (obj.has("baseActivityId") && !obj.isNull("baseActivityId")) obj.optString("baseActivityId") else null,
            title = obj.optString("title", ""),
            description = obj.optString("description", ""),
            isCustom = obj.optBoolean("isCustom", false)
        )
}
