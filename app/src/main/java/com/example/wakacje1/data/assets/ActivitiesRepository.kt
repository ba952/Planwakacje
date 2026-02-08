package com.example.wakacje1.data.assets

import android.content.Context
import com.example.wakacje1.domain.assets.ActivityTemplate
import com.example.wakacje1.domain.assets.ActivityType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Repozytorium odpowiedzialne za dostęp do bazy aktywności (Atrakcji).
 * Działa w oparciu o pliki statyczne (assets) w formacie JSON.
 */
class ActivitiesRepository(
    private val context: Context
) : com.example.wakacje1.domain.assets.ActivitiesRepository {

    private val cachedActivities: List<ActivityTemplate> by lazy {
        loadActivitiesFromAssets()
    }

    override fun getAllActivities(): List<ActivityTemplate> = cachedActivities

    private fun loadActivitiesFromAssets(): List<ActivityTemplate> {
        val files = listOf("activities.json", "activitiesUnique.json")
        val map = LinkedHashMap<String, ActivityTemplate>()

        for (file in files) {
            val arr = runCatching { readArrayFromAssets(file) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val a = parseActivity(obj)
                map[a.id] = a
            }
        }
        return map.values.toList()
    }

    private fun readArrayFromAssets(fileName: String): JSONArray {
        val jsonStr = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return JSONArray(jsonStr)
    }

    private fun parseActivity(obj: JSONObject): ActivityTemplate {
        val id = obj.optString("id", "").trim().ifBlank { "unknown_${System.currentTimeMillis()}" }
        val title = obj.optString("title", "").trim().ifBlank { "Aktywność" }
        val description = obj.optString("description", "").trim()

        val typeStr = obj.optString("type", "CULTURE").trim()
        val type = try {
            ActivityType.valueOf(typeStr)
        } catch (_: IllegalArgumentException) {
            ActivityType.CULTURE
        }

        fun jsonArrayToSet(key: String): Set<String> {
            val array = obj.optJSONArray(key) ?: return emptySet()
            val res = mutableSetOf<String>()
            for (j in 0 until array.length()) {
                val v = array.optString(j, "").trim()
                if (v.isNotBlank()) res.add(v)
            }
            return res
        }

        val regions = jsonArrayToSet("suitableRegions")
        val styles = jsonArrayToSet("suitableStyles")
        val indoor = obj.optBoolean("indoor", false)
        val destinationId = obj.optString("destinationId", "").trim().ifBlank { null }

        return ActivityTemplate(
            id = id,
            title = title,
            description = description,
            type = type,
            suitableRegions = regions,
            suitableStyles = styles,
            indoor = indoor,
            destinationId = destinationId
        )
    }
}
