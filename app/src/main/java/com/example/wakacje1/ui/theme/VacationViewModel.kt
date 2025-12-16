package com.example.wakacje1.ui.theme

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.data.WeatherRepository
import com.example.wakacje1.data.model.ActivitiesRepository
import com.example.wakacje1.data.model.Destination
import com.example.wakacje1.data.model.DestinationRepository
import com.example.wakacje1.data.model.DayPlan
import com.example.wakacje1.data.model.InternalDayPlan
import com.example.wakacje1.data.model.PlanGenerator
import com.example.wakacje1.data.model.Preferences
import kotlinx.coroutines.launch
import java.util.Calendar

// UI dla pogody bieżącej
data class WeatherUiState(
    val loading: Boolean = false,
    val city: String? = null,
    val temperature: Double? = null,
    val description: String? = null,
    val error: String? = null
)

// UI dla pogody dziennej (pod plan)
data class DayWeatherUi(
    val dateMillis: Long,
    val tempMin: Double?,
    val tempMax: Double?,
    val description: String?,
    val isBadWeather: Boolean
)

class VacationViewModel(application: Application) : AndroidViewModel(application) {

    private val destinationRepository = DestinationRepository(application)
    private val activitiesRepository = ActivitiesRepository(application)

    // preferencje
    var preferences by mutableStateOf<Preferences?>(null)
        private set

    // 3 propozycje wyjazdu
    var destinationSuggestions by mutableStateOf<List<Destination>>(emptyList())
        private set

    var chosenDestination by mutableStateOf<Destination?>(null)
        private set

    // finalny plan do UI
    var plan by mutableStateOf<List<DayPlan>>(emptyList())
        private set

    // struktura planu do edycji slotów
    private var internalPlanDays: MutableList<InternalDayPlan> = mutableListOf()

    // pogoda
    var weather by mutableStateOf(WeatherUiState())
        private set

    // prognoza dzienna pod plan
    var dayWeatherByDate by mutableStateOf<Map<Long, DayWeatherUi>>(emptyMap())
        private set

    // ========================= Preferencje =========================

    fun updatePreferences(prefs: Preferences) {
        preferences = prefs
        destinationSuggestions = emptyList()
        chosenDestination = null
        plan = emptyList()
        internalPlanDays = mutableListOf()
        weather = WeatherUiState()
        dayWeatherByDate = emptyMap()
    }

    // ========================= Propozycje miejsc =========================

    fun prepareDestinationSuggestions() {
        val prefs = preferences ?: run {
            destinationSuggestions = emptyList()
            return
        }

        val all = destinationRepository.getAllDestinations()
        val days = prefs.days.coerceAtLeast(1)
        val budgetPerDay = prefs.budget.toDouble() / days

        val scored = all.map { d ->
            var score = 0.0

            if (prefs.region == d.region) score += 3.0
            if (prefs.climate == d.climate) score += 2.0
            if (d.tags.any { it.equals(prefs.style, ignoreCase = true) }) score += 1.5

            val ratio = budgetPerDay / d.minBudgetPerDay.toDouble()
            when {
                ratio < 0.7 -> score -= 100.0
                ratio < 1.0 -> score -= 3.0
                ratio <= 1.6 -> score += 2.0
                else -> score += 1.0
            }

            d to score
        }.filter { it.second > -50.0 }

        destinationSuggestions = scored
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
    }

    fun chooseDestination(destination: Destination) {
        chosenDestination = destination
        plan = emptyList()
        internalPlanDays = mutableListOf()
        dayWeatherByDate = emptyMap()

        // od razu odpal pogodę
        if (destination.apiQuery.isNotBlank()) {
            loadWeatherForCity(destination.apiQuery)
            loadForecastForTrip()
        }
    }

    // ========================= Pogoda (bieżąca) =========================

    fun loadWeatherForCity(cityQuery: String, force: Boolean = false) {
        viewModelScope.launch {
            weather = WeatherUiState(loading = true)
            try {
                val r = WeatherRepository.getWeatherForCity(cityQuery, forceRefresh = force)
                weather = WeatherUiState(
                    loading = false,
                    city = r.city,
                    temperature = r.temperature,
                    description = r.description,
                    error = null
                )
            } catch (e: Exception) {
                weather = WeatherUiState(
                    loading = false,
                    error = e.message ?: "Nie udało się pobrać pogody."
                )
            }
        }
    }

    // ========================= Prognoza na dni wyjazdu =========================

