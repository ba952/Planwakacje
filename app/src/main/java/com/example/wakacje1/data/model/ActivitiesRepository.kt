package com.example.wakacje1.data.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typy aktywności wykorzystywane przy budowie planu dnia.
 */
enum class ActivityType {
    CULTURE,   // muzea, starówka, zabytki
    NATURE,    // góry, parki, widokowe spacery
    RELAX,     // plaża, spa, spokojny odpoczynek
    FOOD,      // jedzenie, degustacja lokalnej kuchni
    ACTIVE,    // intensywne aktywności (trekking, sport)
    NIGHT      // wieczorne / nocne wyjścia
}

/**
 * Szablon pojedynczej aktywności – wczytywany z pliku JSON.
 */
data class ActivityTemplate(
    val id: String,
    val title: String,
    val description: String,
    val type: ActivityType,
    val suitableRegions: Set<String>,
    val suitableStyles: Set<String>,
    val indoor: Boolean
)

/**
 * Repozytorium aktywności – ładuje listę z pliku JSON w assets i trzyma w pamięci.
 */
class ActivitiesRepository(private val context: Context) {

    // leniwe wczytanie tylko raz
    private val cachedActivities: List<ActivityTemplate> by lazy {
        loadActivitiesFromAssets()
    }

    fun getAllActivities(): List<ActivityTemplate> = cachedActivities

    // ------------------ prywatne ------------------

    private fun loadActivitiesFromAssets(): List<ActivityTemplate> {
        // UPEWNIJ SIĘ, że plik nazywa się "activities.json"
        val inputStream = context.assets.open("activities.json")
        val jsonStr = inputStream.bufferedReader().use { it.readText() }

        val arr = JSONArray(jsonStr)
        val result = mutableListOf<ActivityTemplate>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            // nawet jeśli jakiś obiekt jest lekko niekompletny, parser go "naprostuje"
            result.add(parseActivity(obj))
        }

        return result
    }

    private fun parseActivity(obj: JSONObject): ActivityTemplate {
        // Bezpieczne pobieranie id
        val id = obj.optString("id", "").ifBlank { "unknown_id" }

        // Bezpieczne pobranie title – jeśli brakuje, użyjemy domyślnej nazwy
        val rawTitle = obj.optString("title", "")
        val title = if (rawTitle.isBlank()) {
            "Aktywność"
        } else {
            rawTitle
        }

        val description = obj.optString("description", "")

        // Typ – jeśli w JSON jest literówka, fallback na CULTURE
        val typeStr = obj.optString("type", "CULTURE")
        val type = try {
            ActivityType.valueOf(typeStr)
        } catch (_: IllegalArgumentException) {
            ActivityType.CULTURE
        }

        fun jsonArrayToSet(key: String): Set<String> {
            if (!obj.has(key)) return emptySet()
            val array = obj.getJSONArray(key)
            val res = mutableSetOf<String>()
            for (j in 0 until array.length()) {
                val value = array.optString(j, "")
                if (value.isNotBlank()) {
                    res.add(value)
                }
            }
            return res
        }

        val regions = jsonArrayToSet("suitableRegions")
        val styles = jsonArrayToSet("suitableStyles")
        val indoor = obj.optBoolean("indoor", false)

        return ActivityTemplate(
            id = id,
            title = title,
            description = description,
            type = type,
            suitableRegions = regions,
            suitableStyles = styles,
            indoor = indoor
        )
    }
}
