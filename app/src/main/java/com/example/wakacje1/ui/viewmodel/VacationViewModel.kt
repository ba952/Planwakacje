package com.example.wakacje1.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.wakacje1.data.local.PdfExporter
import com.example.wakacje1.data.local.PlanStorage
import com.example.wakacje1.data.local.StoredDestination
import com.example.wakacje1.data.local.StoredInternalDayPlan
import com.example.wakacje1.data.local.StoredPlan
import com.example.wakacje1.data.local.StoredPreferences
import com.example.wakacje1.data.model.ActivitiesRepository
import com.example.wakacje1.data.model.DayPlan
import com.example.wakacje1.data.model.DaySlot
import com.example.wakacje1.data.model.Destination
import com.example.wakacje1.data.model.DestinationRepository
import com.example.wakacje1.data.model.InternalDayPlan
import com.example.wakacje1.data.model.PlanGenerator
import com.example.wakacje1.data.model.Preferences
import com.example.wakacje1.data.model.SlotPlan
import com.example.wakacje1.data.remote.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID

data class WeatherUiState(
    val loading: Boolean = false,
    val city: String? = null,
    val temperature: Double? = null,
    val description: String? = null,
    val error: String? = null
)

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

    var preferences by mutableStateOf<Preferences?>(null)
        private set

    var destinationSuggestions by mutableStateOf<List<Destination>>(emptyList())
        private set

    var chosenDestination by mutableStateOf<Destination?>(null)
        private set

    var plan by mutableStateOf<List<DayPlan>>(emptyList())
        private set

    private var internalPlanDays: MutableList<InternalDayPlan> = mutableListOf()

    var canEditPlan by mutableStateOf(true)
        private set

    var weather by mutableStateOf(WeatherUiState())
        private set

    var dayWeatherByDate by mutableStateOf<Map<Long, DayWeatherUi>>(emptyMap())
        private set

    // --- UX: overlay / blokada klikania + komunikaty ---
    var isBlockingUi by mutableStateOf(false)
        private set

    var uiMessage by mutableStateOf<String?>(null)
        private set

    var forecastNotice by mutableStateOf<String?>(null)
        private set

    fun clearUiMessage() {
        uiMessage = null
    }

    fun updatePreferences(prefs: Preferences) {
        preferences = prefs
        destinationSuggestions = emptyList()
        chosenDestination = null
        plan = emptyList()
        internalPlanDays = mutableListOf()
        canEditPlan = true
        weather = WeatherUiState()
        dayWeatherByDate = emptyMap()
        forecastNotice = null
        uiMessage = null
    }

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
        canEditPlan = true
        dayWeatherByDate = emptyMap()
        forecastNotice = null

        if (destination.apiQuery.isNotBlank()) {
            loadWeatherForCity(destination.apiQuery)
            loadForecastForTrip()
        }
    }

    fun loadWeatherForCity(cityQuery: String, force: Boolean = false) {
        viewModelScope.launch {
            weather = WeatherUiState(loading = true)
            try {
                val r = WeatherRepository.getWeatherForCity(cityQuery, forceRefresh = force)
                weather = WeatherUiState(
                    loading = false,
                    city = r.city,
                    temperature = r.temperature,
                    description = r.description
                )
            } catch (e: Exception) {
                weather = WeatherUiState(
                    loading = false,
                    error = e.message ?: "Nie udało się pobrać pogody."
                )
            }
        }
    }

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
                var covered = 0

                for (i in 0 until days) {
                    val dayMillis = startNorm + oneDay * i
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

                dayWeatherByDate = map

                forecastNotice = when {
                    covered == 0 -> "Prognoza dzienna jest niedostępna dla tego terminu (API ma ograniczony horyzont)."
                    covered < days -> "Prognoza dzienna dostępna tylko dla części wyjazdu: $covered/$days dni (ograniczenie API)."
                    else -> null
                }
            } catch (_: Exception) {
                dayWeatherByDate = emptyMap()
                forecastNotice = "Nie udało się pobrać prognozy dziennej."
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

    fun getInternalDayOrNull(dayIndex: Int): InternalDayPlan? =
        internalPlanDays.getOrNull(dayIndex)

    fun getSlotOrNull(dayIndex: Int, slot: DaySlot): SlotPlan? {
        val d = internalPlanDays.getOrNull(dayIndex) ?: return null
        return when (slot) {
            DaySlot.MORNING -> d.morning
            DaySlot.MIDDAY -> d.midday
            DaySlot.EVENING -> d.evening
        }
    }

    fun getDayWeatherForIndex(dayIndex: Int): DayWeatherUi? {
        val prefs = preferences ?: return null
        val startMillis = prefs.startDateMillis ?: return null
        val startNorm = normalizeToLocalMidnight(startMillis)
        val oneDay = 24L * 60 * 60 * 1000L
        val key = startNorm + oneDay * dayIndex
        return dayWeatherByDate[key]
    }

    fun generatePlan() {
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val allActivities =
                    withContext(Dispatchers.IO) { activitiesRepository.getAllActivities() }
                val newInternal = withContext(Dispatchers.Default) {
                    PlanGenerator.generateInternalPlan(
                        prefs = prefs,
                        dest = dest,
                        allActivities = allActivities,
                        isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                    )
                }

                internalPlanDays = newInternal
                canEditPlan = true
                plan = rebuildUiPlan()
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun regenerateDay(dayIndex: Int) {
        if (!canEditPlan) return
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val allActivities =
                    withContext(Dispatchers.IO) { activitiesRepository.getAllActivities() }
                withContext(Dispatchers.Default) {
                    PlanGenerator.regenerateWholeDay(
                        dayIndex = dayIndex,
                        prefs = prefs,
                        dest = dest,
                        allActivities = allActivities,
                        internal = internalPlanDays,
                        isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                    )
                }
                plan = rebuildUiPlan()
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun moveDayUp(index: Int) {
        if (!canEditPlan) return
        if (index <= 0 || index >= internalPlanDays.size) return
        val tmp = internalPlanDays[index - 1]
        internalPlanDays[index - 1] = internalPlanDays[index]
        internalPlanDays[index] = tmp
        plan = rebuildUiPlan()
    }

    fun moveDayDown(index: Int) {
        if (!canEditPlan) return
        if (index < 0 || index >= internalPlanDays.size - 1) return
        val tmp = internalPlanDays[index + 1]
        internalPlanDays[index + 1] = internalPlanDays[index]
        internalPlanDays[index] = tmp
        plan = rebuildUiPlan()
    }

    fun rollNewActivity(dayIndex: Int, slot: DaySlot) {
        if (!canEditPlan) return
        val prefs = preferences ?: return
        val dest = chosenDestination ?: return

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val allActivities =
                    withContext(Dispatchers.IO) { activitiesRepository.getAllActivities() }
                withContext(Dispatchers.Default) {
                    PlanGenerator.rollNewSlot(
                        dayIndex = dayIndex,
                        slot = slot,
                        prefs = prefs,
                        dest = dest,
                        allActivities = allActivities,
                        internal = internalPlanDays,
                        isBadWeatherForDayIndex = { idx -> isBadWeatherForDayIndex(idx) }
                    )
                }
                plan = rebuildUiPlan()
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun setCustomActivity(dayIndex: Int, slot: DaySlot, title: String, description: String) {
        if (!canEditPlan) return
        PlanGenerator.setCustomSlot(
            dayIndex = dayIndex,
            slot = slot,
            title = title,
            description = description,
            internal = internalPlanDays
        )
        plan = rebuildUiPlan()
    }

    fun savePlanLocally() {
        val ctx = getApplication<Application>()
        val prefs = preferences ?: run { uiMessage = "Brak preferencji."; return }
        val dest = chosenDestination ?: run { uiMessage = "Brak wybranego miejsca."; return }
        if (internalPlanDays.isEmpty()) { uiMessage = "Brak planu do zapisu."; return }

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val stored = StoredPlan(
                    id = UUID.randomUUID().toString(),
                    createdAtMillis = System.currentTimeMillis(),
                    destination = StoredDestination.from(dest),
                    preferences = StoredPreferences.from(prefs),
                    // POPRAWKA: mapowanie wg Twojego StoredInternalDayPlan.from(InternalDayPlan)
                    internalDays = internalPlanDays.map { StoredInternalDayPlan.from(it) }
                )

                val msg = withContext(Dispatchers.IO) {
                    try {
                        PlanStorage.savePlan(ctx, stored)
                        "Zapisano lokalnie."
                    } catch (e: Exception) {
                        "Błąd zapisu: ${e.message ?: "?"}"
                    }
                }
                uiMessage = msg
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun loadPlanLocally() {
        val ctx = getApplication<Application>()

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val stored = withContext(Dispatchers.IO) { PlanStorage.loadPlan(ctx) }
                if (stored == null) {
                    uiMessage = "Brak zapisanego planu."
                    return@launch
                }

                preferences = stored.preferences?.toPreferences()
                chosenDestination = stored.destination.toDestination()
                internalPlanDays = stored.internalDays.map { it.toInternalDayPlan() }.toMutableList()

                canEditPlan = true
                plan = rebuildUiPlan()

                weather = WeatherUiState()
                dayWeatherByDate = emptyMap()
                forecastNotice = null

                uiMessage = "Wczytano plan lokalny."
            } finally {
                isBlockingUi = false
            }
        }
    }

    fun applyStoredPlan(stored: StoredPlan) {
        preferences = stored.preferences?.toPreferences()
        chosenDestination = stored.destination.toDestination()
        internalPlanDays = stored.internalDays.map { it.toInternalDayPlan() }.toMutableList()

        canEditPlan = true
        plan = PlanGenerator.rebuildDayPlans(internalPlanDays, chosenDestination?.displayName ?: "")

        weather = WeatherUiState()
        dayWeatherByDate = emptyMap()
        forecastNotice = null
        uiMessage = "Wczytano plan z konta."
    }

    fun exportCurrentPlanToPdf() {
        val ctx = getApplication<Application>()
        if (plan.isEmpty()) { uiMessage = "Brak planu do eksportu."; return }

        viewModelScope.launch {
            isBlockingUi = true
            try {
                val msg = withContext(Dispatchers.IO) {
                    try {
                        val title = "Plan wyjazdu – ${chosenDestination?.displayName ?: "Plan"}"
                        val fileName = "plan_${System.currentTimeMillis()}.pdf"
                        val file = PdfExporter.exportPlanToPdf(ctx, fileName, title, plan)
                        "PDF zapisany: ${file.absolutePath}"
                    } catch (e: Exception) {
                        "Błąd PDF: ${e.message ?: "?"}"
                    }
                }
                uiMessage = msg
            } finally {
                isBlockingUi = false
            }
        }
    }

    private fun rebuildUiPlan(): List<DayPlan> {
        val dest = chosenDestination ?: return emptyList()
        return PlanGenerator.rebuildDayPlans(internalPlanDays, dest.displayName)
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
}