    fun loadForecastForTrip(force: Boolean = false) {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return
        val startMillis = prefs.startDateMillis ?: return

        val days = prefs.days.coerceAtLeast(1)

        viewModelScope.launch {
            try {
                val forecast = WeatherRepository.getForecastForCity(dest.apiQuery, forceRefresh = force)
                val byDate = forecast.associateBy { it.dateMillis }

                val oneDay = 24L * 60 * 60 * 1000L
                val startNorm = normalizeToLocalMidnight(startMillis)

                val map = mutableMapOf<Long, DayWeatherUi>()
                for (i in 0 until days) {
                    val dayMillis = startNorm + oneDay * i
                    val f = byDate[dayMillis]
                    map[dayMillis] = DayWeatherUi(
                        dateMillis = dayMillis,
                        tempMin = f?.tempMin,
                        tempMax = f?.tempMax,
                        description = f?.description,
                        isBadWeather = f?.isBadWeather ?: false
                    )
                }
                dayWeatherByDate = map
            } catch (_: Exception) {
                dayWeatherByDate = emptyMap()
            }
        }
    }

    private fun isBadWeatherForDayIndex(dayIndex: Int): Boolean {
        val prefs = preferences ?: return false
        val startMillis = prefs.startDateMillis ?: return false

        val oneDay = 24L * 60 * 60 * 1000L
        val startNorm = normalizeToLocalMidnight(startMillis)
        val dayMillis = startNorm + oneDay * dayIndex

        return dayWeatherByDate[dayMillis]?.isBadWeather ?: false
    }

    // ========================= Generowanie planu =========================

    fun generatePlan() {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return
        val allActivities = activitiesRepository.getAllActivities()

        internalPlanDays = PlanGenerator.generateInternalPlan(
            prefs = prefs,
            dest = dest,
            allActivities = allActivities,
            isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
        )

        // budujemy plan do UI
        plan = rebuildUiPlanWithWeather()
    }

    // ========================= Edycja planu =========================

    fun regenerateDay(dayIndex: Int) {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return
        val allActivities = activitiesRepository.getAllActivities()

        PlanGenerator.regenerateWholeDay(
            dayIndex = dayIndex,
            prefs = prefs,
            dest = dest,
            allActivities = allActivities,
            internal = internalPlanDays,
            isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
        )

        plan = rebuildUiPlanWithWeather()
    }

    fun rollNewActivity(dayIndex: Int, slot: com.example.wakacje1.data.model.DaySlot) {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return
        val allActivities = activitiesRepository.getAllActivities()

        PlanGenerator.rollNewSlot(
            dayIndex = dayIndex,
            slot = slot,
            prefs = prefs,
            dest = dest,
            allActivities = allActivities,
            internal = internalPlanDays,
            isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
        )

        plan = rebuildUiPlanWithWeather()
    }

    fun setCustomActivity(dayIndex: Int, slot: com.example.wakacje1.data.model.DaySlot, title: String, description: String) {
        PlanGenerator.setCustomSlot(
            dayIndex = dayIndex,
            slot = slot,
            title = title,
            description = description,
            internal = internalPlanDays
        )
        plan = rebuildUiPlanWithWeather()
    }

    // ========================= Składanie opisu + pogoda =========================

    private fun rebuildUiPlanWithWeather(): List<DayPlan> {
        val dest = chosenDestination ?: return emptyList()
        val base = PlanGenerator.rebuildDayPlans(internalPlanDays, dest.displayName)

        // dopinamy ładną linijkę z pogodą (jeśli prognoza jest)
        val prefs = preferences
        val startMillis = prefs?.startDateMillis
        if (startMillis == null || dayWeatherByDate.isEmpty()) return base

        val oneDay = 24L * 60 * 60 * 1000L
        val startNorm = normalizeToLocalMidnight(startMillis)

        return base.mapIndexed { idx, dayPlan ->
            val dayMillis = startNorm + oneDay * idx
            val w = dayWeatherByDate[dayMillis]
            if (w == null) dayPlan
            else {
                val temps = when {
                    w.tempMin != null && w.tempMax != null ->
                        " (${w.tempMin.toInt()}°C – ${w.tempMax.toInt()}°C)"
                    else -> ""
                }
                val desc = w.description ?: ""
                val note = if (w.isBadWeather) " (pogoda słaba → preferowane atrakcje indoor)" else ""
                dayPlan.copy(details = dayPlan.details + "\n\nPogoda: $desc$temps$note")
            }
        }
    }

    // ========================= Pomocnicze =========================

    private fun normalizeToLocalMidnight(millis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
