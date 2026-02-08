package com.example.wakacje1.data.remote

import com.example.wakacje1.BuildConfig
import com.example.wakacje1.data.CurrentWeatherResponse
import com.example.wakacje1.data.ForecastResponse
import com.example.wakacje1.data.WeatherApi
import com.example.wakacje1.domain.weather.CurrentWeather
import com.example.wakacje1.domain.weather.ForecastDay
import com.example.wakacje1.domain.weather.WeatherFailure
import com.example.wakacje1.domain.weather.WeatherResult
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

class WeatherRepository : com.example.wakacje1.domain.weather.WeatherRepository {

    // Cache TTL
    private val TTL_CURRENT_MS = 10L * 60 * 1000
    private val TTL_FORECAST_MS = 30L * 60 * 1000

    private data class CacheEntry<T>(val timestampMs: Long, val value: T)

    private val currentCache = ConcurrentHashMap<String, CacheEntry<CurrentWeather>>()
    private val forecastCache = ConcurrentHashMap<String, CacheEntry<List<ForecastDay>>>()

    fun clearCache() {
        currentCache.clear()
        forecastCache.clear()
    }

    private fun apiKeyOrNull(): String? {
        val k = BuildConfig.OPEN_WEATHER_API_KEY
        return k.takeIf { it.isNotBlank() }
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

    override suspend fun getCurrentWeather(
        cityQuery: String,
        forceRefresh: Boolean
    ): WeatherResult<CurrentWeather> = withContext(Dispatchers.IO) {

        val q = cityQuery.trim()
        if (q.isBlank()) return@withContext WeatherResult.Failure(WeatherFailure.NotFound)

        val key = q.lowercase()

        if (!forceRefresh) {
            val cached = currentCache[key]
            if (cached != null && (System.currentTimeMillis() - cached.timestampMs) < TTL_CURRENT_MS) {
                return@withContext WeatherResult.Success(cached.value)
            }
        }

        val apiKey = apiKeyOrNull()
            ?: return@withContext WeatherResult.Failure(WeatherFailure.Unauthorized)

        try {
            val res = api.getCurrentWeather(city = q, apiKey = apiKey)
            val parsed = mapCurrent(res)
            currentCache[key] = CacheEntry(System.currentTimeMillis(), parsed)
            WeatherResult.Success(parsed)
        } catch (e: HttpException) {
            WeatherResult.Failure(mapHttpFailure(e))
        } catch (e: IOException) {
            WeatherResult.Failure(WeatherFailure.Network)
        } catch (e: Exception) {
            WeatherResult.Failure(WeatherFailure.Unknown(e.message))
        }
    }

    override suspend fun getForecast(
        cityQuery: String,
        forceRefresh: Boolean
    ): WeatherResult<List<ForecastDay>> = withContext(Dispatchers.IO) {

        val q = cityQuery.trim()
        if (q.isBlank()) return@withContext WeatherResult.Failure(WeatherFailure.NotFound)

        val key = q.lowercase()

        if (!forceRefresh) {
            val cached = forecastCache[key]
            if (cached != null && (System.currentTimeMillis() - cached.timestampMs) < TTL_FORECAST_MS) {
                return@withContext WeatherResult.Success(cached.value)
            }
        }

        val apiKey = apiKeyOrNull()
            ?: return@withContext WeatherResult.Failure(WeatherFailure.Unauthorized)

        try {
            val res = api.getForecast(city = q, apiKey = apiKey)
            val parsed = mapForecastToDays(res)
            forecastCache[key] = CacheEntry(System.currentTimeMillis(), parsed)
            WeatherResult.Success(parsed)
        } catch (e: HttpException) {
            WeatherResult.Failure(mapHttpFailure(e))
        } catch (e: IOException) {
            WeatherResult.Failure(WeatherFailure.Network)
        } catch (e: Exception) {
            WeatherResult.Failure(WeatherFailure.Unknown(e.message))
        }
    }

    private fun mapHttpFailure(e: HttpException): WeatherFailure {
        return when (e.code()) {
            401 -> WeatherFailure.Unauthorized
            404 -> WeatherFailure.NotFound
            429 -> WeatherFailure.RateLimited
            else -> WeatherFailure.ApiError(e.code())
        }
    }

    private fun mapCurrent(res: CurrentWeatherResponse): CurrentWeather {
        val city = res.name?.takeIf { it.isNotBlank() } ?: ""
        val temp = res.main?.temp ?: 0.0
        val w0 = res.weather?.firstOrNull()
        val desc = w0?.description ?: ""
        val id = w0?.id ?: 0

        return CurrentWeather(
            city = city,
            temperature = temp,
            description = desc,
            conditionId = id
        )
    }

    private data class Agg(
        var min: Double,
        var max: Double,
        var description: String,
        var conditionId: Int,
        var badCount: Int,
        var totalCount: Int
    )

    private fun mapForecastToDays(res: ForecastResponse): List<ForecastDay> {
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
            ForecastDay(
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
}
