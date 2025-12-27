package com.example.wakacje1.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException

object PlanStorage {

    private const val FILE_NAME = "saved_plan.json"

    fun savePlan(context: Context, plan: StoredPlan) {
        val json = planToJson(plan).toString()
        context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    fun loadPlan(context: Context): StoredPlan? {
        val json = try {
            context.openFileInput(FILE_NAME).use { input ->
                input.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (_: FileNotFoundException) {
            return null
        }

        if (json.isBlank()) return null
        return jsonToPlan(JSONObject(json))
    }

    fun clear(context: Context) {
        context.deleteFile(FILE_NAME)
    }

    // -------------------- JSON (encode) --------------------

    private fun planToJson(p: StoredPlan): JSONObject {
        val o = JSONObject()
        o.put("id", p.id)
        o.put("createdAtMillis", p.createdAtMillis)
        o.put("destination", destinationToJson(p.destination))

        // preferences mogą być null
        if (p.preferences != null) {
            o.put("preferences", preferencesToJson(p.preferences))
        } else {
            o.put("preferences", JSONObject.NULL)
        }

        val daysArr = JSONArray()
        p.internalDays.forEach { daysArr.put(internalDayToJson(it)) }
        o.put("internalDays", daysArr)

        return o
    }

    private fun destinationToJson(d: StoredDestination): JSONObject {
        val o = JSONObject()
        o.put("displayName", d.displayName)
        o.put("country", d.country)
        o.put("region", d.region)
        o.put("climate", d.climate)
        o.put("minBudgetPerDay", d.minBudgetPerDay)
        o.put("typicalBudgetPerDay", d.typicalBudgetPerDay)

        val tagsArr = JSONArray()
        d.tags.forEach { tagsArr.put(it) }
        o.put("tags", tagsArr)

        o.put("apiQuery", d.apiQuery)
        return o
    }

    private fun preferencesToJson(p: StoredPreferences): JSONObject {
        val o = JSONObject()
        o.put("region", p.region)
        o.put("climate", p.climate)
        o.put("style", p.style)
        o.put("budget", p.budget)
        o.put("days", p.days)

        // nullable
        if (p.startDateMillis != null) o.put("startDateMillis", p.startDateMillis)
        else o.put("startDateMillis", JSONObject.NULL)

        return o
    }

    private fun internalDayToJson(d: StoredInternalDayPlan): JSONObject {
        val o = JSONObject()
        o.put("day", d.day)
        o.put("morning", slotToJson(d.morning))
        o.put("midday", slotToJson(d.midday))
        o.put("evening", slotToJson(d.evening))
        return o
    }

    private fun slotToJson(s: StoredSlotPlan): JSONObject {
        val o = JSONObject()
        // baseActivityId może być null
        if (s.baseActivityId != null) o.put("baseActivityId", s.baseActivityId)
        else o.put("baseActivityId", JSONObject.NULL)

        o.put("title", s.title)
        o.put("description", s.description)
        o.put("indoor", s.indoor)
        return o
    }

    // -------------------- JSON (decode) --------------------

    private fun jsonToPlan(o: JSONObject): StoredPlan {
        val id = o.optString("id", "")
        val createdAtMillis = o.optLong("createdAtMillis", 0L)

        val destObj = o.optJSONObject("destination") ?: JSONObject()
        val destination = jsonToDestination(destObj)

        val preferences = run {
            val prefObj = o.optJSONObject("preferences")
            if (prefObj == null) null else jsonToPreferences(prefObj)
        }

        val internalDaysArr = o.optJSONArray("internalDays") ?: JSONArray()
        val internalDays = buildList {
            for (i in 0 until internalDaysArr.length()) {
                val dayObj = internalDaysArr.optJSONObject(i) ?: continue
                add(jsonToInternalDay(dayObj))
            }
        }

        return StoredPlan(
            id = id,
            createdAtMillis = createdAtMillis,
            destination = destination,
            preferences = preferences,
            internalDays = internalDays
        )
    }

    private fun jsonToDestination(o: JSONObject): StoredDestination {
        val tagsArr = o.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (i in 0 until tagsArr.length()) add(tagsArr.optString(i, ""))
        }.filter { it.isNotBlank() }

        return StoredDestination(
            displayName = o.optString("displayName", ""),
            country = o.optString("country", ""),
            region = o.optString("region", ""),
            climate = o.optString("climate", ""),
            minBudgetPerDay = o.optInt("minBudgetPerDay", 0),
            typicalBudgetPerDay = o.optInt("typicalBudgetPerDay", 0),
            tags = tags,
            apiQuery = o.optString("apiQuery", "")
        )
    }

    private fun jsonToPreferences(o: JSONObject): StoredPreferences {
        val startMillis = if (o.isNull("startDateMillis")) null else o.optLong("startDateMillis", 0L)

        return StoredPreferences(
            region = o.optString("region", ""),
            climate = o.optString("climate", ""),
            style = o.optString("style", ""),
            budget = o.optInt("budget", 0),
            days = o.optInt("days", 1),
            startDateMillis = startMillis
        )
    }

    private fun jsonToInternalDay(o: JSONObject): StoredInternalDayPlan {
        val morning = o.optJSONObject("morning")?.let { jsonToSlot(it) } ?: StoredSlotPlan()
        val midday = o.optJSONObject("midday")?.let { jsonToSlot(it) } ?: StoredSlotPlan()
        val evening = o.optJSONObject("evening")?.let { jsonToSlot(it) } ?: StoredSlotPlan()

        return StoredInternalDayPlan(
            day = o.optInt("day", 1),
            morning = morning,
            midday = midday,
            evening = evening
        )
    }

    private fun jsonToSlot(o: JSONObject): StoredSlotPlan {
        val baseId = if (o.isNull("baseActivityId")) null else o.optString("baseActivityId", null)

        return StoredSlotPlan(
            baseActivityId = baseId,
            title = o.optString("title", ""),
            description = o.optString("description", ""),
            indoor = o.optBoolean("indoor", false)
        )
    }
}
