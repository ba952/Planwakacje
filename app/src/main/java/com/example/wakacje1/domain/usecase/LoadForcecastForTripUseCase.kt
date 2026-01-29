package com.example.wakacje1.domain.usecase

import com.example.wakacje1.data.remote.WeatherRepository
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.presentation.viewmodel.DayWeatherUi // Upewnij się, że ten import jest poprawny
import com.example.wakacje1.util.DateUtils

data class ForecastResult(
    val byDate: Map<Long, DayWeatherUi>,
    val notice: String?
)

class LoadForecastForTripUseCase(
    private val weatherRepository: WeatherRepository // <--- WSTRZYKUJEMY REPOZYTORIUM
) {
    suspend fun execute(prefs: Preferences, dest: Destination, force: Boolean): ForecastResult {
        val startMillis = prefs.startDateMillis ?: return ForecastResult(emptyMap(), null)
        val days = prefs.days.coerceAtLeast(1)

        return try {
            // ZMIANA: Wywołanie na instancji (mała litera 'w')
            val forecast = weatherRepository.getForecastForCity(dest.apiQuery, forceRefresh = force)
            val byDate = forecast.associateBy { it.dateMillis }

            val startNorm = DateUtils.normalizeToLocalMidnight(startMillis)
            val map = mutableMapOf<Long, DayWeatherUi>()
            var covered = 0

            for (i in 0 until days) {
                val dayMillis = DateUtils.dayMillisForIndex(startNorm, i)
                val f = byDate[dayMillis]
                if (f != null) covered++

                map[dayMillis] = DayWeatherUi(
                    dateMillis = dayMillis,
                    tempMin = f?.tempMin,
                    tempMax = f?.tempMax,
                    description = f?.description,
                    isBadWeather = f?.isBadWeather ?: false
                )
            }

            val notice = when {
                covered == 0 -> "Prognoza dzienna jest niedostępna dla tego terminu."
                covered < days -> "Prognoza dzienna dostępna tylko dla części wyjazdu: $covered/$days dni."
                else -> null
            }

            ForecastResult(map, notice)
        } catch (_: Exception) {
            ForecastResult(emptyMap(), "Nie udało się pobrać prognozy dziennej.")
        }
    }
}