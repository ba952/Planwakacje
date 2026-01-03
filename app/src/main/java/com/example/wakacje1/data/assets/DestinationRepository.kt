package com.example.wakacje1.data.assets

import android.content.Context
import com.example.wakacje1.domain.model.Destination
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class DestinationRepository(private val context: Context) {

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

    private fun parseDestinationSafe(obj: JSONObject): Destination {
        val displayName = obj.optString("displayName", "").trim().ifBlank { "Miejsce" }
        val country = obj.optString("country", "").trim()
        val region = obj.optString("region", "").trim()
        val climate = obj.optString("climate", "").trim()

        val idRaw = obj.optString("id", "").trim()
        val id = if (idRaw.isNotBlank()) idRaw else slug("${displayName}_${country}_${region}_${climate}")

        val minBudget = obj.optInt("minBudgetPerDay", 0)
        val typicalBudget = obj.optInt("typicalBudgetPerDay", minBudget)

        val tags = mutableListOf<String>()
        if (obj.has("tags")) {
            val tagsArr = obj.optJSONArray("tags") ?: JSONArray()
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.optString(i, "").trim()
                if (t.isNotBlank()) tags.add(t)
            }
        }

        val apiQuery = obj.optString("apiQuery", displayName).trim().ifBlank { displayName }

        // --- NEW: transport range ---
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

    private fun slug(s: String): String {
        val lower = s.lowercase(Locale.ROOT)
        return lower
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "dest_${System.currentTimeMillis()}" }
    }
}
