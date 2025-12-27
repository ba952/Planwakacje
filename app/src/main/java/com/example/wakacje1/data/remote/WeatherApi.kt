package com.example.wakacje1.data

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "pl"
    ): CurrentWeatherResponse

    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "pl"
    ): ForecastResponse
}

// -------------------- MODELE API (Moshi) --------------------

data class CurrentWeatherResponse(
    @Json(name = "name") val name: String?,
    @Json(name = "main") val main: MainDto?,
    @Json(name = "weather") val weather: List<WeatherDto>?
)

data class ForecastResponse(
    @Json(name = "list") val list: List<ForecastItemDto>?
)

data class ForecastItemDto(
    @Json(name = "dt") val dt: Long?,              // sekundy UTC
    @Json(name = "main") val main: MainDto?,
    @Json(name = "weather") val weather: List<WeatherDto>?
)

data class MainDto(
    @Json(name = "temp") val temp: Double?
)

data class WeatherDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "description") val description: String?
)
