package com.example.wakacje1.data.remote

import com.example.wakacje1.BuildConfig
import com.example.wakacje1.data.CurrentWeatherResponse
import com.example.wakacje1.data.ForecastResponse
import com.example.wakacje1.data.WeatherApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

// ZMIANA: class zamiast object
class WeatherRepository {

    // ------------------ MODELE (dla UI/ViewModel) ------------------

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
        val isBadWeather: Boolean
    )

    // ------------------ CACHE (in-memory) ------------------
    // Cache jest teraz per instancja (co jest OK, bo Koin trzyma singleton)
    private val TTL_CURRENT_MS = 10L * 60 * 1000
    private val TTL_FORECAST_MS = 30L * 60 * 1000

    private data class CacheEntry<T>(val timestampMs: Long, val value: T)

    private val currentCache = ConcurrentHashMap<String, CacheEntry<WeatherResult>>()
    private val forecastCache = ConcurrentHashMap<String, CacheEntry<List<WeatherForecastDay>>>()

    fun clearCache() {
        currentCache.clear()
        forecastCache.clear()
    }

    // ------------------ Retrofit ------------------

    private fun apiKey(): String {
        val k = BuildConfig.OPEN_WEATHER_API_KEY
        require(k.isNotBlank()) {
            "Brak OPEN_WEATHER_API_KEY. Dodaj go do local.properties (ROOT projektu) i zrób Sync."
        }
        return k
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    private val okHttp: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val api: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(WeatherApi::class.java)
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

            try {
                val res = api.getCurrentWeather(city = cityQuery, apiKey = apiKey())
                val parsed = mapCurrent(res)
                currentCache[key] = CacheEntry(System.currentTimeMillis(), parsed)
                parsed
            } catch (e: HttpException) {
                throw RuntimeException(friendlyHttpError("pogody", e))
            } catch (_: IOException) {
                throw RuntimeException("Brak połączenia z internetem / timeout podczas pobierania pogody.")
            }
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

            try {
                val res = api.getForecast(city = cityQuery, apiKey = apiKey())
                val parsed = mapForecastToDays(res)
                forecastCache[key] = CacheEntry(System.currentTimeMillis(), parsed)
                parsed
            } catch (e: HttpException) {
                throw RuntimeException(friendlyHttpError("prognozy", e))
            } catch (_: IOException) {
                throw RuntimeException("Brak połączenia z internetem / timeout podczas pobierania prognozy.")
            }
        }

    // ------------------ MAPOWANIE: bieżąca pogoda ------------------

    private fun mapCurrent(res: CurrentWeatherResponse): WeatherResult {
        val city = res.name?.takeIf { it.isNotBlank() } ?: "—"
        val temp = res.main?.temp ?: 0.0
        val w0 = res.weather?.firstOrNull()
        val desc = w0?.description ?: ""
        val id = w0?.id ?: 0

        return WeatherResult(
            city = city,
            temperature = temp,
            description = desc,
            conditionId = id
        )
    }

    // ------------------ MAPOWANIE: prognoza dzienna ------------------

    private data class Agg(
        var min: Double,
        var max: Double,
        var description: String,
        var conditionId: Int,
        var badCount: Int,
        var totalCount: Int
    )

    private fun mapForecastToDays(res: ForecastResponse): List<WeatherForecastDay> {
        val items = res.list.orEmpty()
        val acc = mutableMapOf<Long, Agg>()

        for (it in items) {
            val dtSec = it.dt ?: continue
            val temp = it.main?.temp ?: continue
            val w0 = it.weather?.firstOrNull()

            val desc = w0?.description ?: ""
            val conditionId = w0?.id ?: 0

            val millis = dtSec * 1000L
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
                isBadWeather = badRatio >= 0.35
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

    private fun isBadWeather(conditionId: Int, desc: String): Boolean {
        val group = conditionId / 100
        if (group in setOf(2, 3, 5, 6, 7)) return true
        val d = desc.lowercase()
        return d.contains("deszcz") || d.contains("burz") || d.contains("śnieg") || d.contains("mgł")
    }

    private fun friendlyHttpError(what: String, e: HttpException): String {
        return when (e.code()) {
            401 -> "Błąd autoryzacji ($what). Sprawdź klucz OPEN_WEATHER_API_KEY."
            404 -> "Nie znaleziono miasta ($what). Sprawdź nazwę/format zapytania."
            else -> "Błąd API $what: HTTP ${e.code()}."
        }
    }
}