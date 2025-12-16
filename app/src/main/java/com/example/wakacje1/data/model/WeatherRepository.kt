package com.example.wakacje1.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

object WeatherRepository {

    private const val OPEN_WEATHER_API_KEY = "3a6edc24c47e52980c7c6adce42862b9"

    // ------------------ MODELE ------------------

    data class WeatherResult(
        val city: String,
        val temperature: Double,
        val description: String,
        val conditionId: Int
    )

    data class WeatherForecastDay(
        val dateMillis: Long,      // lokalna północ
        val tempMin: Double,
        val tempMax: Double,
        val description: String,
        val conditionId: Int,
        val isBadWeather: Boolean  // heurystyka: deszcz/śnieg/burza itp.
    )

    // ------------------ CACHE (in-memory) ------------------
    // TTL: bieżąca pogoda 10 min, prognoza 30 min
    private const val TTL_CURRENT_MS = 10L * 60 * 1000
    private const val TTL_FORECAST_MS = 30L * 60 * 1000

    private data class CacheEntry<T>(val timestampMs: Long, val value: T)

    private val currentCache = ConcurrentHashMap<String, CacheEntry<WeatherResult>>()
    private val forecastCache = ConcurrentHashMap<String, CacheEntry<List<WeatherForecastDay>>>()

    fun clearCache() {
        currentCache.clear()
        forecastCache.clear()
    }

    // ------------------ PUBLIC API ------------------

    suspend fun getWeatherForCity(cityQuery: String, forceRefresh: Boolean = false): WeatherResult =
        withContext(Dispatchers.IO) {
            val key = cityQuery.trim().lowercase()
            if (!forceRefresh) {
                val cached = currentCache[key]
                if (cached != null && (System.currentTimeMillis() - cached.timestampMs) < TTL_CURRENT_MS) {
                    return@withContext cached.value
                }
            }

            val url = buildCurrentUrl(cityQuery)
            val json = getJson(url)
            val parsed = parseCurrent(json)

            currentCache[key] = CacheEntry(System.currentTimeMillis(), parsed)
            parsed
        }

    suspend fun getForecastForCity(cityQuery: String, forceRefresh: Boolean = false): List<WeatherForecastDay> =
        withContext(Dispatchers.IO) {
            val key = cityQuery.trim().lowercase()
            if (!forceRefresh) {
                val cached = forecastCache[key]
                if (cached != null && (System.currentTimeMillis() - cached.timestampMs) < TTL_FORECAST_MS) {
                    return@withContext cached.value
                }
            }

            val url = buildForecastUrl(cityQuery)
            val json = getJson(url)
            val parsed = parseDailyForecast(json)

            forecastCache[key] = CacheEntry(System.currentTimeMillis(), parsed)
            parsed
        }

    // ------------------ URL-e ------------------

    private fun buildCurrentUrl(cityQuery: String): String {
        val encoded = URLEncoder.encode(cityQuery, "UTF-8")
        return "https://api.openweathermap.org/data/2.5/weather?q=$encoded&appid=$OPEN_WEATHER_API_KEY&units=metric&lang=pl"
    }

    private fun buildForecastUrl(cityQuery: String): String {
        val encoded = URLEncoder.encode(cityQuery, "UTF-8")
        return "https://api.openweathermap.org/data/2.5/forecast?q=$encoded&appid=$OPEN_WEATHER_API_KEY&units=metric&lang=pl"
    }

    // ------------------ HTTP JSON ------------------

    private fun getJson(urlString: String): JSONObject {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
        }

        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (code !in 200..299) {
                throw RuntimeException("Błąd API pogody: $code $body")
            }

            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    // ------------------ PARSOWANIE: bieżąca pogoda ------------------

    private fun parseCurrent(json: JSONObject): WeatherResult {
        val main = json.getJSONObject("main")
        val weatherArray = json.getJSONArray("weather")
        val weatherObj = weatherArray.getJSONObject(0)

        val cityName = json.optString("name", "")
        val temp = main.getDouble("temp")
        val desc = weatherObj.optString("description", "")
        val conditionId = weatherObj.optInt("id", 0)

        return WeatherResult(
            city = cityName,
            temperature = temp,
            description = desc,
            conditionId = conditionId
        )
    }

    // ------------------ PARSOWANIE: prognoza dzienna ------------------

    private data class Agg(
        var min: Double,
        var max: Double,
        var description: String,
        var conditionId: Int,
        var badCount: Int,
        var totalCount: Int
    )

    private fun parseDailyForecast(json: JSONObject): List<WeatherForecastDay> {
        // /forecast = lista co 3h -> grupujemy do dni
        val list = json.getJSONArray("list")
        val acc = mutableMapOf<Long, Agg>()

        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val dt = item.getLong("dt") // sekundy UTC
            val main = item.getJSONObject("main")
            val temp = main.getDouble("temp")

            val weatherArray = item.getJSONArray("weather")
            val weatherObj = weatherArray.getJSONObject(0)

            val desc = weatherObj.optString("description", "")
            val conditionId = weatherObj.optInt("id", 0)

            val millis = dt * 1000L
            val dayKey = normalizeToLocalMidnight(millis)

            val isBad = isBadWeather(conditionId, desc)

            val a = acc[dayKey]
            if (a == null) {
                acc[dayKey] = Agg(
                    min = temp,
                    max = temp,
                    description = desc,
                    conditionId = conditionId,
                    badCount = if (isBad) 1 else 0,
                    totalCount = 1
                )
            } else {
                if (temp < a.min) a.min = temp
                if (temp > a.max) a.max = temp
                a.totalCount += 1
                if (isBad) a.badCount += 1
                // description/conditionId zostawiamy jako "pierwsze z dnia"
            }
        }

        return acc.map { (dayMillis, agg) ->
            val badRatio = if (agg.totalCount == 0) 0.0 else (agg.badCount.toDouble() / agg.totalCount)
            WeatherForecastDay(
                dateMillis = dayMillis,
                tempMin = agg.min,
                tempMax = agg.max,
                description = agg.description,
                conditionId = agg.conditionId,
                isBadWeather = badRatio >= 0.35 // jeśli >=35% slotów dnia to zła pogoda
            )
        }.sortedBy { it.dateMillis }
    }

    private fun normalizeToLocalMidnight(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Heurystyka "złej pogody" na bazie kodów OpenWeather:
     * 2xx burze, 3xx mżawka, 5xx deszcz, 6xx śnieg, 7xx mgła/smog itp.
     */
    private fun isBadWeather(conditionId: Int, desc: String): Boolean {
        val group = conditionId / 100
        if (group in setOf(2, 3, 5, 6, 7)) return true

        // awaryjnie po opisie (gdyby conditionId było 0)
        val d = desc.lowercase()
        return d.contains("deszcz") || d.contains("burz") || d.contains("śnieg") || d.contains("mgł")
    }
}
