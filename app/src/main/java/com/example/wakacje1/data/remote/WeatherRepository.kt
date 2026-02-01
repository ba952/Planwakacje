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

/**
 * Domenowe wyjątki pogodowe.
 * Pozwalają warstwie UI reagować na konkretne błędy (np. złe miasto)
 * bez wiedzy o bibliotece Retrofit/OkHttp.
 */
sealed class WeatherException(cause: Throwable? = null) : IOException(cause) {
    class NetworkError(cause: Throwable) : WeatherException(cause)
    class CityNotFound : WeatherException()
    class InvalidApiKey : WeatherException()
    class ApiError(val code: Int) : WeatherException()
    class Unknown(cause: Throwable) : WeatherException(cause)
}

/**
 * Repozytorium danych pogodowych.
 * Odpowiada za pobieranie danych z OpenWeatherMap, ich mapowanie na modele domenowe
 * oraz obsługę cache'owania w pamięci (RAM).
 */
class WeatherRepository {

    // ------------------ MODELE DOMENOWE ------------------

    data class WeatherResult(
        val city: String,
        val temperature: Double,
        val description: String,
        val conditionId: Int
    )

    data class WeatherForecastDay(
        val dateMillis: Long,
        val tempMin: Double,
        val tempMax: Double,
        val description: String,
        val conditionId: Int,
        val isBadWeather: Boolean
    )

    // ------------------ CACHE (In-Memory) ------------------
    // Krótki TTL dla bieżącej pogody (10 min), dłuższy dla prognozy (30 min)
    private val TTL_CURRENT_MS = 10L * 60 * 1000
    private val TTL_FORECAST_MS = 30L * 60 * 1000

    private data class CacheEntry<T>(val timestampMs: Long, val value: T)

    // ConcurrentHashMap zapewnia bezpieczeństwo wątkowe przy dostępie z wielu korutyn
    private val currentCache = ConcurrentHashMap<String, CacheEntry<WeatherResult>>()
    private val forecastCache = ConcurrentHashMap<String, CacheEntry<List<WeatherForecastDay>>>()

    fun clearCache() {
        currentCache.clear()
        forecastCache.clear()
    }

    // ------------------ KONFIGURACJA SIECIOWA ------------------

    private fun apiKey(): String {
        val k = BuildConfig.OPEN_WEATHER_API_KEY
        // Walidacja konfiguracji builda (fail-fast)
        require(k.isNotBlank()) {
            "Missing OPEN_WEATHER_API_KEY. Add it to local.properties."
        }
        return k
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
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

    /**
     * Pobiera aktualną pogodę.
     * Logika: Cache -> API -> Mapowanie -> Obsługa Błędów.
     */
    suspend fun getWeatherForCity(cityQuery: String, forceRefresh: Boolean = false): WeatherResult =
        withContext(Dispatchers.IO) {
            val key = cityQuery.trim().lowercase()

            // 1. Sprawdzenie Cache
            if (!forceRefresh) {
                val cached = currentCache[key]
                if (cached != null && (System.currentTimeMillis() - cached.timestampMs) < TTL_CURRENT_MS) {
                    return@withContext cached.value
                }
            }

            // 2. Pobranie z API i obsługa błędów
            try {
                val res = api.getCurrentWeather(city = cityQuery, apiKey = apiKey())
                val parsed = mapCurrent(res)
                currentCache[key] = CacheEntry(System.currentTimeMillis(), parsed)
                parsed
            } catch (e: HttpException) {
                throw mapHttpException(e)
            } catch (e: IOException) {
                throw WeatherException.NetworkError(e)
            } catch (e: Exception) {
                throw WeatherException.Unknown(e)
            }
        }

    /**
     * Pobiera prognozę długoterminową.
     */
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
                throw mapHttpException(e)
            } catch (e: IOException) {
                throw WeatherException.NetworkError(e)
            } catch (e: Exception) {
                throw WeatherException.Unknown(e)
            }
        }

    // Tłumaczenie kodów błędów HTTP na wyjątki domenowe
    private fun mapHttpException(e: HttpException): WeatherException {
        return when (e.code()) {
            401 -> WeatherException.InvalidApiKey()
            404 -> WeatherException.CityNotFound()
            else -> WeatherException.ApiError(e.code())
        }
    }

    // ------------------ MAPOWANIE DANYCH ------------------

    private fun mapCurrent(res: CurrentWeatherResponse): WeatherResult {
        val city = res.name?.takeIf { it.isNotBlank() } ?: ""
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

    // Struktura pomocnicza do agregacji (API zwraca dane co 3h)
    private data class Agg(
        var min: Double,
        var max: Double,
        var description: String,
        var conditionId: Int,
        var badCount: Int,
        var totalCount: Int
    )

    /**
     * Agreguje listę prognoz 3-godzinnych (API) do listy dni (UI).
     * Wyznacza min/max temperaturę dnia oraz decyduje, czy pogoda jest "zła".
     */
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

            // Logika agregacji (aktualizacja min/max)
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
            // Jeśli > 35% odczytów w ciągu dnia to zła pogoda, oznaczamy cały dzień jako zły.
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

    /**
     * Logika biznesowa oceny warunków atmosferycznych.
     * Sprawdza grupy kodów pogodowych (2xx - burze, 3xx/5xx - deszcz, 6xx - śnieg).
     */
    private fun isBadWeather(conditionId: Int, desc: String): Boolean {
        val group = conditionId / 100
        if (group in setOf(2, 3, 5, 6, 7)) return true
        val d = desc.lowercase()
        return d.contains("deszcz") || d.contains("burz") || d.contains("śnieg") || d.contains("mgł")
    }
}