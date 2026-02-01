package com.example.wakacje1.data.assets

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Typ wyliczeniowy określający kategorię aktywności.
 * Wykorzystywany przez algorytm [PlanGenerator] do szacowania kosztów
 * oraz dobierania atrakcji do pory dnia (np. NIGHT -> Wieczór).
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


 // Model danych reprezentujący szablon aktywności wczytany z pliku JSON.

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

/**
 * Repozytorium odpowiedzialne za dostęp do bazy aktywności (Atrakcji).
 *
 * Działa w oparciu o pliki statyczne (assets) w formacie JSON.
 * Implementuje mechanizm leniwego ładowania (Lazy Loading), aby nie blokować
 * startu aplikacji operacjami I/O.
 */
class ActivitiesRepository(private val context: Context) {

    /**
     * Zcache'owana lista aktywności.
     * Inicjalizacja następuje dopiero przy pierwszym odwołaniu (by lazy).
     */
    private val cachedActivities: List<ActivityTemplate> by lazy {
        loadActivitiesFromAssets()
    }

    /**
     * Zwraca listę wszystkich dostępnych szablonów aktywności.
     */
    fun getAllActivities(): List<ActivityTemplate> = cachedActivities

    /**
     * Wczytuje i scala dane z dwóch źródeł JSON:
     * 1. activities.json - aktywności ogólne (generyczne).
     * 2. activitiesUnique.json - aktywności dedykowane dla konkretnych miast.
     *
     * Użycie LinkedHashMap pozwala na nadpisywanie aktywności o tym samym ID
     * (de-duplikacja) przy zachowaniu kolejności wstawiania.
     */
    private fun loadActivitiesFromAssets(): List<ActivityTemplate> {
        val files = listOf("activities.json", "activitiesUnique.json")
        val map = LinkedHashMap<String, ActivityTemplate>()

        for (file in files) {
            // runCatching zabezpiecza przed crashem w przypadku braku pliku lub błędu odczytu
            val arr = runCatching { readArrayFromAssets(file) }.getOrNull() ?: continue
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val a = parseActivity(obj)
                // Kluczem jest ID, co eliminuje duplikaty (ostatni wygrywa)
                map[a.id] = a
            }
        }

        return map.values.toList()
    }

    /**
     * Pomocnicza metoda wykonująca operacje I/O na strumieniu Assets.
     */
    private fun readArrayFromAssets(fileName: String): JSONArray {
        val jsonStr = context.assets.open(fileName).bufferedReader().use { it.readText() }
        return JSONArray(jsonStr)
    }

    /**
     * Parsuje obiekt JSON do modelu domeny [ActivityTemplate].
     * Zastosowano podejście "defensive parsing" - w przypadku braku pól
     * wstawiane są wartości domyślne, aby uniknąć wyjątków w Runtime.
     */
    private fun parseActivity(obj: JSONObject): ActivityTemplate {
        // Generowanie ID tymczasowego w przypadku braku w JSON (fallback)
        val id = obj.optString("id", "").trim().ifBlank { "unknown_${System.currentTimeMillis()}" }

        val title = obj.optString("title", "").trim().ifBlank { "Aktywność" }
        val description = obj.optString("description", "").trim()

        // Bezpieczne mapowanie String -> Enum
        val typeStr = obj.optString("type", "CULTURE").trim()
        val type = try {
            ActivityType.valueOf(typeStr)
        } catch (_: IllegalArgumentException) {
            ActivityType.CULTURE
        }

        // Funkcja lokalna do bezpiecznego parsowania tablic stringów z JSON
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