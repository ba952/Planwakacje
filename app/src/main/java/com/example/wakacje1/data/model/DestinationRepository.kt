package com.example.wakacje1.data.model

import android.content.Context
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
            result.add(parseDestination(obj, index = i))
        }

        return result
    }

    private fun parseDestination(obj: JSONObject, index: Int): Destination {
        val displayName = obj.optString("displayName", "").trim()
        val country = obj.optString("country", "").trim()

        val idFromJson = obj.optString("id", "").trim()
        val safeId = if (idFromJson.isNotBlank()) {
            idFromJson
        } else {
            // stabilne i czytelne id nawet jeśli JSON go nie ma
            val base = listOf(displayName, country)
                .filter { it.isNotBlank() }
                .joinToString("_")
                .ifBlank { "destination_$index" }
            slugify(base) + "_$index"
        }

        val tags = mutableListOf<String>()
        val tagsArr = obj.optJSONArray("tags")
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.optString(i, "").trim()
                if (t.isNotBlank()) tags.add(t)
            }
        }

        return Destination(
            id = safeId,
            displayName = displayName.ifBlank { "Miejsce ${index + 1}" },
            country = country,
            region = obj.optString("region", "").trim(),
            climate = obj.optString("climate", "").trim(),
            minBudgetPerDay = obj.optInt("minBudgetPerDay", 0),
            typicalBudgetPerDay = obj.optInt("typicalBudgetPerDay", 0),
            tags = tags,
            apiQuery = obj.optString("apiQuery", displayName).trim()
        )
    }

    private fun slugify(input: String): String {
        return input
            .lowercase(Locale.ROOT)
            .replace("ł", "l")
            .replace("ą", "a")
            .replace("ć", "c")
            .replace("ę", "e")
            .replace("ń", "n")
            .replace("ó", "o")
            .replace("ś", "s")
            .replace("ż", "z")
            .replace("ź", "z")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "destination" }
    }
}

