package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.presentation.viewmodel.DayWeatherUi
import com.example.wakacje1.util.DateUtils

/**
 * Model wyjściowy zawierający mapę pogody oraz ewentualne komunikaty dla UI (np. "Prognoza tylko na 3 dni").
 */
data class ForecastResult(
    val byDate: Map<Long, DayWeatherUi>,
    val notice: String?
)

/**
 * UseCase odpowiedzialny za dopasowanie surowej prognozy (z repozytorium)
 * do konkretnych dat wycieczki użytkownika.
 */
class LoadForecastForTripUseCase(
    private val weatherRepository: WeatherRepository
) {
    suspend fun execute(prefs: Preferences, dest: Destination, force: Boolean): ForecastResult {
        // Bez zdefiniowanej daty startu nie możemy przypisać pogody do dni
        val startMillis = prefs.startDateMillis ?: return ForecastResult(emptyMap(), null)
        val days = prefs.days.coerceAtLeast(1)

        return try {
            val forecast = weatherRepository.getForecastForCity(dest.apiQuery, forceRefresh = force)

            // Indeksowanie listy prognoz po dacie (timestamp północy) dla O(1) dostępu w pętli
            val byDate = forecast.associateBy { it.dateMillis }

            val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
            val map = mutableMapOf<Long, DayWeatherUi>()
            var covered = 0

            // Iteracja po wszystkich dniach planowanego wyjazdu
            for (i in 0 until days) {
                val dayMillis = DateUtils.dayMillisForIndex(startNorm, i)
                val f = byDate[dayMillis]

                if (f != null) covered++

                // Mapowanie na model UI. Jeśli brak danych (f == null), pola będą puste, ale dzień istnieje w mapie.
                map[dayMillis] = DayWeatherUi(
                    dateMillis = dayMillis,
                    tempMin = f?.tempMin,
                    tempMax = f?.tempMax,
                    description = f?.description,
                    isBadWeather = f?.isBadWeather ?: false
                )
            }

            // Analiza pokrycia: OpenWeatherMap Free daje tylko 5 dni prognozy.
            // Jeśli wyjazd jest dłuższy lub odległy, informujemy użytkownika.
            val notice = when {
                covered == 0 -> "Prognoza dzienna jest niedostępna dla tego terminu."
                covered < days -> "Prognoza dzienna dostępna tylko dla części wyjazdu: $covered/$days dni."
                else -> null
            }

            ForecastResult(map, notice)
        } catch (_: Exception) {
            // Fail-safe: Błąd pogody jest niekrytyczny dla działania planera
            ForecastResult(emptyMap(), "Nie udało się pobrać prognozy dziennej.")
        }
    }
}