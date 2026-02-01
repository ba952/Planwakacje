package com.example.wakacje1.data

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfejs API definiujący endpointy serwisu OpenWeatherMap.
 * Wykorzystywany przez Retrofit do generowania implementacji klienta HTTP.
 */
interface WeatherApi {

    /**
     * Pobiera bieżące dane pogodowe dla wskazanego miasta.
     *
     * param city Nazwa miasta (np. "Paris").
     * param apiKey Klucz dostępu do API (przekazywany z BuildConfig).
     * param units Jednostki (metric = Celsjusz).
     * param lang Język opisu pogody (pl).
     */
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "pl"
    ): CurrentWeatherResponse

    /**
     * Pobiera prognozę pogody na 5 dni (kroki co 3 godziny).
     * Służy do analizy warunków dla planowanego wyjazdu.
     */
    @GET("data/2.5/forecast")
    suspend fun getForecast(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "pl"
    ): ForecastResponse
}

// -------------------- MODELE API (DTO / Moshi) --------------------

/**
 * Model odpowiedzi dla bieżącej pogody.
 * Mapuje strukturę JSON zwracaną przez endpoint /weather.
 */
data class CurrentWeatherResponse(
    @Json(name = "name") val name: String?,
    @Json(name = "main") val main: MainDto?,
    @Json(name = "weather") val weather: List<WeatherDto>?
)

/**
 * Model odpowiedzi dla prognozy.
 * Zawiera listę punktów czasowych (co 3h).
 */
data class ForecastResponse(
    @Json(name = "list") val list: List<ForecastItemDto>?
)

/**
 * Pojedynczy punkt na osi czasu prognozy.
 * property dt Czas w formacie Unix Timestamp (sekundy UTC).
 */
data class ForecastItemDto(
    @Json(name = "dt") val dt: Long?,
    @Json(name = "main") val main: MainDto?,
    @Json(name = "weather") val weather: List<WeatherDto>?
)

/**
 * Główny obiekt danych meteorologicznych (temperatura, ciśnienie, wilgotność).
 * W tym projekcie mapujemy tylko temperaturę.
 */
data class MainDto(
    @Json(name = "temp") val temp: Double?
)

/**
 * Opis warunków pogodowych (np. "bezchmurnie", "deszcz").
 * @property id Kod warunku pogodowego (używany do wykrywania złej pogody w logice biznesowej).
 */
data class WeatherDto(
    @Json(name = "id") val id: Int?,
    @Json(name = "description") val description: String?
)