package com.example.wakacje1.data

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApi {

    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "pl"
    ): String   // ZWRACAMY GOŁY JSON
}
