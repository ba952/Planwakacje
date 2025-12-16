package com.example.wakacje1.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Model do zapisu na dysk (warstwa data) – NIE używa UI-owych klas.
 */
data class StoredDayPlan(
    val day: Int,
    val title: String,
    val details: String
)

data class StoredPlan(
    val id: String,
    val createdAtMillis: Long,
    val placeName: String,
    val days: List<StoredDayPlan>
)

object PlanStorage {

    private const val FILE_NAME = "saved_plan.json"

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
        obj.put("id", plan.id)
        obj.put("createdAtMillis", plan.createdAtMillis)
        obj.put("placeName", plan.placeName)

        val arr = JSONArray()
        plan.days.forEach { d ->
            val dObj = JSONObject()
            dObj.put("day", d.day)
            dObj.put("title", d.title)
            dObj.put("details", d.details)
            arr.put(dObj)
        }
        obj.put("days", arr)
        return obj
    }

    private fun fromJson(obj: JSONObject): StoredPlan {
        val id = obj.optString("id", "plan")
        val createdAt = obj.optLong("createdAtMillis", 0L)
        val place = obj.optString("placeName", "")

        val daysArr = obj.optJSONArray("days") ?: JSONArray()
        val days = mutableListOf<StoredDayPlan>()
        for (i in 0 until daysArr.length()) {
            val dObj = daysArr.getJSONObject(i)
            days.add(
                StoredDayPlan(
                    day = dObj.optInt("day", i + 1),
                    title = dObj.optString("title", ""),
                    details = dObj.optString("details", "")
                )
            )
        }

        return StoredPlan(
            id = id,
            createdAtMillis = createdAt,
            placeName = place,
            days = days
        )
    }
}
