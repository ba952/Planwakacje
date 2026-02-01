package com.example.wakacje1.data.assets

import android.content.Context
import com.example.wakacje1.R
import com.example.wakacje1.domain.model.Destination
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Repozytorium odczytujące dane o destynacjach z pliku JSON (assets).
 * Wykorzystuje cache w pamięci.
 */
class DestinationRepository(private val context: Context) {

    // Leniwe ładowanie (lazy) - dane są parsowane tylko raz przy pierwszym użyciu
    private val cachedDestinations: List<Destination> by lazy { loadDestinationsFromAssets() }

    fun getAllDestinations(): List<Destination> = cachedDestinations

    private fun loadDestinationsFromAssets(): List<Destination> {
        val inputStream = context.assets.open("destinations.json")
        val jsonStr = inputStream.bufferedReader().use { it.readText() }

        val arr = JSONArray(jsonStr)
        val result = mutableListOf<Destination>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(parseDestinationSafe(obj))
        }
        return result
    }

    /**
     * Bezpieczne parsowanie JSON - uzupełnia braki wartościami domyślnymi.
     */
    private fun parseDestinationSafe(obj: JSONObject): Destination {
        // Pobranie nazwy zastępczej z zasobów (gdy JSON jest uszkodzony)
        val fallbackName = context.getString(R.string.fallback_destination_name)
        val displayName = obj.optString("displayName", "").trim().ifBlank { fallbackName }

        val country = obj.optString("country", "").trim()
        val region = obj.optString("region", "").trim()
        val climate = obj.optString("climate", "").trim()

        // Generowanie technicznego ID, jeśli brakuje go w pliku
        val idRaw = obj.optString("id", "").trim()
        val id = idRaw.ifBlank { slug("${displayName}_${country}_${region}_${climate}") }

        val minBudget = obj.optInt("minBudgetPerDay", 0)
        val typicalBudget = obj.optInt("typicalBudgetPerDay", minBudget)

        // Parsowanie tagów
        val tags = mutableListOf<String>()
        if (obj.has("tags")) {
            val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.optString(i, "").trim()
                if (t.isNotBlank()) tags.add(t)
            }
        }

        val apiQuery = obj.optString("apiQuery", displayName).trim().ifBlank { displayName }

        // Parsowanie kosztów transportu (obsługa formatu zakresu lub min/max)
        val rangeArr = obj.optJSONArray("transportCostRoundTripPlnRange")
        val tMinRaw = when {
            rangeArr != null && rangeArr.length() > 0 -> rangeArr.optInt(0, 0)
            else -> obj.optInt("transportCostRoundTripPlnMin", 0)
        }
        val tMaxRaw = when {
            rangeArr != null && rangeArr.length() > 1 -> rangeArr.optInt(1, tMinRaw)
            else -> obj.optInt("transportCostRoundTripPlnMax", tMinRaw)
        }

        val tMin = tMinRaw.coerceAtLeast(0)
        val tMax = tMaxRaw.coerceAtLeast(tMin)

        return Destination(
            id = id,
            displayName = displayName,
            country = country,
            region = region,
            climate = climate,
            minBudgetPerDay = minBudget,
            typicalBudgetPerDay = typicalBudget,
            tags = tags,
            apiQuery = apiQuery,
            transportCostRoundTripPlnMin = tMin,
            transportCostRoundTripPlnMax = tMax
        )
    }

    /**
     * Helper tworzący bezpieczny identyfikator (slug) z nazwy.
     */
    private fun slug(s: String): String {
        val lower = s.lowercase(Locale.ROOT)
        return lower
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "dest_${System.currentTimeMillis()}" }
    }
}