package com.example.wakacje1.data.model


import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DestinationRepository(private val context: Context) {

    // Cache w pamięci, żeby nie czytać pliku za każdym razem
    private val cachedDestinations: List<Destination> by lazy { loadDestinationsFromAssets() }

    fun getAllDestinations(): List<Destination> = cachedDestinations

    private fun loadDestinationsFromAssets(): List<Destination> {
        val inputStream = context.assets.open("destinations.json")
        val jsonStr = inputStream.bufferedReader().use { it.readText() }

        val arr = JSONArray(jsonStr)
        val result = mutableListOf<Destination>()

        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            // tu zamiast `result += ...`
            result.add(parseDestination(obj))
        }

        return result
    }

    private fun parseDestination(obj: JSONObject): Destination {
        val tags = mutableListOf<String>()
        if (obj.has("tags")) {
            val tagsArr = obj.getJSONArray("tags")
            for (i in 0 until tagsArr.length()) {
                tags.add(tagsArr.getString(i))
            }
        }

        return Destination(
            id = obj.getString("id"),
            displayName = obj.getString("displayName"),
            country = obj.getString("country"),
            region = obj.getString("region"),
            climate = obj.getString("climate"),
            minBudgetPerDay = obj.getInt("minBudgetPerDay"),
            typicalBudgetPerDay = obj.getInt("typicalBudgetPerDay"),
            tags = tags,
            apiQuery = obj.getString("apiQuery")
        )
    }
}
